package org.juxtasoftware.resource.heatmap;

import java.util.Iterator;
import java.util.List;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import eu.interedition.text.Range;

/**
 * Inject heat intensity markup into the witness stream
 * 
 * @author loufoster
 *
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ChangeInjector implements StreamInjector<Change> {
       
    private List<Change> changes;
    private Iterator<Change> changeItr;
    private Change currChange = null;
    private boolean tagStarted = false;
    private int witnessCount;
    
    public void setWitnessCount( int size ) {
        this.witnessCount = size;
    }
    
    @Override
    public void initialize(List<Change> data) {
        this.changes = data;
        this.changeItr = this.changes.iterator();
        if ( this.changeItr.hasNext() ) {
            this.currChange = this.changeItr.next();
        }
    }

    @Override
    public List<Change> getData() {
        return this.changes;
    }
    
    @Override
    public boolean hasContent(long pos) {
        if ( this.currChange == null) {
            return false;
        }
        
        if ( this.currChange.getRange().getStart() <= pos && this.tagStarted == false ||
             this.currChange.getRange().getEnd() <= pos && this.tagStarted == true ) {
            return true;
        }
        return false;
    }
    
    @Override
    public void injectContentStart(StringBuilder line, final long currPositon) {
        if ( this.currChange != null && this.tagStarted == false ) {
            if ( this.currChange.getRange().getStart() <= currPositon) {
                line.append( generateChangeHtml( this.currChange, this.witnessCount ) );
                this.tagStarted = true;
            } 
        }
    }
    
    @Override
    public void injectContentEnd(StringBuilder line, final long currPosition) {
        if ( this.currChange != null && this.tagStarted == true ) {
            if ( this.currChange.getRange().getEnd() <= currPosition) {
                line.append("</span>");
                this.tagStarted = false;
                this.currChange = null;
                if ( this.changeItr.hasNext() ) {
                    this.currChange = this.changeItr.next();
                }
            }
        }
    }
    
    /**
     * Create a heatmap span for a change. The color intensity is based
     * upon the frequency of the change.
     * @param change
     * @param numWitnesses
     * @return
     */
    private String generateChangeHtml(Change change, int numWitnesses ) {
        final int diffHighlightLen = 9;
        float value = ((float) change.getDifferenceFrequency() / (float)numWitnesses) * diffHighlightLen;
        int idx = (int) Math.round(value);
        idx = Math.max(0, idx);
        idx = Math.min(idx, diffHighlightLen);
        
        StringBuffer sb = new StringBuffer();
        sb.append("<span juxta:range=\"");
        Range range = change.getRange();
        if ( change.wasRangeAdjusted() ) {
           range = change.getOrignialRange();
        }
        sb.append(range.getStart()).append(",").append(range.getEnd()).append("\"");
        sb.append(" juxta:diff-freq=\"").append(change.getDifferenceFrequency()).append("\"");
        sb.append(" class=\"heatmap heat").append(idx).append("\" id=\"").append(change.getId()).append("\">");
       
        return sb.toString();
    }
}
