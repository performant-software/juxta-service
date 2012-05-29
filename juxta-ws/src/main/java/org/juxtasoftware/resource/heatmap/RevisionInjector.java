package org.juxtasoftware.resource.heatmap;

import java.util.Iterator;
import java.util.List;

import org.juxtasoftware.model.RevisionInfo;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Inject revision tagging for a witness
 * 
 * @author loufoster
 *
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class RevisionInjector implements StreamInjector<RevisionInfo> {

    private List<RevisionInfo> revisions;
    private Iterator<RevisionInfo> revisionItr;
    private RevisionInfo currRevision;
    private boolean tagStarted = false;
    
    @Override
    public void initialize(List<RevisionInfo> data) {
        this.revisions = data;
        this.revisionItr = this.revisions.iterator();
        if ( this.revisionItr.hasNext() ) {
            this.currRevision = this.revisionItr.next();
        }
    }

    @Override
    public List<RevisionInfo> getData() {
        return this.revisions;
    }
    @Override
    public boolean hasContent(long pos) {
        if ( this.currRevision == null) {
            return false;
        }
        
        if ( this.currRevision.getRange().getStart() == pos && this.tagStarted == false ||
             this.currRevision.getRange().getEnd() == pos && this.tagStarted == true ) {
            return true;
        }
        return false;
    }

    @Override
    public void injectContentStart(StringBuilder line, final long currPositon) {
        if ( this.currRevision != null && this.tagStarted == false ) {
            if ( this.currRevision.getRange().getStart() == currPositon) {
                StringBuffer tag = new StringBuffer();
                long id = this.currRevision.getAnnotationId();
                String type = "add";
                if ( this.currRevision.isDelete() ) {
                    type = "delete";
                }
                String accept = "accept";
                tag.append("<span id=\"rev-").append(id).append("\" ");
                tag.append(" class=\"rev ").append(type).append(" plain-revs ").append(accept).append("\">");
                
                line.append(tag);
                this.tagStarted = true;
            }
        }
    }
    
    @Override
    public void  injectContentEnd(StringBuilder line, long currPosition) {
        if ( this.currRevision != null && this.tagStarted == true ) {
           
            if ( this.currRevision.getRange().getEnd() == currPosition) {
                line.append("</span>");

                this.currRevision = null;
                if ( this.revisionItr.hasNext() ) {
                    this.currRevision = this.revisionItr.next();
                } 
                this.tagStarted = false;
            }
        }
    }
}
