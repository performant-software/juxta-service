package org.juxtasoftware.resource.heatmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.juxtasoftware.Constants;
import org.juxtasoftware.model.Witness;

import eu.interedition.text.Name;
import eu.interedition.text.Range;

/**
 * Helper class to track detailed change information 
 * for a single range in the base document.
 * 
 * @author loufoster
 *
 */
public final class Change implements Comparable<Change> {
    private final long id;
    private final int group;
    private Range range;
    private Range origRange;
    private boolean rangeAdjusted = false;
    private List<Detail> details = new ArrayList<Detail>();
    
    public Change(long id, Range range, int group) {
        this.id = id;
        this.group = group;
        this.range = range;
        this.origRange = new Range(range);
    }
    
    public int getGroup() {
        return this.group;
    }
    
    public void adjustRange( long newStart, long newEnd ) {
        this.rangeAdjusted = true;
        this.range = new Range(newStart, newEnd);
    }

    public boolean wasRangeAdjusted( ) {
        return this.rangeAdjusted;
    }

    public Range getOrignialRange() {
        return new Range( this.origRange);
    }

    public boolean hasMatchingWitnesses(Change other) {
        if (this.details.size() != other.details.size() ) {
            return false;
        }
        for ( Detail detail : this.details ) {
            Long thisWitnessId = detail.witness.getId();
            boolean found = false;
            for ( Detail otherDetail : other.details ) {
                Long otherWitnessId = otherDetail.witness.getId();
                if ( thisWitnessId.equals(otherWitnessId ) ) {
                    found = true;
                    break;
                }
            }
            if ( found == false ) {
                return false;
            }
        }
        return true;
    }

    private final Detail findWitnessDetails( Long id) {
        for ( Detail detail : this.details ) {
            if ( detail.getWitness().getId().equals( id ) ) {
                return detail;
            }
        }
        return null;
    }
    
    public boolean hasMatchingGroup(Change prior) {
        if ( getGroup() == 0 || prior.getGroup() == 0 ) {
            return false;
        } else {
            return (getGroup() == prior.getGroup());
        }
    } 

    public void mergeChange( Change mergeFrom ) {

        // new range of this change is the  min/max of the two ranges
        this.range = new Range(//
                Math.min( this.range.getStart(), mergeFrom.getRange().getStart() ),//
                Math.max( this.range.getEnd(), mergeFrom.getRange().getEnd() )
        );

        // for each of the witness details in the merge source, grab the
        // details and add them to the details on this change. note that
        // all witnesses must match up between mergeFrom and this or the
        // merge will not happen. this is enforced in the heatmap render code
        for ( Detail mergeDetail : mergeFrom.getDetails()) {
            Detail detail = findWitnessDetails( mergeDetail.getWitness().getId() );
            for ( Range r : mergeDetail.ranges ) {
                detail = findWitnessDetails( mergeDetail.getWitness().getId() );
                if ( detail == null ) {
                    detail = addWitnessDetail( mergeDetail.getWitness(), Constants.ADD_DEL_NAME, mergeDetail.ranges.get(0) );
                }
                //detail.expandRange( r);
                detail.addRange(r);
            }
        }
    }
    public Detail addWitnessDetail( Witness witness, Name changeName, Range witnessRange  ) {
        Detail detail = findWitnessDetails(witness.getId());
        if ( detail == null ) {
            Detail.Type type = Detail.Type.CHANGE;
            // determine if add or delete based on range; if witness
            // has a non-zero length this text has been ADDED relative to base
            if (changeName.equals(Constants.ADD_DEL_NAME)) {
                if ( witnessRange.length() > 0 ) {
                    type = Detail.Type.ADD;
                } else {
                    type = Detail.Type.DEL;
                }
            }
            detail = new Detail(type, witness, witnessRange );
            this.details.add( detail );
        } else {
            //detail.expandRange( witnessRange );
            detail.addRange(witnessRange);
;        }
        return detail;
    }
    public List<Detail> getDetails() {
        return this.details;
    }
    public final Range getRange() {
        return this.range;
    }
    public final long getId() {
        return this.id;
    }

    public final int getDifferenceFrequency() {
        return this.details.size();
    }
    
    @Override
    public int compareTo(Change that) {
        return this.range.compareTo(that.range);
    }

    @Override
    public String toString() {
        return "ID: " + this.id+" - Range: "+this.range + " Frequency: "+getDifferenceFrequency();
    }

    public void sortDetails() {
        for (Detail d : this.details ) {
            Collections.sort( d.ranges );
        }
    }  

    /**
     * Track the range in the witness doc and the change type. Used
     * to pull text fragments and add content to the margin box
     * @author loufoster
     *
     */
    public static final class Detail {
        enum Type {CHANGE, ADD, DEL};
        private final Type type;
        private final Witness witness;
        private List<Range> ranges = new ArrayList<Range>();
        private String fragment;
        public Detail( final Type t, final Witness w, final Range r ) {
            this.type = t;
            this.witness = w;
            this.ranges.add( r );
        }
        public void addRange( Range r ) {
            this.ranges.add( r );
        }
        public void expandRange( Range newRange ) {
            Collections.sort( this.ranges );
            Range expandedRange = null;
            for ( Iterator<Range> it = this.ranges.iterator(); it.hasNext(); ) {
                final Range range = it.next();
                if ( newRange.equals( range )) {
                    return;
                } else if ( newRange.hasOverlapWith(range )) {
                    it.remove();
                    expandedRange = new Range(//
                            Math.min(range.getStart(), newRange.getStart()),//
                            Math.max(range.getEnd(), newRange.getEnd()));
                    break;
                } 
            }
            this.ranges.add( expandedRange == null ? newRange : expandedRange);
        }
        public Type getType() {
            return this.type;
        }
        public Witness getWitness() {
            return this.witness;
        }
        public void setFragment( String f) {
            this.fragment = f;
        }
        public final String getFragment() {
            return this.fragment;
        }
        public final List<Range> getRanges() {
            return this.ranges;
        }
        public String toString() {
            return "WitnessID: "+this.witness.getId()+" - "+this.fragment;
        }
    } 
}