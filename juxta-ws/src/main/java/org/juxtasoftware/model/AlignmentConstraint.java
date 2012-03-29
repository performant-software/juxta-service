package org.juxtasoftware.model;

import java.util.HashSet;
import java.util.Set;

import eu.interedition.text.Range;

public class AlignmentConstraint {
    private final Long setId;
    private boolean isBaseless;
    private final Long baseId;
    private Long alignmentId;
    private Range range = null;
    private QNameFilter filter = null;
    private Set<Long> witnessIdFilter = new HashSet<Long>();
    
    public AlignmentConstraint( final ComparisonSet set, final Long baseWitnessId ) {
        this.setId = set.getId();
        this.baseId = baseWitnessId;
        this.isBaseless = false;
        addWitnessIdFilter( this.baseId );
    }
    public AlignmentConstraint( final ComparisonSet set ) {
        this.setId = set.getId();
        this.baseId = null;
        this.isBaseless = true;
    }
    public boolean isBaseless() {
        return this.isBaseless;
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
    public Long getAlignmentId() {
        return alignmentId;
    }
    public void setAlignmentId(Long alignmentId) {
        this.alignmentId = alignmentId;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((alignmentId == null) ? 0 : alignmentId.hashCode());
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
        if (alignmentId == null) {
            if (other.alignmentId != null) {
                return false;
            }
        } else if (!alignmentId.equals(other.alignmentId)) {
            return false;
        }
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