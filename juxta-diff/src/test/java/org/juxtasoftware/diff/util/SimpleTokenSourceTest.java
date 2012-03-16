package org.juxtasoftware.diff.util;

import com.google.common.collect.Iterables;
import org.junit.Test;
import org.juxtasoftware.diff.AbstractTest;

import java.io.IOException;

import static java.util.Collections.singleton;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class SimpleTokenSourceTest extends AbstractTest {

    @Test
    public void tokenize() throws IOException {
        final SimpleComparand c = comparand("Hello World World World");
        System.out.println(Iterables.toString(tokenSource.tokensOf(c.getText(), singleton(c.getTextRange()))));
    }
}
