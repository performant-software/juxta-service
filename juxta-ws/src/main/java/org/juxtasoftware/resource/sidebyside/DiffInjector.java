package org.juxtasoftware.resource.sidebyside;

import java.util.Iterator;
import java.util.List;

import org.juxtasoftware.resource.sidebyside.SideBySideView.Change;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DiffInjector implements OverlapInjector<Change> {

    private List<Change> changes;
    private Iterator<Change> changeItr;
    private Change currChange = null;
    private boolean tagStarted = false;
    
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
        
        if ( this.currChange.getRange().getStart() == pos && this.tagStarted == false ||
             this.currChange.getRange().getEnd() == pos && this.tagStarted == true ) {
            return true;
        }
        return false;
    }
    
    @Override
    public void restartContent( StringBuilder line ) {
        if ( this.tagStarted ) {
            line.append("</span>");
            line.append("<span id=\"diff-").append(this.currChange.id).append("-continued\"");
            line.append(" class=\"diff\"");
            line.append(" juxta:connect-to=\"").append(this.currChange.connectedToId).append("\"");
            line.append(" title=\"").append(this.currChange.type).append("\">");
        }
    }
    
    @Override
    public boolean injectContentStart(StringBuilder line, long currPositon) {
        if ( this.currChange != null && this.tagStarted == false ) {
            if ( this.currChange.getRange().getStart() == currPositon) {
                line.append("<span id=\"diff-").append(currChange.id).append("\"");
                line.append(" class=\"diff\"");
                line.append(" juxta:connect-to=\"").append(currChange.connectedToId).append("\"");
                line.append(" title=\"").append(currChange.type).append("\">");
                this.tagStarted = true;
                return true;
            } 
        }        
        return false;
    }

    @Override
    public boolean injectContentEnd(StringBuilder line, long currPosition) {
        if ( this.currChange != null && this.tagStarted == true ) {
            if ( this.currChange.getRange().getEnd() == currPosition) {
                line.append("</span>");
                this.tagStarted = false;
                this.currChange = null;
                if ( this.changeItr.hasNext() ) {
                    this.currChange = this.changeItr.next();
                }
                return true;
            }
        }
        return false;
    }

}
