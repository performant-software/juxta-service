package org.juxtasoftware.util.xml.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.juxtasoftware.model.Note;

import com.google.common.collect.Maps;

import eu.interedition.text.Name;
import eu.interedition.text.Range;
import eu.interedition.text.TextConstants;
import eu.interedition.text.xml.XMLEntity;
import eu.interedition.text.xml.XMLParserState;
import eu.interedition.text.xml.module.XMLParserModuleAdapter;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class NoteCollectingXMLParserModule extends XMLParserModuleAdapter {
    
    // the current note and its content
    private Note currNote;
    private StringBuilder currNoteContent;
    
    // final list of notes encountered in the text
    private List<Note> notes = new ArrayList<Note>();
    
    // a map of xml id to range for all tags that have an ID
    private Map<String, Range> identifiedRanges = Maps.newHashMap();

    public NoteCollectingXMLParserModule() {
    }

    public List<Note> getNotes() {
        return this.notes;
    }

    @Override
    public void start(XMLEntity entity, XMLParserState state) {
        final long textOffset = state.getTextOffset();
        final Map<Name, String> attributes = entity.getAttributes();
        final String localName = entity.getName().getLocalName();

        // is this a note tag?
        if ("note".equals(localName)) {
            this.currNote = new Note();
            this.currNote.setAnchorRange(new Range(textOffset, textOffset));
            this.currNoteContent = new StringBuilder();

            // search note tag attributes for type and target
            // and add them to the note.
            for (Name attrName : attributes.keySet()) {
                final String attrLocalName = attrName.getLocalName();
                if ("type".equals(attrLocalName)) {
                    this.currNote.setType(attributes.get(attrName));
                } else if ("target".equals(attrLocalName)) {
                    this.currNote.setTargetID(attributes.get(attrName));
                }
            }
            
            this.notes.add(this.currNote);
      
        } else {
            // if the tag has an identifier, save it off for later 
            // crossreference with targeted notes
            final String xmlId = attributes.get(TextConstants.XML_ID_ATTR_NAME);
            if (xmlId != null) {
                this.identifiedRanges.put(xmlId, new Range(textOffset, textOffset));
            }
        }
    }

    @Override
    public void end(XMLEntity entity, XMLParserState state) {
        final long textOffset = state.getTextOffset();
        final Map<Name, String> attributes = entity.getAttributes();
        final String localName = entity.getName().getLocalName();
        
        // when current note has ended, add the collected
        // content to it
        if ("note".equals(localName)) {
            this.currNote.setContent(this.currNoteContent.toString().replaceAll("\\s+", " ").trim());
            if ( this.currNote.getContent().length() == 0 ) {
                this.notes.remove(this.currNote);
            }
            this.currNote = null;
            this.currNoteContent = null;
        } else {
            // if the tag has an identifier, save it off for later 
            // crossreference with targeted notes
            final String xmlId = attributes.get(TextConstants.XML_ID_ATTR_NAME);
            if (xmlId != null) {
                identifiedRanges.put(xmlId, new Range(identifiedRanges.get(xmlId).getStart(), textOffset));
            }
        }
    }

    @Override
    public void end(XMLParserState state) {
        // at the end of parsing, find all notes that have a target
        // specified. Look up that id and set the associated range
        // as the note anchor point
        for ( Note note : this.notes ) {
            String noteTargetId = note.getTargetID();
            if ( noteTargetId != null && noteTargetId.length() > 0){
                Range tgtRange = this.identifiedRanges.get(noteTargetId);
                if ( tgtRange != null ) {
                    note.setAnchorRange( tgtRange );
                }
            }
        }
    }

    @Override
    public void text(String text, XMLParserState state) {
        if ( this.currNoteContent != null ) {
            this.currNoteContent.append(text);
        }
    }
}
