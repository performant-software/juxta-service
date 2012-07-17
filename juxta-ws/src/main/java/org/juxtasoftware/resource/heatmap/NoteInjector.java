package org.juxtasoftware.resource.heatmap;

import java.util.Iterator;
import java.util.List;

import org.juxtasoftware.model.Note;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Inject note information into the heatmap text stream
 * @author loufoster
 *
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class NoteInjector implements StreamInjector<Note> {

    private List<Note> notes;
    private Iterator<Note> noteItr;
    private Note currNote;
    private boolean tagStarted = false;
    
    @Override
    public void initialize(List<Note> data) {
        this.notes = data;
        this.noteItr = this.notes.iterator();
        if ( this.noteItr.hasNext() ) {
            this.currNote = this.noteItr.next();
        } 
    }
    
    @Override
    public List<Note> getData() {
        return this.notes;
    }
    
    @Override
    public boolean hasContent(long pos) {
        if ( this.currNote == null) {
            return false;
        }
        
        if ( this.currNote.getAnchorRange().getStart() == pos && this.tagStarted == false ||
             this.currNote.getAnchorRange().getEnd() == pos && this.tagStarted == true ) {
            return true;
        }
        return false;
    }
    
    public boolean addTrailingNotes(StringBuilder line ) {
        boolean added = false;
        while ( this.currNote != null ) {
            added = true;
            injectContentStart( line, this.currNote.getAnchorRange().getStart() );
            injectContentEnd( line, this.currNote.getAnchorRange().getEnd() );
        }
        return added;
    }

    @Override
    public void injectContentStart(StringBuilder line, final long currPositon) {
        if ( this.currNote != null && this.tagStarted == false ) {
      
            if (  this.currNote.getAnchorRange().getStart() == currPositon ) {
                line.append("<span class=\"note-anchor\" id=\"note-anchor-");
                line.append(this.currNote.getId()).append("\">");
                this.tagStarted = true;
            }
        }
    }
    
    @Override
    public void injectContentEnd(StringBuilder line, final long currPosition) {
        
        if (this.currNote != null && this.tagStarted == true) {
            if (this.currNote.getAnchorRange().getEnd() == currPosition) {
                if (this.currNote.getAnchorRange().length() == 0) {
                    // this is a note not anchored to a range of text.
                    // add a marker at this point to show location
                    line.append("&#x273b;");
                }
                line.append("</span>");
                this.tagStarted = false;
                
                Note priorNote = this.currNote;
                this.currNote = null;
                if (this.noteItr.hasNext()) {
                    this.currNote = this.noteItr.next();
                    if (this.currNote.getAnchorRange().getStart() < currPosition) {
                        handleNestedNotes(line, currPosition, priorNote);
                    }
                }
            }
        }
    }
    
    private void handleNestedNotes(StringBuilder line, long currPos, Note parentNote ) {
        
        final String mark = "&#x26ac;&nbsp;";        
        StringBuffer content = new StringBuffer();
        content.append(mark).append(parentNote.getContent());
        
        while (true ) {
            boolean getNext = false;
            if ( this.currNote.getAnchorRange().getStart() < currPos ) {
                if ( this.currNote.getAnchorRange().getEnd() <= currPos ) {
                    content.append("<br/>").append(mark).append(this.currNote.getContent());
                    this.noteItr.remove();
                    getNext = true;
                } else {
                    // this is an overlapped note. can't handle it, so junk it
                    this.noteItr.remove();
                    getNext = true;
                }
            } else {
                break;
            }
            
            if ( getNext ) {
                if ( this.noteItr.hasNext() ) {
                    this.currNote = this.noteItr.next();
                } else {
                    this.currNote = null;
                    break;
                }
            } else {
                break;
            }
        }

        parentNote.setContent(content.toString());
    }

}
