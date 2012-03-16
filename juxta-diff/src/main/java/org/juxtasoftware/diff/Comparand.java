package org.juxtasoftware.diff;

import eu.interedition.text.Range;
import eu.interedition.text.Text;

/**
 * An entity to be collated against another of its kind.
 * <p/>
 * Defines a minimal contract whereby a comparand is comprised of some text and the (sub-)range of its characters
 * to be compared.
 *
 * @see DiffCollator#collate(DiffCollatorConfiguration, Comparand, Comparand)
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public interface Comparand {
    
    /**
     * Text to be collated.
     * <p/>
     * The collator accesses the content of the text by using it as a handle against a {@link TokenSource token source}.
     *
     * @return the text of the comparand to be collated
     */
    Text getText();

    /**
     * Specifies the range of characters contained in the text, which the collator will include in the comparison.
     * <p/>
     * This may be a fragment of the whole textual content or its entirety from <code>0</code> to the
     * {@link eu.interedition.text.Text#getLength() text's length}.
     *
     * @return the text range of the comparand to be collated
     */
    Range getTextRange();
}
