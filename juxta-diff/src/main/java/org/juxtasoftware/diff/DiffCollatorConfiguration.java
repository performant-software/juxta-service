package org.juxtasoftware.diff;

import java.util.Comparator;


public interface DiffCollatorConfiguration {

    TokenSource getTokenSource();

    TranspositionSource getTranspositionSource();

    Comparator<Token> getTokenComparator();

    DifferenceStore getDifferenceStore();

}
