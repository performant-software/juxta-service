package org.juxtasoftware.model;

import java.util.HashSet;
import java.util.Set;

import eu.interedition.text.Range;

/**
 * Constraints for finding alignments. 
 * 
 * This operates in two modes, BASELESS and BASED. The mode controls
 * how the witness filter list will be used.
 * 
 * In BASELESS mode the filter is INCLUSIVE: Only alignments that have 
 * annotations on witnesses matching those in the filter will be returned.
 * 
 * In BASED mode, only alignments that have one of their annotations on
 * the base witness will be returned. Additionally, the filter is EXCLUSIVE:
 * aligments will be EXCLUDED if they have an annotation on a witness
 * from the filter list.
 * 
 * @author loufoster
 *
 */
public class AlignmentConstraint {
    private final Long setId;
    private final Long baseId;
    private Range range = null;
    private QNameFilter filter = null;
    private Set<Long> witnessIdFilter = new HashSet<Long>();
    private int from = -1;
    private int batchSize = -1;
    
    public AlignmentConstraint( final ComparisonSet set, final Long baseWitnessId ) {
        this.setId = set.getId();
        this.baseId = baseWitnessId;
    }
    public AlignmentConstraint( final ComparisonSet set ) {
        this.setId = set.getId();
        this.baseId = null;
    }
    public boolean isResultsRangeSet() {
        return ( this.from != -1 && this.batchSize != -1);
    }
    public void setResultsRange( int from, int batchSize) {
        this.from = from;
        this.batchSize = batchSize;
    }
    public int getFrom() {
        return this.from;
    }
    public int getBatchSize() {
        return this.batchSize;
    }
    public boolean isBaseless() {
        return (this.baseId == null);
    }
    public void addWitnessIdFilter( final Long witnessId ) {
        this.witnessIdFilter.add(witnessId );
    }
    public Set<Long> getWitnessIdFilter() {
        return this.witnessIdFilter;
    }
    public final Range getRange() {
        return range;
    }
    public final void setRange(Range range) {
        this.range = range;
    }
    public final QNameFilter getFilter() {
        return filter;
    }
    public final void setFilter(QNameFilter filter) {
        this.filter = filter;
    }
    public final Long getSetId() {
        return setId;
    }
    public Long getBaseId() {
        return baseId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((baseId == null) ? 0 : baseId.hashCode());
        result = prime * result + ((filter == null) ? 0 : filter.hashCode());
        result = prime * result + ((range == null) ? 0 : range.hashCode());
        result = prime * result + ((setId == null) ? 0 : setId.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AlignmentConstraint other = (AlignmentConstraint) obj;
        if (baseId == null) {
            if (other.baseId != null) {
                return false;
            }
        } else if (!baseId.equals(other.baseId)) {
            return false;
        }
        if (filter == null) {
            if (other.filter != null) {
                return false;
            }
        } else if (!filter.equals(other.filter)) {
            return false;
        }
        if (range == null) {
            if (other.range != null) {
                return false;
            }
        } else if (!range.equals(other.range)) {
            return false;
        }
        if (setId == null) {
            if (other.setId != null) {
                return false;
            }
        } else if (!setId.equals(other.setId)) {
            return false;
        }
        return true;
    }
    
}
