package org.juxtasoftware.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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
 * Source of token annotations to be used by the diff collator
 */
public class RepositoryTokenSource implements TokenSource {
    private final TokenizerConfiguration config;
    private final JuxtaAnnotationDao annotationDao;
    private final QNameFilter tokenFilter;
    private final Long setId;
    
    // anything thats not alnum or hyphen is consdered punctuation here
    private static final Pattern PUNCTUATION = Pattern.compile("[^a-zA-Z0-9\\-]");

    public RepositoryTokenSource( TokenizerConfiguration config, Long setId, JuxtaAnnotationDao annoDao, QNameFilter tokenFilter) {
        this.config = config;
        this.annotationDao = annoDao;
        this.tokenFilter = tokenFilter;
        this.setId = setId;
    }

    @Override
    public List<Token> tokensOf(Text text, Set<Range> ranges) throws IOException {        
        List<Token> tokens = new ArrayList<Token>();
        AnnotationConstraint constraint = new AnnotationConstraint(this.setId, text);
        constraint.setFilter( this.tokenFilter );
        for (Range r : ranges ) {
            constraint.addRange(r);
        }
        constraint.setIncludeText(true);
        List<JuxtaAnnotation> annos = this.annotationDao.list(constraint) ;
        for ( JuxtaAnnotation anno : annos ) {
            String tokenText = anno.getContent();
            
            if ( this.config.getHyphenationFilter().equals(HyphenationFilter.FILTER_ALL)  ) {
                if ( tokenText.contains("-")) {
                    String[] bits = tokenText.split("-");
                    if (bits.length == 2) {
                        tokenText = bits[0].trim() + bits[1].trim();
                    }
                }
            } else if ( this.config.getHyphenationFilter().equals(HyphenationFilter.FILTER_LINEBREAK)  ) {
                if ( tokenText.contains("-") && (tokenText.indexOf(10) > -1) || tokenText.indexOf(13) > -1) {
                    String[] bits = tokenText.split("-");
                    if (bits.length == 2) {
                        tokenText = bits[0].trim() + bits[1].trim();
                    }
                }   
            }
            
            if ( this.config.isFilterCase()) {
                tokenText = tokenText.toLowerCase();
            }
            if ( this.config.getHyphenationFilter().equals(HyphenationFilter.FILTER_ALL)) {
                tokenText = tokenText.replaceAll("-", "");
            }
            if ( this.config.isFilterPunctuation() ) {
                tokenText = PUNCTUATION.matcher(tokenText).replaceAll("");
            }
            if ( this.config.isFilterWhitespace() ) {
                tokenText = tokenText.trim().replaceAll("\\s+", " ");
            }
            
            if ( tokenText.length() > 0 ) {
                tokens.add(new SimpleToken(anno, tokenText));
            }
        }
        return tokens;
    }
}
