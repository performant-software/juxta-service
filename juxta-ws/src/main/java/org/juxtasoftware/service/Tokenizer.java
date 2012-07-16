package org.juxtasoftware.service;

import static eu.interedition.text.query.Criteria.and;
import static eu.interedition.text.query.Criteria.annotationName;
import static eu.interedition.text.query.Criteria.text;
import static org.juxtasoftware.Constants.TOKEN_NAME;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Set;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.CollatorConfig.HyphenationFilter;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.BackgroundTaskSegment;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import eu.interedition.text.Annotation;
import eu.interedition.text.AnnotationRepository;
import eu.interedition.text.Range;
import eu.interedition.text.Text;
import eu.interedition.text.TextConsumer;
import eu.interedition.text.TextRepository;
import eu.interedition.text.mem.SimpleAnnotation;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class Tokenizer {
    private static final Logger LOG = LoggerFactory.getLogger(Tokenizer.class);

    @Autowired private Integer tokenizationBatchSize;
    @Autowired private AnnotationRepository annotationRepository;
    @Autowired private TextRepository textRepository;
    @Autowired private ComparisonSetDao comparisonSetDao;

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
                
        taskStatus.setNote("Tokenizing " + comparisonSet);
        for (Witness witness : witnesses) {
            taskStatus.setNote("Tokenizing '" + witness.getName() + "'");
            LOG.info("Tokenizing " + witness.getName());
            tokenize(config, witness);
            ts.incrementValue();            
        }
    }
    
    private void tokenize(final CollatorConfig config, final Witness witness) throws IOException {
        // purge any prior tokens for this witness
        final Text text = witness.getText();
        Preconditions.checkNotNull(text);
        this.annotationRepository.delete(and(text(text), annotationName(TOKEN_NAME)));
        
        // do the tokenization
        TokenizingConsumer tc = new TokenizingConsumer(config, witness);
        this.textRepository.read(text, tc);
    }
    
    
    /**
     * Text consumer that splits the text stream into tokens based
     * on configuration settings
     * 
     * @author loufoster
     *
     */
    private class TokenizingConsumer implements TextConsumer {
        private List<Annotation> tokens = Lists.newArrayListWithExpectedSize(tokenizationBatchSize);
        private final Range fragment;
        private final Text text;
        private final boolean filterLinebreak;
        private final boolean filterHyphens;
        private final boolean filterWhitespace;
        private final boolean filterPunctuation;
        
        public TokenizingConsumer(CollatorConfig cfg, Witness w) {
            this.filterPunctuation = cfg.isFilterPunctuation();
            this.filterWhitespace = cfg.isFilterWhitespace();
            this.filterLinebreak = cfg.getHyphenationFilter().equals(HyphenationFilter.FILTER_LINEBREAK);
            this.filterHyphens = cfg.getHyphenationFilter().equals(HyphenationFilter.FILTER_ALL);
            this.fragment = w.getFragment();
            this.text = w.getText();
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
            
            if ( this.filterPunctuation ) {
                if (Character.isLetter(c) || Character.isDigit(c)) {
                    return true;
                }
                return false;
            } 
            return true;
        }
        
        public void read(Reader tokenText, long contentLength) throws IOException {
            
            int offset = 0;
            int start = -1;
            boolean foundLineFeed = false;
            boolean foundHyphen = false; 
            boolean inHyphenatedWord = false;
            boolean whitespaceRun = false;

            do {
                final int read = tokenText.read();
                if (read < 0) {
                    if (start != -1) {
                        // catch case when last line of the source contains
                        // only the 2nd part of a word break or hyphenated word
                        if ( (this.filterLinebreak||this.filterHyphens) && inHyphenatedWord ) {
                            joinLastToken(offset);
                        } else {
                            createToken( start, offset, false);
                        }
                    }
                    break;
                }

                // make sure we are in bounds for the requested text fragment
                if ( this.fragment.equals(Range.NULL) || (offset >= this.fragment.getStart() && offset < this.fragment.getEnd()) ) {
                    
                    // track start of whitespace run if whitespace is not ignored
                    if (this.filterWhitespace == false && Character.isWhitespace(read) && start == -1) {
                        start = offset;
                        whitespaceRun = true;
                    } else if (isTokenChar(read)) {
                        // end whitespace runs on non whitespace chars. Create
                        // a token containing the spaces
                        if (whitespaceRun) {
                            createToken( start, offset, false);
                            start = -1;
                            whitespaceRun = false;
                        }
                        
                        // If this is a non-whitespace char after a dash where
                        // only a space and a linefeed have been found, this token
                        // is considered to be the 2nd part of a word break. Promote the flags.
                        // Alternatively, if mode is filter ALL hyphenation and a hyphen has been found
                        // promote the flags
                        if ( foundHyphen && ( (foundLineFeed && this.filterLinebreak) || this.filterHyphens) ) {
                            foundHyphen = false;
                            foundLineFeed = false;
                            inHyphenatedWord = true;
                        } else {
                            foundHyphen = false;
                            foundLineFeed = false;
                        }
                        
                        // start a new token
                        if (start == -1) {
                            start = offset;
                        }
                        
                    } else {
                        if ( this.filterLinebreak == true || this.filterHyphens ) {
                            if ( inHyphenatedWord == false ) {
                                if ( foundHyphen == false ) {
                                    // Note the presence of a hyphen. Actual behavior will
                                    // be determined as additional data is read
                                    foundHyphen = ( read == '-' );
                                } else {
                                    // Whitespace is ignored after a hyphen is found in both hyphen-filter settings
                                    if ( Character.isWhitespace(read) ) {
                                        // In filter linebreak mode, a hyphen may be followed by whitespace
                                        // and MUST have a linefeed or carriage return
                                        if ( this.filterLinebreak == true ) {
                                            if (read == 13 || read == 10 ) {
                                                foundLineFeed = true;
                                            }
                                        }
                                    } else {
                                        foundHyphen = false;
                                        foundLineFeed = false;
                                    }
                                }
                            } else {
                                // this is the end of a token that has been identified as
                                // the second half of a hyphenated word. Join it with the last
                                // created token and continue on - without executing the rest
                                // of the loop below
                                inHyphenatedWord = false;
                                joinLastToken(offset);
                                start = -1;
                                offset++;
                                continue;
                            }
         
                        }
                        
                        // Either whitespace or punctuation found. Normally, this would end a
                        // token. Behavior is different if whitespace is not being ignored!
                        if ( this.filterWhitespace == false ) {
                            // Simple case: punctuation - end and create a new token.
                            // Harder case: whitespace - if we are in the midst of a 
                            // whitespace run DONT create a token, just keep accumulating the whitespace
                            if (start != -1 && (isPunctuation(read) || whitespaceRun == false )) {
                                createToken( start, offset, foundHyphen );
                                start = -1;
                                
                                // if whitespace ended the token, start a new token with the 
                                // whitespace. This ensures that all whitespace is included in the collation
                                if ( Character.isWhitespace(read) ) {
                                    start = offset;
                                    whitespaceRun = true;
                                }
                            }
                        } else {
                            // simple case, filtering whitespace
                            if ( start != -1 ) {
                                createToken( start, offset, foundHyphen );
                                start = -1;
                            }
                        }
                    }
                }

                offset++;
            } while (true);

            if (!this.tokens.isEmpty()) {
                write();
            }
        }

        private void joinLastToken(int end) {
            Annotation last = this.tokens.remove( this.tokens.size()-1 );
            this.tokens.add(  new SimpleAnnotation(last.getText(), TOKEN_NAME, new Range(last.getRange().getStart(), end), null) ); 
        }

        private void createToken(int start, int end, boolean possbleWordbreak) {
            this.tokens.add(  new SimpleAnnotation(this.text, TOKEN_NAME, new Range(start, end), null) );
            if ( possbleWordbreak == false ) {
                if ((this.tokens.size() % tokenizationBatchSize ) == 0) {
                    write();
                }
            }
        }

        private void write() {
            Tokenizer.this.annotationRepository.create(this.tokens);
            this.tokens.clear();
        }
    }
}
