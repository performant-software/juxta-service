package org.juxtasoftware.service;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Set;

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
    private static final Logger LOG = LoggerFactory.getLogger(Tokenizer.class);

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
        final Set<Witness> witnesses = comparisonSetDao.getWitnesses(comparisonSet);
        final BackgroundTaskSegment ts = taskStatus.add(1, new BackgroundTaskSegment(witnesses.size()));
              
        taskStatus.setNote("Clearing old tokens");
        LOG.info("Cleanup collation data ");
        this.set = comparisonSet;
        this.comparisonSetDao.clearCollationData(comparisonSet);
        
        this.tokenQName = this.qnameRepo.get(Constants.TOKEN_NAME);
        
        taskStatus.setNote("Tokenizing " + comparisonSet);
        for (Witness witness : witnesses) {
            taskStatus.setNote("Tokenizing '" + witness.getName() + "'");
            LOG.info("Tokenizing " + witness.getName());
            long totalTokenLen = tokenize(config, witness);
            comparisonSetDao.setTokenzedLength(comparisonSet, witness, totalTokenLen);
            ts.incrementValue();            
        }
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
        private final boolean filterWhitespace;
        private final boolean filterPunctuation;
        private long tokenizedLength;
        
        public TokenizingConsumer(CollatorConfig cfg, Witness w) {
            this.filterPunctuation = cfg.isFilterPunctuation();
            this.filterWhitespace = cfg.isFilterWhitespace();
            this.hyphenFilter = cfg.getHyphenationFilter();
            this.witness = w;
        }
        
        public long getTokenizedLength() {
            return this.tokenizedLength;
        }
        
        private boolean isPunctuation( int c ) {
            return ( Character.isWhitespace(c) == false &&
                     Character.isLetter(c) == false &&
                     Character.isDigit(c) == false );
        }
        
        private boolean isTokenChar(int c) {
            if ( Character.isWhitespace(c) ) {
                return false;
            }
            
            // this is always good.
            if (Character.isLetter(c) || Character.isDigit(c)) {
                return true;
            }
            
            // non-letter / digit.. it is either punctuation or hyphens.
            // first do special handling for hyphens based in the hyphen filter setting
            // becaue it overrides the default punctuation filtered behavior.
            if ( c == '-' ) {
                return true;
            }
            
            // at this point the char is punctuation. it is considered a token char
            // if punctuation is not filtered
            return !this.filterPunctuation;
        }
        
        public void read(Reader tokenText, long contentLength) throws IOException {
            
            int offset = 0;
            int start = -1;
            boolean isLinebreakWord = false;
            boolean inHyphenatedPart = false;
            boolean foundHyphen = false; 
            boolean whitespaceRun = false;
            final boolean filterLinebreak = 
                (this.hyphenFilter.equals(HyphenationFilter.FILTER_LINEBREAK) || 
                 this.hyphenFilter.equals(HyphenationFilter.FILTER_ALL) );

            do {
                final int read = tokenText.read();
                if (read < 0) {
                    if (start != -1) {
                        createToken( start, offset);
                    }
                    break;
                }

                // make sure we are in bounds for the requested text fragment
                Range frag = this.witness.getFragment();
                if ( frag.equals(Range.NULL) || (offset >= frag.getStart() && offset < frag.getEnd()) ) {
                    
                    // track start of whitespace run if whitespace is not ignored
                    if (this.filterWhitespace == false && Character.isWhitespace(read) ) {
                        whitespaceRun = true;
                        if ( start == -1) {
                            start = offset;
                        }
                    } else if (isTokenChar(read)) {
                        // end whitespace runs on non whitespace chars. Create
                        // a token containing the spaces
                        if (whitespaceRun) {
                            createToken( start, offset);
                            start = -1;
                            whitespaceRun = false;
                        }
                        
                        // start a new token
                        if (start == -1) {
                            start = offset;
                        }
                        
                        // flag if hyphen has been found. Only care about this if we are
                        // filtering out hyphens that signify a line break. This will be determined
                        // when the next whitespace chars are read
                        if ( filterLinebreak ) {
                            if ( isLinebreakWord ) {
                                inHyphenatedPart = true;
                            }
                            else if ( read == '-' && filterLinebreak ) {
                                foundHyphen = true;
                                inHyphenatedPart = false;
                            }
                        }
                    } else {
                        // special linebreak filter behavior when a hyphen has been found!
                        // don't create tokens on whitespace: just look for linefeed. token
                        // will get created aboove
                        if ( filterLinebreak && (inHyphenatedPart || foundHyphen)  ) {
                            if ( inHyphenatedPart ) {
                                createToken( start, offset );
                                start = -1;
                                inHyphenatedPart = false;
                                foundHyphen = false;
                                isLinebreakWord = false;
                            } else {
                                if (  read == 13 || read == 10 ) {
                                    isLinebreakWord = true;
                                }
                            }
                            offset++;
                            continue;
                        }
                        
                        // Either whitespace or punctuation found. Normally, this would end a
                        // token. Behavior is different if whitespace is not being ignored!
                        if ( this.filterWhitespace == false ) {
                            // Simple case: punctuation - end and create a new token.
                            // Harder case: whitespace - if we are in the midst of a 
                            // whitespace run DONT create a token, just keep accumulating the whitespace
                            if (start != -1 && (isPunctuation(read) || whitespaceRun == false )) {
                                createToken( start, offset );
                                start = -1;
                                inHyphenatedPart = false;
                                foundHyphen = false;
                                isLinebreakWord = false;
                                
                                // if whitespace ended the token, start a new token with the 
                                // whitespace. This ensures that all whitespace is included in the collation
                                if ( Character.isWhitespace(read) ) {
                                    start = offset;
                                    whitespaceRun = true;
                                }
                            }
                            offset++;
                            continue;
                        } 
                        
                        // simple case, filtering whitespace, no hyphens. just create!
                        if ( start != -1 ) {
                            createToken( start, offset );
                            start = -1;
                            inHyphenatedPart = false;
                            foundHyphen = false;
                            isLinebreakWord = false;
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
        }

        private void write() {
            LOG.info("Writing "+this.tokens.size()+" token annotations");
            int cnt = Tokenizer.this.annotationDao.create(this.tokens);
            LOG.info("DONE Writing "+cnt+" token annotations");
            if ( cnt != this.tokens.size() ) {
                LOG.error("Not all tokens writtens: "+cnt+" of "+this.tokens.size());
            }
            this.tokens.clear();
        }
    }
}
