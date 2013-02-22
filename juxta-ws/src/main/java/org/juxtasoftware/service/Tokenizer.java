package org.juxtasoftware.service;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

import org.json.simple.JSONObject;
import org.juxtasoftware.Constants;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaAnnotationDao;
import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.CollatorConfig.HyphenationFilter;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaAnnotation;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.BackgroundTaskSegment;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import eu.interedition.text.Name;
import eu.interedition.text.NameRepository;
import eu.interedition.text.Range;
import eu.interedition.text.Text;
import eu.interedition.text.TextConsumer;
import eu.interedition.text.TextRepository;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class Tokenizer {
    private static final Logger LOG = LoggerFactory.getLogger(Constants.WS_LOGGER_NAME);
    private enum HyphenState {NONE, FOUND_HYPHEN, LINEBREAK_HYPHEN, IN_HYPHENATED_PART};
    private enum RunType {NONE, TOKEN, NON_TOKEN, WHITESPACE};

    @Autowired private Integer tokenizationBatchSize;
    @Autowired private JuxtaAnnotationDao annotationDao;
    @Autowired private TextRepository textRepository;
    @Autowired private ComparisonSetDao comparisonSetDao;
    @Autowired private NameRepository qnameRepo;
    private ComparisonSet set;
    private Name tokenQName;

    /**
     * Break up the text of the all witnesses in the comparison set on whitespace boundaries. 
     * If the configuration specifies that punctuation also be ignored, 
     * tokenize on punctuation as well.
     * 
     * @param comparisonSet
     * @param config
     * @param taskStatus
     * 
     * @throws IOException
     */
    public void tokenize(ComparisonSet comparisonSet, CollatorConfig config, BackgroundTaskStatus taskStatus) throws IOException {
        final List<Witness> witnesses = comparisonSetDao.getWitnesses(comparisonSet);
        final BackgroundTaskSegment ts = taskStatus.add(1, new BackgroundTaskSegment(witnesses.size()));
        
        this.set = comparisonSet;
                 
        LOG.info("Token batch size: " + this.tokenizationBatchSize );
        taskStatus.setNote("Clearing old tokens");
        LOG.info("Cleanup collation data ");
        this.comparisonSetDao.clearCollationData(comparisonSet);
        
        this.set.setStatus(ComparisonSet.Status.TOKENIZING );
        this.comparisonSetDao.update(this.set);
        
        this.tokenQName = this.qnameRepo.get(Constants.TOKEN_NAME);
        
        taskStatus.setNote("Tokenizing " + JSONObject.escape(comparisonSet.getName()));
        for (Witness witness : witnesses) {
            taskStatus.setNote("Tokenizing '" + witness.getJsonName() + "'");
            LOG.info("Tokenizing " + witness.getName());
            long totalTokenLen = tokenize(config, witness);
            comparisonSetDao.setTokenzedLength(comparisonSet, witness, totalTokenLen);
            ts.incrementValue();            
        }
        
        this.set.setStatus(ComparisonSet.Status.TOKENIZED );
        this.comparisonSetDao.update(this.set);
    }
    
    private long tokenize(final CollatorConfig config, final Witness witness) throws IOException {
        final Text text = witness.getText();
        TokenizingConsumer tc = new TokenizingConsumer(config, witness);
        this.textRepository.read(text, tc);
        return tc.getTokenizedLength();
    }
    
    /**
     * Text consumer that splits the text stream into tokens based
     * on configuration settings
     * 
     * @author loufoster
     *
     */
    private class TokenizingConsumer implements TextConsumer {
        private List<JuxtaAnnotation> tokens = Lists.newArrayListWithExpectedSize(tokenizationBatchSize);
        private final Witness witness;
        private final HyphenationFilter hyphenFilter;
        private long tokenizedLength;
        
        public TokenizingConsumer(CollatorConfig cfg, Witness w) {
            this.hyphenFilter = cfg.getHyphenationFilter();
            this.witness = w;
        }
        
        public long getTokenizedLength() {
            return this.tokenizedLength;
        }
        
        private boolean isTokenChar(int c) {
            if (Character.isLetter(c) || Character.isDigit(c) || c == '-') {
                return true;
            }
            return false;
        }
        
        public void read(Reader tokenText, long contentLength) throws IOException {
            
            int offset = 0;
            int start = -1;
            HyphenState hyphenState = HyphenState.NONE;
            RunType runType = RunType.NONE;
            final boolean filterLinebreak = 
                (this.hyphenFilter.equals(HyphenationFilter.FILTER_LINEBREAK) || 
                 this.hyphenFilter.equals(HyphenationFilter.FILTER_ALL) );
            
            StringBuilder tokenTxt = new StringBuilder();
            do {
                final int read = tokenText.read();
                if (read < 0) {
                    if (start != -1) {
                        createToken( start, offset);
                        tokenTxt = new StringBuilder();
                    }
                    break;
                }
                
                tokenTxt.append((char)read);
                    
                // Token char (alphanumeric or hyphen)?
                if ( isTokenChar(read)) {
                    // create a token with prior run of non-token characters
                    if ( runType.equals(RunType.NON_TOKEN) ) {
                        createToken(start, offset);
                        start = -1;
                        tokenTxt = new StringBuilder();
                    }
                    
                    runType = RunType.TOKEN;
                    if (start == -1 ) {
                        start = offset;
                    }
                    
                    // Special case handling for linebreak hyphen filtering
                    if ( filterLinebreak ) {
                        // If we have found a hyphen before (and possibly identified this as a linebreak),
                        // the next text encountered is the continuation of the hyphenated word.
                        if ( hyphenState.equals(HyphenState.FOUND_HYPHEN) || hyphenState.equals(HyphenState.LINEBREAK_HYPHEN) ) {
                            hyphenState = HyphenState.IN_HYPHENATED_PART;
                        }
                        else if ( read == '-' ) {
                            if ( tokenTxt.toString().contains("substi")) {
                                System.err.println("fb");
                            }
                            hyphenState = HyphenState.FOUND_HYPHEN;
                        }
                    }
                } else {
                    
                    if ( filterLinebreak ) {
                        if ( hyphenState.equals( HyphenState.IN_HYPHENATED_PART) ) {
                            createToken( start, offset );
                            start = -1;
                            runType = RunType.NONE;
                            hyphenState = HyphenState.NONE;
                            tokenTxt = new StringBuilder();
                        } else if ( hyphenState.equals(HyphenState.FOUND_HYPHEN) || hyphenState.equals(HyphenState.LINEBREAK_HYPHEN)) {
                            // Special case for text that is a candidate for being a linebreak 
                            // hyphenated word. We have a hyphen. Do nothing but wait if more whitespace
                            // is encountered. If the whitespace is a linefeed, flag
                            // this as a line break. In either case, do no more processing.
                            if ( Character.isWhitespace(read) ) {
                                if (  read == 13 || read == 10 ) {
                                    hyphenState = HyphenState.LINEBREAK_HYPHEN;
                                }
                                offset++;
                                continue;
                            }
                        }
                    }

                    // if this non-token char breaks up a prior token
                    // run, create a new token with it
                    if ( runType.equals(RunType.TOKEN) ) {
                        createToken( start, offset);
                        start = -1;
                        runType = RunType.NONE;
                        hyphenState = HyphenState.NONE;
                        tokenTxt = new StringBuilder();
                    } 
                    
                    // Start or continue a run of non-token characters?
                    if ( Character.isWhitespace(read) == false ) {
                        runType = RunType.NON_TOKEN;
                        if (start == -1 ) {
                            start = offset;
                        }
                    } else {
                        // This is whitespace. See if we need to end a non-token run.
                        // other than that, do not track the whitespace
                        if ( runType.equals(RunType.NON_TOKEN) ) {
                            createToken(start, offset);
                            runType = RunType.NONE;
                            hyphenState = HyphenState.NONE;
                            start = -1;
                            tokenTxt = new StringBuilder();
                        }
                    }
                }

                offset++;
            } while (true);

            if (!this.tokens.isEmpty()) {
                write();
            }
        }

        private void createToken(int start, int end) {
            this.tokenizedLength += (end - start);
            this.tokens.add(  new JuxtaAnnotation(set.getId(), this.witness, tokenQName, new Range(start, end)) );
            if ((this.tokens.size() % tokenizationBatchSize ) == 0) {
                write();
            }
        }

        private void write() {
            LOG.info("Writing "+this.tokens.size()+" token annotations");
            int cnt = Tokenizer.this.annotationDao.create(this.tokens);
            if ( cnt != this.tokens.size() ) {
                LOG.error("Not all tokens writtens: "+cnt+" of "+this.tokens.size());
            }
            this.tokens.clear();
        }
    }
}
