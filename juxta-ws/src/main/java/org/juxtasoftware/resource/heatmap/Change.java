package org.juxtasoftware.resource.heatmap;

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
    private int diffFrequency;
    private Range range;
    private Range origRange;
    private boolean rangeAdjusted = false;
    
    public Change(long id, Range range, int diffFrequency) {
        this.id = id;
        this.diffFrequency = diffFrequency;
        this.range = new Range(range);
        this.origRange = new Range(range);
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

    public final Range getRange() {
        return this.range;
    }
    public final long getId() {
        return this.id;
    }

    public final int getDifferenceFrequency() {
        return this.diffFrequency;
    }
    public void increaseDiffFrequency() {
        this.diffFrequency++;
    }
    
    @Override
    public int compareTo(Change that) {
        
        // NOTE: There is a bug in interedition Range. It will
        // order range [0,1] before [0,0] when sorting ascending.
        // So.. do NOT use its compareTo. Roll own.
        Range r1 = this.range;
        Range r2 = that.range;
        if ( r1.getStart() < r2.getStart() ) {
            return -1;
        } else if ( r1.getStart() > r2.getStart() ) {
            return 1;
        } else {
            if ( r1.getEnd() < r2.getEnd() ) {
                return -1;
            } else if ( r1.getEnd() > r2.getEnd() ) {
                return 1;
            } 
        }
        return 0;
    }

    @Override
    public String toString() {
        return "ID: " + this.id+" - Range: "+this.range + " Frequency: "+getDifferenceFrequency();
    }
}