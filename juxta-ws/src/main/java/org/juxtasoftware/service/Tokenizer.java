package org.juxtasoftware.service;

import static eu.interedition.text.query.Criteria.and;
import static eu.interedition.text.query.Criteria.annotationName;
import static eu.interedition.text.query.Criteria.text;
import static org.juxtasoftware.Constants.TOKEN_NAME;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Set;

import eu.interedition.text.TextConsumer;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.BackgroundTaskSegment;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import eu.interedition.text.Annotation;
import eu.interedition.text.AnnotationRepository;
import eu.interedition.text.Range;
import eu.interedition.text.Text;
import eu.interedition.text.TextRepository;
import eu.interedition.text.mem.SimpleAnnotation;

@Service
@Transactional
public class Tokenizer {
    private static final int BATCH_SIZE = 15000;
    private static final Logger LOG = LoggerFactory.getLogger(Tokenizer.class);

    @Autowired private AnnotationRepository annotationRepository;
    @Autowired private TextRepository textRepository;
    @Autowired private ComparisonSetDao comparisonSetDao;
    private boolean ignorePunctuation = true;

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
        
        // look into the cfg and see if we should be ignoring punctuaion. If so,
        // set the flag so we can tokenize on ws and punctuation. this keeps
        // results consistent with the desktop juxta.
        this.ignorePunctuation = config.isFilterPunctuation(); 
        
        taskStatus.setNote("Tokenizing " + comparisonSet);
        
        for (Witness witness : witnesses) {
            taskStatus.setNote("Tokenizing '" + witness.getName() + "'");
            
            tokenize(witness);
            
            ts.incrementValue();            
        }
    }
    
    private void tokenize(final Witness witness) throws IOException {
        // purge any prior tokens for this witness
        final Text text = witness.getText();
        Preconditions.checkNotNull(text);
        this.annotationRepository.delete(and(text(text), annotationName(TOKEN_NAME)));

        final Range fragment = witness.getFragment();
        this.textRepository.read(text, new TextConsumer() {

            private List<Annotation> tokens = Lists.newArrayListWithExpectedSize(BATCH_SIZE);

            public void read(Reader tokenText, long contentLength) throws IOException {
                LOG.trace("Tokenizing " + witness);

                int numTokens = 0;
                int offset = 0;
                int start = -1;
                StringBuffer test = new StringBuffer();

                do {
                    final int read = tokenText.read();
                    if (read < 0) {
                        if (start != -1) {
                            createToken(text, start, offset);
                            numTokens++;
                        }
                        break;
                    }

                    // make sure we are in bounds for the requested text fragment
                    // -- or no fragment was specified at all
                    if ( fragment.equals(Range.NULL) ||
                         (offset >= fragment.getStart() && offset < fragment.getEnd()) ) {
                        
                        if ( isTokenChar(read) ) {
                            if ( start == -1 ) {
                                start = offset;
                            }
                            test.append( (char)read );
                        } else {
                            if ( start != -1 ) {
                                createToken(text, start, offset);
                                numTokens++;
                                test = new StringBuffer();
                                start = -1;
                            }
                        }
                    }

                    offset++;
                } while (true);

                if (!tokens.isEmpty()) {
                    write();
                }
                LOG.trace(witness + " has " + numTokens + " token(s)");
            }

            private void createToken(Text text, int start, int end) {
                tokens.add(new SimpleAnnotation(text, TOKEN_NAME, new Range(start, end), null));
                if ((tokens.size() % BATCH_SIZE) == 0) {
                    write();
                }
            }

            private void write() {
                annotationRepository.create(tokens);
                tokens.clear();
            }
        });
    }
    
    private boolean isTokenChar(int c) {
        if (Character.isWhitespace(c)) {
            return false;
        }
        
        if ( this.ignorePunctuation ) {
            if (Character.isLetter(c) || Character.isDigit(c)) {
                return true;
            }
            return false;
        } 
        return true;
    }
}
