package org.juxtasoftware.diff;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.juxtasoftware.diff.impl.SimpleTokenComparator;
import org.juxtasoftware.diff.util.SimpleComparand;
import org.juxtasoftware.diff.util.SimpleTokenSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.interedition.text.Annotation;
import eu.interedition.text.Range;
import eu.interedition.text.mem.SimpleText;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public abstract class AbstractTest implements DiffCollatorConfiguration, DifferenceStore {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractTest.class.getPackage().getName());

    protected DiffCollator collator = new DiffCollator();
    protected List<Difference> alignments = new LinkedList<Difference>();

    protected SimpleTokenSource tokenSource = new SimpleTokenSource();
    protected Comparator<Token> tokenComparator = new SimpleTokenComparator();

    protected List<Difference> collate(Comparand base, Comparand witness) {
        try {
            collator.collate(this, base, witness);
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
        return alignments;
    }

    protected static SimpleComparand comparand(String contents) {
        return new SimpleComparand(contents.toLowerCase());
    }

    protected static void print(List<Difference> alignments) {
        if (!LOG.isDebugEnabled()) {
            return;
        }

        for (Difference a : alignments) {
            final StringBuilder str = new StringBuilder();
            final Range baseRange = a.getBase().getRange();
            str.append("DIFF ID[").append(a.getGroup()).append("], Edit Distance=").append(a.getEditDistance()).append(" : ");
            if (baseRange.length() == 0) {
                str.append("BASE: ");
                str.append( a.getBase().getRange()).append(" { deleted } ");
            } else {
                str.append("BASE: ");
                str.append( a.getBase().getRange()).append(" '");
                String txt = ((SimpleText) a.getBase().getText()).getContent().substring((int) baseRange.getStart(), (int) baseRange.getEnd());
                str.append(txt);
                str.append("' ");
            }

            str.append(" <===> ");

            final Range witnessRange = a.getWitness().getRange();
            if (witnessRange.length() == 0) {
                str.append("WITNESS: ");
                str.append( a.getWitness().getRange()).append(" { deleted } ");
            } else {
                str.append("WITNESS: ");
                str.append( a.getWitness().getRange()).append(" '");
                str.append(((SimpleText) a.getWitness().getText()).getContent().substring((int) witnessRange.getStart(), (int) witnessRange.getEnd()));
                str.append( "' ");
            }

            if (a.getEditDistance() > 0) {
                str.append(" [d = ").append(a.getEditDistance()).append("]");
            }

            LOG.debug(str.toString());
        }
    }

    @Override
    public TokenSource getTokenSource() {
        return tokenSource;
    }

    @Override
    public TranspositionSource getTranspositionSource() {
        return EMPTY_TRANSPOSITION_SOURCE;
    }

    @Override
    public Comparator<Token> getTokenComparator() {
        return tokenComparator;
    }

    @Override
    public DifferenceStore getDifferenceStore() {
        return this;
    }

    @Override
    public void add(Difference diff) throws IOException {
        alignments.add(diff);
    }
    
    @Override
    public void save() throws IOException {
        // TODO Auto-generated method stub 
    }

    protected static final TranspositionSource EMPTY_TRANSPOSITION_SOURCE = new TranspositionSource() {
        @Override
        public Set<Set<Annotation>> transpositionsIn(Comparison collation) throws IOException {
            return Collections.emptySet();
        }
    };
}
