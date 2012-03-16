package org.juxtasoftware.diff;

import eu.interedition.text.Range;
import eu.interedition.text.Text;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public interface TokenSource {

    List<Token> tokensOf(Text text, Set<Range> ranges) throws IOException;
}
