package org.juxtasoftware.resource.sidebyside;

import java.util.Iterator;
import java.util.List;

import org.juxtasoftware.resource.sidebyside.SideBySideView.Change;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class TranspositionInjector implements OverlapInjector<Change> {

    private List<Change> transpositions;
    private Iterator<Change> transpositionItr;
    private Change currTrans = null;
    private boolean tagStarted = false;
    
    @Override
    public void initialize(List<Change> data) {
        this.transpositions = data;
        this.transpositionItr = this.transpositions.iterator();
        if ( this.transpositionItr.hasNext() ) {
            this.currTrans = this.transpositionItr.next();
        }
    }

    @Override
    public List<Change> getData() {
        return this.transpositions;
    }

    @Override
    public boolean hasContent(long pos) {
        if ( this.currTrans == null) {
            return false;
        }
        
        if ( this.currTrans.getRange().getStart() == pos && this.tagStarted == false ||
             this.currTrans.getRange().getEnd() == pos && this.tagStarted == true ) {
            return true;
        }
        return false;
    }
    
    @Override
    public void restartContent( StringBuilder line ) {
        if ( this.tagStarted ) {
            line.append("</span>");
            line.append("<span id=\"move-").append(this.currTrans.getId()).append("-continued\"");
            line.append(" class=\"move\"  title=\"Tranposition\"");
            line.append(" juxta:connect-to=\"").append(this.currTrans.getConnectedId()).append("\"");
            line.append(">");
        }
    }
    @Override
    public boolean injectContentStart(StringBuilder line, long currPositon) {
        if ( this.currTrans != null && this.tagStarted == false ) {
            if ( this.currTrans.getRange().getStart() == currPositon) {
                line.append("<span id=\"move-").append(currTrans.getId()).append("\"");
                line.append(" class=\"move\" title=\"Tranposition\"");
                line.append(" juxta:connect-to=\"").append(currTrans.getConnectedId()).append("\"");
                line.append(">");
                this.tagStarted = true;
                return true;
            } 
        }    
        return false;
    }

    @Override
    public boolean injectContentEnd(StringBuilder line, long currPosition) {
        if ( this.currTrans != null && this.tagStarted == true ) {
            if ( this.currTrans.getRange().getEnd() == currPosition) {
                line.append("</span>");
                this.tagStarted = false;
                this.currTrans = null;
                if ( this.transpositionItr.hasNext() ) {
                    this.currTrans = this.transpositionItr.next();
                }
                return true;
            }
        }
        return false;
    }

}
