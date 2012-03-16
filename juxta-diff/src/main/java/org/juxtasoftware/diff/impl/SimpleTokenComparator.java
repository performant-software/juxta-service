package org.juxtasoftware.diff.impl;

import org.juxtasoftware.diff.Token;

import java.util.Comparator;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class SimpleTokenComparator implements Comparator<Token> {
    @Override
    public int compare(Token o1, Token o2) {
        return ((SimpleToken) o1).getContent().compareTo(((SimpleToken) o2).getContent());
    }
}
