package org.juxtasoftware.diff.util;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;

import org.juxtasoftware.diff.Token;
import org.juxtasoftware.diff.TokenSource;
import org.juxtasoftware.diff.impl.SimpleToken;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import eu.interedition.text.Name;
import eu.interedition.text.Range;
import eu.interedition.text.Text;
import eu.interedition.text.mem.SimpleAnnotation;
import eu.interedition.text.mem.SimpleName;
import eu.interedition.text.mem.SimpleText;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class SimpleTokenSource implements TokenSource {
    private static final Name TEST_TOKEN_NAME = new SimpleName((URI) null, "testToken");

    @Override
    public List<Token> tokensOf(Text text, Set<Range> ranges) throws IOException {
        Preconditions.checkArgument(text instanceof SimpleText);

        final String textContent = ((SimpleText) text).getContent();
        final List<Token> tokens = Lists.newArrayList();
        int start = -1;
        StringBuffer token = new StringBuffer();
        for ( int offset=0; offset<textContent.length(); offset++) {
            char read = textContent.charAt(offset);
            if ( isTokenChar( read )) {
                if ( start == -1 ) {
                    start = offset;
                }
                token.append( read );
            } else {
                if ( start != -1 ) {
                    Range tokenRange = new Range(start, offset);
                    final SimpleAnnotation a = new SimpleAnnotation(text, TEST_TOKEN_NAME, tokenRange, null);
                    tokens.add(new SimpleToken(a, token.toString()));
                    token = new StringBuffer();
                    start = -1;
                }
            }
        }
        
        if (start > -1 ) {
            Range tokenRange = new Range(start, textContent.length()-1);
            final SimpleAnnotation a = new SimpleAnnotation(text, TEST_TOKEN_NAME, tokenRange, null);
            tokens.add(new SimpleToken(a, token.toString()));
        }
        return tokens;
    }

    
    private boolean isTokenChar(int c) {
        if (Character.isWhitespace(c)) {
            return false;
        }
        
        if (Character.isLetter(c) || Character.isDigit(c)) {
            return true;
        }
        return false;
    }
}
