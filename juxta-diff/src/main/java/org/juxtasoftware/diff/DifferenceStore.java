package org.juxtasoftware.diff;

import java.io.IOException;

/**
 * Contract with some storage for collation results.
 *
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public interface DifferenceStore {
    void add(Difference aignment) throws IOException;
    void save() throws IOException;
}
