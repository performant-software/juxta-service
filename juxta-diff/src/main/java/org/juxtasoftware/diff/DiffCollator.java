package org.juxtasoftware.diff;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;
import eu.interedition.text.Annotation;
import eu.interedition.text.Name;
import eu.interedition.text.Range;
import eu.interedition.text.Text;
import eu.interedition.text.mem.SimpleAnnotation;
import eu.interedition.text.mem.SimpleName;
import eu.interedition.text.util.Annotations;


/**
 * 
 * Perform a diff upon two comperands and tansform results into alignments. Diff is done
 * using google diff-utils and the meyers diff algorithm.
 * 
 */
public class DiffCollator {
    protected static final Name GAP_NAME = new SimpleName(URI.create("http://juxtasoftware.org/ns"), "gap");

    protected static final Logger LOG = LoggerFactory.getLogger(DiffCollator.class);   
    protected boolean transpositionCollation; 

    public void collate(DiffCollatorConfiguration config, Comparand base, Comparand witness) throws IOException {

        // This amounts to a config for the diff/collation
        Comparison comparison = new Comparison(base, witness);
        
        // First find an filter out any transpositions and collate
        // the filtered result
        final Set<Set<Annotation>> transpositions = config.getTranspositionSource().transpositionsIn(comparison);
        for (Comparison filtered : comparison.filter(transpositions)) {
            LOG.info("Collating " + filtered);
            this.transpositionCollation = false;
            collate(config, filtered);
        }

        // Now take each of the transpositions and perform a
        // mini-collation on it
        for (Set<Annotation> transposition : transpositions) {
            LOG.info("Collating transposition " + Iterables.toString(transposition));
            this.transpositionCollation = true;
            collate(config, new Comparison(base, witness, transposition));
        }
        Runtime.getRuntime().gc();
        Runtime.getRuntime().runFinalization();
    }

    protected void collate(DiffCollatorConfiguration config, Comparison collation) throws IOException {
       
        // Pull ALL tokens for base and witness into memory. 
        // The token inclides annotation info (range, qname, etc) plus
        // notmalized text based on settings (no case, punctiuation stripped, etc)
        final TokenSource tokenSource = config.getTokenSource();
        final Text base = collation.getBase().getText();
        final Text witness = collation.getWitness().getText();
        final List<Token> baseTokens = tokenSource.tokensOf(base, collation.getBaseRanges());
        final List<Token> witnessTokens = tokenSource.tokensOf(witness, collation.getWitnessRanges());
        if (baseTokens.isEmpty() && witnessTokens.isEmpty()) {
            return;
        }
        
        // Do the diff!
        Patch diffResult = DiffUtils.diff(baseTokens, witnessTokens);

        // Convert the dif results into differences
        DifferenceStore differenceStore = config.getDifferenceStore();
        int baseTokenIndex = 0; 
        int witnessTokenIndex = 0;
        int diffSequece = 0;
        
        // Each diff may contain several contiguous tokens that make
        // up the total diff. Assign each diff a sequence number so
        // the token-based diffs can be grouped back into contiguous 
        // runs of text later
        for ( Delta diff : diffResult.getDeltas() ) {
            diffSequece++;
            
            // grab references to diff token indexes for base (original) and witness (revised)
            int baseDiffTokenStartIndex = diff.getOriginal().getPosition();
            int baseDiffTokenEndIndex = baseDiffTokenStartIndex + diff.getOriginal().getLines().size();
            int witnessDiffTokenStartIndex = diff.getRevised().getPosition();
            int witnessDiffTokenEndIndex = witnessDiffTokenStartIndex + diff.getRevised().getLines().size();
            
            do {
                // curr indexes are before change - these are aligned, just skip over them
                if ( baseTokenIndex < baseDiffTokenStartIndex && witnessTokenIndex < witnessDiffTokenStartIndex) {
                    baseTokenIndex++;
                    witnessTokenIndex++;
                    continue;
                }
                
                // curr indexes are both within change bounds... mark as change - no gaps
                if ( baseTokenIndex < baseDiffTokenEndIndex && witnessTokenIndex < witnessDiffTokenEndIndex) {
                    Token baseToken = baseTokens.get(baseTokenIndex++);
                    Token witnessToken = witnessTokens.get(witnessTokenIndex++);
                    differenceStore.add( createDifference(diffSequece, Difference.Type.CHANGE,
                        baseToken.getAnnotation(), baseToken, 
                        witnessToken.getAnnotation(), witnessToken));
                    continue;
                }
                
                // Base still within change range, witnesss not. Introduce a gap in the witness
                if ( baseTokenIndex < baseDiffTokenEndIndex && witnessTokenIndex >= witnessDiffTokenEndIndex) {
                    Token baseToken = baseTokens.get(baseTokenIndex++);
                    Annotation witnessGap = createGap( witness, witnessTokenIndex, witnessTokens );
                    differenceStore.add( createDifference(diffSequece, Difference.Type.ADD_DEL,
                        baseToken.getAnnotation(), baseToken, witnessGap, null));
                    continue;
                }
                
                // WITNESS still within change range, base not. Introduce a gap in the base
                if ( baseTokenIndex >= baseDiffTokenEndIndex && witnessTokenIndex < witnessDiffTokenEndIndex) {
                    Token witnessToken = witnessTokens.get(witnessTokenIndex++);
                    Annotation baseGap = createGap( base, baseTokenIndex, baseTokens );
                    differenceStore.add( createDifference(diffSequece, Difference.Type.ADD_DEL, 
                        baseGap, null, witnessToken.getAnnotation(), witnessToken));
                    continue;
                }
                
                
            } while ( baseTokenIndex <  baseDiffTokenEndIndex || witnessTokenIndex <  witnessDiffTokenEndIndex );
        }
    }

    private Annotation createGap(Text comparandText, int currIndex, List<Token> tokens) throws IOException {
        if ( currIndex == 0 ) {
            if ( this.transpositionCollation == false ) {
                return  new GapAnnotation(comparandText, 0);
            }
            return  gap( tokens.get(0).getAnnotation() );
        } else {
            return gap( tokens.get(currIndex-1).getAnnotation() );
        }
    }

    private Difference createDifference(int diffSequece, Difference.Type type, Annotation base, Token baseToken, Annotation witness, Token witnessToken) {
        int editDistance = 0;
        if (type.equals(Difference.Type.CHANGE) ) {
            editDistance = baseToken.editDistanceTo(witnessToken);
        } else if (base.getRange().length() == 0 || witness.getRange().length() == 0) {
            editDistance = -1;
        }
        return new Difference(diffSequece, type, base, witness, editDistance);
    }


    protected Annotation gap(Annotation prev) throws IOException {
        if (prev instanceof GapAnnotation) {
            return prev;
        } else {
            return new GapAnnotation(prev.getText(), prev.getRange().getEnd());
        }
    }

    public static class GapAnnotation extends SimpleAnnotation {

        private final long offset;

        public GapAnnotation(Text text, long offset) {
            super(text, GAP_NAME, new Range(offset, offset), null);
            this.offset = offset;
        }

        public long getOffset() {
            return offset;
        }

        @Override
        public Text getText() {
            return text;
        }

        @Override
        public Name getName() {
            return GAP_NAME;
        }

        @Override
        public Range getRange() {
            return new Range(offset, offset);
        }
        
        @Override
        public String toString() {
            return "GAP "+text.toString()+" "+getRange().toString();
        }

        @Override
        public int compareTo(Annotation o) {
            return Annotations.compare(this, o).compare(this, o, Ordering.arbitrary()).result();
        }

        public static final Function<Annotation,Long> TO_OFFSET = new Function<Annotation, Long>() {
            @Override
            public Long apply(Annotation input) {
                Preconditions.checkArgument(input.getRange().length() == 0);
                return input.getRange().getEnd();
            }
        };
    }
}
