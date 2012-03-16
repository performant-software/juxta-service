package org.juxtasoftware.diff.util;

import com.google.common.base.Objects;
import eu.interedition.text.Range;
import eu.interedition.text.Text;
import eu.interedition.text.mem.SimpleText;
import org.juxtasoftware.diff.Comparand;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class SimpleComparand implements Comparand {
    private final String content;
    private final SimpleText text;

    public SimpleComparand(String content) {
        this.content = content;
        this.text = new SimpleText(Text.Type.TXT, content);
    }

    @Override
    public Text getText() {
        return text;
    }

    @Override
    public Range getTextRange() {
        return new Range(0, text.getLength());
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).addValue(content).toString();
    }
}
