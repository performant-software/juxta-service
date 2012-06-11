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
                long id = this.currRevision.getId();
                String type = this.currRevision.getType().toString().toLowerCase();
                
                String show = "hide-rev";
                if ( this.currRevision.isIncluded() ) {
                    show = "show-rev";
                }
                String tagType = "ins";
                if (type.equals("delete")) {
                    tagType = "del";
                }

                tag.append("<"+tagType+" id=\"rev-").append(id).append("\" ");
                tag.append(" class=\"rev ").append(type).append(" plain-revs ").append(show).append("\">");
                
                line.append(tag);
                
                // if this is something that was not incuded, add the content
                // (invisibly) to line and end tag. Skip to next tag and say we are not started
                if ( this.currRevision.isIncluded() == false ) {
                    line.append(this.currRevision.getText());
                    line.append("</"+tagType+">");
                    this.tagStarted = false;
                    this.currRevision = null;
                    if ( this.revisionItr.hasNext() ) {
                        this.currRevision = this.revisionItr.next();
                    } 
                } else {
                    this.tagStarted = true;
                }
            }
        }
    }
    
    @Override
    public void  injectContentEnd(StringBuilder line, long currPosition) {
        if ( this.currRevision != null && this.tagStarted == true ) {
            
            String type = this.currRevision.getType().toString().toLowerCase();
            String tagType = "ins";
            if (type.equals("delete")) {
                tagType = "del";
            }
            
            if ( this.currRevision.getRange().getEnd() == currPosition) {
                line.append("</"+tagType+">");

                this.currRevision = null;
                if ( this.revisionItr.hasNext() ) {
                    this.currRevision = this.revisionItr.next();
                } 
                this.tagStarted = false;
            }
        }
    }
}
