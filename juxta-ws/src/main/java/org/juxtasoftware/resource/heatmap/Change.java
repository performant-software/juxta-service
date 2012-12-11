package org.juxtasoftware.resource.heatmap;

import java.util.HashSet;
import java.util.Set;

import org.juxtasoftware.model.Witness;

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
    private Set<Long> witnesses = new HashSet<Long>();
    
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
        if (this.witnesses.size() != other.witnesses.size() ) {
            return false;
        }
        for ( Long thisWitnessId : this.witnesses ) {
            boolean found = false;
            for ( Long otherWitnessId : other.witnesses ) {
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
        this.witnesses.addAll(mergeFrom.witnesses);
    }
    
    public void addWitness( Witness witness   ) {
        this.witnesses.add(witness.getId());
    }

    public final Range getRange() {
        return this.range;
    }
    public final long getId() {
        return this.id;
    }

    public final int getDifferenceFrequency() {
        return this.witnesses.size();
    }
    
    @Override
    public int compareTo(Change that) {
        return this.range.compareTo(that.range);
    }

    @Override
    public String toString() {
        return "ID: " + this.id+" - Range: "+this.range + " Frequency: "+getDifferenceFrequency();
    }
}