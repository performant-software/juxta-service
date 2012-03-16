package org.juxtasoftware.diff.impl;

import org.juxtasoftware.diff.Token;

import eu.interedition.text.Annotation;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class SimpleToken implements Token {
    private final Annotation annotation;
    private final String content;

    public SimpleToken(Annotation annotation, String content) {
        this.annotation = annotation;
        this.content = content;
    }

    @Override
    public String toString() {
        return content + " - " + annotation.getRange();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((content == null) ? 0 : content.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        SimpleToken other = (SimpleToken) obj;
        if (content == null) {
            if (other.content != null) {
                return false;
            }
        } else if (!content.equals(other.content)) {
            return false;
        }
        return true;
    }

    @Override
    public Annotation getAnnotation() {
        return annotation;
    }

    @Override
    public int editDistanceTo(Token other) {
        return EditDistance.compute(content, ((SimpleToken) other).content);
    }

    public String getContent() {
        return content;
    }
}
