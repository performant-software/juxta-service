package org.juxtasoftware.diff;

import eu.interedition.text.Annotation;

public interface Token {
    Annotation getAnnotation();

    int editDistanceTo(Token other);
}