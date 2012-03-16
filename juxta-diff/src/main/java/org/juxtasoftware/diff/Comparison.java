package org.juxtasoftware.diff;

import static com.google.common.collect.Sets.newTreeSet;
import static eu.interedition.text.util.Ranges.exclude;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import eu.interedition.text.Annotation;
import eu.interedition.text.Range;
import eu.interedition.text.Text;
import eu.interedition.text.util.Ranges;

public class Comparison {
    private final Comparand base;
    private final Comparand witness;
    private final SortedSet<Range> baseRanges;
    private final SortedSet<Range> witnessRanges;

    public Comparison(Comparand base, Comparand witness, SortedSet<Range> baseRanges, SortedSet<Range> witnessRanges) {
        this.base = base;
        this.witness = witness;
        this.baseRanges = Ranges.compressAdjacent(baseRanges);
        this.witnessRanges = Ranges.compressAdjacent(witnessRanges);
    }

    public Comparison(Comparand base, Comparand witness) {
        this(base, witness,//
                newTreeSet(Collections.singleton(new Range(base.getTextRange()))),//
                newTreeSet(Collections.singleton(new Range(witness.getTextRange()))));
    }

    public Comparison(Comparand base, Comparand witness, Set<Annotation> transposition) {
        this.base = base;
        this.witness = witness;
        this.baseRanges = newTreeSet();
        this.witnessRanges = newTreeSet();

        final Text baseText = base.getText();
        final Text witnessText = witness.getText();
        for (Annotation a : transposition) {
            if (baseText.equals(a.getText())) {
                getBaseRanges().add(a.getRange());
            } else if (witnessText.equals(a.getText())) {
                getWitnessRanges().add(a.getRange());
            }
        }
    }

    public int getBaseLength() {
        return Ranges.length(getBaseRanges());
    }

    public int getWitnessLength() {
        return Ranges.length(getWitnessRanges());
    }

    public int getMaxLength() {
        return Math.max(getBaseLength(), getWitnessLength());
    }

    @Override
    public String toString() {
        final String baseDesc = getBase() + "{" + (Joiner.on(", ").join(getBaseRanges())) + "}";
        final String witnessDesc = getWitness() + "{" + (Joiner.on(", ").join(getWitnessRanges())) + "}";
        return "[" + baseDesc + " vs. " + witnessDesc + "]";
    }

    public List<Comparison> filter(Set<Set<Annotation>> transpositions) {
        final Text baseText = getBase().getText();
        final Text witnessText = getWitness().getText();

        final List<Range> transposedInBase = Lists.newArrayListWithExpectedSize(transpositions.size());
        final List<Range> transposedInWitness = Lists.newArrayListWithExpectedSize(transpositions.size());
        for (Set<Annotation> t : transpositions) {
            for (Annotation a : t) {
                if (baseText.equals(a.getText())) {
                    transposedInBase.add(a.getRange());
                } else if (witnessText.equals(a.getText())) {
                    transposedInWitness.add(a.getRange());
                }
            }
        }
        return Lists.newArrayList(new Comparison(getBase(), getWitness(),//
                newTreeSet(transposedInBase.isEmpty() ? this.getBaseRanges() : exclude(this.getBaseRanges(), transposedInBase)),//
                newTreeSet(transposedInWitness.isEmpty() ? this.witnessRanges : exclude(this.getWitnessRanges(), transposedInWitness))));
    }

    public Comparand getBase() {
        return base;
    }

    public Comparand getWitness() {
        return witness;
    }

    public SortedSet<Range> getBaseRanges() {
        return baseRanges;
    }

    public SortedSet<Range> getWitnessRanges() {
        return witnessRanges;
    }
}
