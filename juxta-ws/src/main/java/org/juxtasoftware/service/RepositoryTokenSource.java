package org.juxtasoftware.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.juxtasoftware.dao.JuxtaAnnotationDao;
import org.juxtasoftware.diff.Token;
import org.juxtasoftware.diff.TokenSource;
import org.juxtasoftware.diff.impl.SimpleToken;
import org.juxtasoftware.model.AnnotationConstraint;
import org.juxtasoftware.model.CollatorConfig.HyphenationFilter;
import org.juxtasoftware.model.JuxtaAnnotation;
import org.juxtasoftware.model.QNameFilter;

import eu.interedition.text.Range;
import eu.interedition.text.Text;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class RepositoryTokenSource implements TokenSource {
    private final TokenizerConfiguration config;
    private final JuxtaAnnotationDao annotationDao;
    private final QNameFilter tokenFilter;

    public RepositoryTokenSource(TokenizerConfiguration config, JuxtaAnnotationDao annoDao, QNameFilter tokenFilter) {
        this.config = config;
        this.annotationDao = annoDao;
        this.tokenFilter = tokenFilter;
    }

    @Override
    public List<Token> tokensOf(Text text, Set<Range> ranges) throws IOException {
        // TODO Test if these ranges are ever non-contiguous
        Long minStart = null;
        Long maxEnd = null;
        for (Range r : ranges ) {
            if ( minStart == null || r.getStart() < minStart ) {
                minStart = r.getStart();
            }
            if ( maxEnd == null || r.getEnd() > maxEnd ) {
                maxEnd = r.getEnd();
            }
        }
        Range mergedRange = new Range(minStart,maxEnd);
        
        List<Token> tokens = new ArrayList<Token>();
        AnnotationConstraint constraint = new AnnotationConstraint(text);
        constraint.setFilter( this.tokenFilter );
        constraint.setRange(mergedRange);
        constraint.setIncludeText(true);
        for ( JuxtaAnnotation anno : this.annotationDao.list(constraint) ) {
            String tokenText = anno.getContent();
            
            if ( this.config.getHyphenationFilter().equals(HyphenationFilter.FILTER_ALL)  ) {
                if ( tokenText.contains("-")) {
                    String[] bits = tokenText.split("-");
                    if (bits.length == 2) {
                        tokenText = bits[0].trim() + bits[1].trim();
                    }
                }
            } else if ( this.config.getHyphenationFilter().equals(HyphenationFilter.FILTER_LINEBREAK)  ) {
                if ( tokenText.contains("-") && tokenText.contains("\n")) {
                    String[] bits = tokenText.split("-");
                    if (bits.length == 2) {
                        tokenText = bits[0].trim() + bits[1].trim();
                    }
                }   
            }
            
            tokens.add(new SimpleToken(anno, tokenText));
        }
        return tokens;
    }
}
