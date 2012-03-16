package org.juxtasoftware.diff;

import eu.interedition.text.Annotation;

import java.io.IOException;
import java.util.Set;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public interface TranspositionSource {

    Set<Set<Annotation>> transpositionsIn(Comparison collation) throws IOException;

}
