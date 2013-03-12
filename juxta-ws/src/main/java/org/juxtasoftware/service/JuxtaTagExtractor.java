package org.juxtasoftware.service;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Note;
import org.juxtasoftware.model.PageMark;
import org.juxtasoftware.model.RevisionInfo;
import org.juxtasoftware.service.importer.jxt.Util;
import org.juxtasoftware.service.importer.ps.WitnessParser.PsWitnessInfo;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.collect.Maps;

import eu.interedition.text.Range;

/**
 * JuxtaExtractor is a SAX xml parser that will collect the position information
 * for tags that require special handling by Juxta: notes, pagebreaks and revisions.
 * If requested, it will also extract witness content from a TEI parallel segmented source
 * 
 * @author loufoster
 */
public class JuxtaTagExtractor extends DefaultHandler  {
    private Note currNote = null;
    private StringBuilder currNoteContent;
    private List<Note> notes = new ArrayList<Note>();
    private List<PageMark> marks = new ArrayList<PageMark>();
    private Map<String, Range> identifiedRanges = Maps.newHashMap();
    private Map<String,Integer> tagOccurences = Maps.newHashMap();
    private JuxtaXslt xslt;     
    private long currPos = 0;
    private boolean isExcluding = false;
    private Stack<String> exclusionContext = new Stack<String>();
    private Stack<String> xmlIdStack = new Stack<String>();
    private Stack<ExtractRevision> revisionExtractStack = new Stack<ExtractRevision>();
    private List<RevisionInfo> revisions = new ArrayList<RevisionInfo>();
    private PsWitnessInfo psWitnessInfo;
    private StringBuilder psWitnessContent;
    private CharArrayWriter contentBuffer = new CharArrayWriter();
    private Stack<String> choiceStack = new Stack<String>();
    private boolean choiceIncluded = false;
    
    /**
     * For parallel segmentated sources, set the witness information that will
     * be used to extract content. 
     * @param info
     */
    public void setPsTargetWitness( final PsWitnessInfo info ) {
        this.psWitnessInfo = info;
        this.psWitnessContent = new StringBuilder();
    }

    public void extract(final Reader sourceReader, final JuxtaXslt xslt) throws SAXException, IOException {          
        this.xslt = xslt;
        Util.saxParser().parse( new InputSource(sourceReader), this);
    }
    
    public List<RevisionInfo> getRevisions() {
        return this.revisions;
    }
    public List<Note> getNotes() {
        return this.notes;
    }
    public List<PageMark> getPageMarks() {
        return this.marks;
    }
    public String getPsWitnessContent() {
        return this.psWitnessContent.toString();
    }
    
    private boolean isChoice( final String qName ) {
        final String localName = stripNamespace(qName);
        return ( localName.equals("choice") );
    }
    private boolean isRevision(final String qName ) {
        final String localName = stripNamespace(qName);
        return ( localName.equals("add") || localName.equals("addSpan") ||
            localName.equals("del") || localName.equals("delSpan"));
    }
    private boolean isNote( final String qName ) {
        final String localName = stripNamespace(qName);
        return ( localName.equals("note") );
    }
    private boolean isPageBreak( final String qName ) {
        final String localName = stripNamespace(qName);
        return ( localName.equals("pb") );
    }
    private boolean isLineNumber( final String qName ) {
        final String localName = stripNamespace(qName);
        return ( localName.equals("l") );
    }
    private boolean isParagraph( final String qName ) {
        final String localName = stripNamespace(qName);
        return ( localName.equals("p") );
    }
    private boolean isPsWitnessContent( final String qName ) {
        final String localName = stripNamespace(qName);
        return ( localName.equals("rdg") ||  localName.equals("lem"));
    }
    private String stripNamespace( final String qName ) {
        if ( qName.indexOf(":") > 0 ) {
            return qName.substring(qName.indexOf(":")+1);
        }
        return qName;
    }
    
    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws IOException, SAXException {
        if (systemId.endsWith(".dtd") || systemId.endsWith(".ent")) {
            StringReader stringInput = new StringReader(" ");
            return new InputSource(stringInput);
        }
        else {
            return super.resolveEntity(publicId, systemId);
        }
    }
    
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        // each time a new element starts, be sure to process the content of the prior
        handleContent();
        
        // If there is a default namespace, the qName will not have it prepended here.
        // Do it now because all of the exclusion/linefeed data in the XSLT must have it.
        if ( this.xslt.getDefaultNamespace() != null && this.xslt.getDefaultNamespace().length() > 0) {
            qName = this.xslt.getDefaultNamespace() + ":" + qName;
        }
        
        // always count up the number of occurrences for this tag
        countOccurrences(qName);
        
        // if an exclusion is currently in process, just push this qName
        // onto the context stack and bail
        if ( this.isExcluding ) {
            this.exclusionContext.push(qName);
            return;
        }
      
        // cache the exclusion state of this tag. Kinda expensive and used multiple times
        boolean isExcluded = this.xslt.isExcluded(qName, this.tagOccurences.get(qName));
        
        // special choice tag handling for ps witnesses: only take content from the
        // first child for each choice element
        if ( this.psWitnessContent != null ) {
            if ( isExcluded == false && this.choiceStack.size() >= 1 &&   this.choiceIncluded == true) {
                // once the first nested tag of a choice is grabbed, bag the rest
                isExcluded = true;
            }
        }
        
        // Handle all tags with special extraction behavior first
        if ( this.psWitnessInfo != null && isPsWitnessContent(qName) ) {
            // exclude content from witnesses we are not interested in
            if ( matchesTargetWitness( attributes ) == false ) {
                this.isExcluding = true;
                this.exclusionContext.push(qName);
            } 
        } else if (isChoice(qName) ) {
            this.choiceIncluded = false;
            this.choiceStack.push(qName);
        }else if ( isRevision(qName) ) {
            this.revisionExtractStack.push( new ExtractRevision(isExcluded, this.currPos) );
        } else if ( isNote(qName) ) {
            handleNote(attributes);
        } else if ( isPageBreak(qName) ) {
            handlePageBreak(attributes);
        } else if ( isLineNumber(qName) ) {
            handleLineNumber(attributes);
        }else {
            // once a choice tag is found, keep pushing tags onto the stwck
            if ( this.choiceStack.size() >=1 ) {
                this.choiceStack.push(qName);
            }
            
            // default handling for all other tags
            if ( isExcluded ) {
                this.isExcluding = true;
                this.exclusionContext.push(qName);
            } else {
                if ( isParagraph(qName)) {
                    extractParagraphNumber(attributes);
                }
                final String idVal = getAttributeValue("id", attributes);
                if ( idVal != null ) {
                    this.identifiedRanges.put(idVal, new Range(this.currPos, this.currPos));
                    this.xmlIdStack.push(idVal);
                } else  {
                    this.xmlIdStack.push("NA");
                }
            }
        }
    }
    
    private boolean matchesTargetWitness(Attributes attributes) {
        // get the value of the wit or lem attribute (only 1 should be present)
        String idAttr = getAttributeValue("wit", attributes);
        if ( idAttr == null ) {
            idAttr = getAttributeValue("lem", attributes);
            if ( idAttr == null ) {
                return false;
            }
        }
        
        // wit/lem ids are prefixed with # and separated by space.
        // Strip the # and break up into tokens. See if one of the
        // IDs matches the target ID for this parser pass.
        idAttr = idAttr.replaceAll("#", "");
        String[] ids = idAttr.split(" ");
        for ( int i=0; i<ids.length; i++) {
            String id = ids[i].trim();
            if ( id.equals(this.psWitnessInfo.getId()) || 
                 id.equals(this.psWitnessInfo.getGroupId()) ) {
                return true;
            }
        }
        return false;
    }

    private void countOccurrences(String qName) {
        Integer cnt = this.tagOccurences.get(qName);
        if ( cnt == null ) {
            this.tagOccurences.put(qName, 1);
        } else {
            this.tagOccurences.put(qName, cnt+1);
        }
    }
    
    private void handleNote(Attributes attributes) {
        this.currNote = new Note();
        this.currNote.setAnchorRange(new Range(this.currPos, this.currPos));
        this.currNoteContent = new StringBuilder();
        //System.err.println("======> NOTE "+this.currPos);

        // search note tag attributes for type and target and add them to the note.
        for (int idx = 0; idx<attributes.getLength(); idx++) {  
            String name = attributes.getQName(idx);
            if ( name.contains(":")) {
                name = name.split(":")[1];
            }
            if ("type".equals(name)) {
                this.currNote.setType(attributes.getValue(idx));
            } else if ("target".equals(name)) {
                this.currNote.setTargetID(attributes.getValue(idx));
            }
        }
        this.notes.add(this.currNote);
    }

    private void handlePageBreak(Attributes attributes) {
        PageMark pb = new PageMark();
        pb.setOffset(this.currPos);
        pb.setType(PageMark.Type.PAGE_BREAK);
        //System.err.println("======> PB "+this.currPos);
        
        for (int idx = 0; idx<attributes.getLength(); idx++) {  
            String name = attributes.getQName(idx);
            if ( name.contains(":")) {
                name = name.split(":")[1];
            }
            if ("n".equals(name)) {
                pb.setLabel( attributes.getValue(idx) );
            } 
        }
        this.marks.add(pb);
    }
    
    private void handleLineNumber(Attributes attributes) {
        PageMark mark = new PageMark();
        mark.setOffset(this.currPos);
        mark.setType(PageMark.Type.LINE_NUMBER);
        //System.err.println("======> LINE NUMBER "+this.currPos);
        
        for (int idx = 0; idx<attributes.getLength(); idx++) {  
            String name = attributes.getQName(idx);
            if ( name.contains(":")) {
                name = name.split(":")[1];
            }
            if ("n".equals(name)) {
                mark.setLabel( "L"+attributes.getValue(idx) );
            } 
        }
        if ( mark.getLabel().trim().length() > 0 ) {
            this.marks.add(mark);
        }
    }
    
    private void extractParagraphNumber(Attributes attributes) {
        PageMark mark = new PageMark();
        mark.setOffset(this.currPos);
        mark.setType(PageMark.Type.LINE_NUMBER);
        
        for (int idx = 0; idx<attributes.getLength(); idx++) {  
            String name = attributes.getQName(idx);
            if ( name.contains(":")) {
                name = name.split(":")[1];
            }
            if ("n".equals(name)) {
                mark.setLabel( "P"+attributes.getValue(idx) );
            } 
        }
        if (  mark.getLabel().length() > 0 ) {
            this.marks.add(mark);
        }
    }

    private String getAttributeValue( final String name, final Attributes attributes ){
        for (int idx = 0; idx<attributes.getLength(); idx++) {  
            String val = attributes.getQName(idx);
            if ( val.contains(":")) {
                val = val.split(":")[1];
            }
            if ( val.equals(name)) {
                return attributes.getValue(idx);
            }
        }
        return null;
    }
    
    
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        // hande any buffered content from tha tag that has just finished
        handleContent();
        
        if ( this.isExcluding ) {
            this.exclusionContext.pop();
            this.isExcluding = !this.exclusionContext.empty();
            return;
        }
                
        // If there is a default namespace, the qName will not have it prepended here.
        // Do it now because all of the exclusion/linefeed data in the XSLT must have it.
        if ( this.xslt.getDefaultNamespace() != null && this.xslt.getDefaultNamespace().length() > 0) {
            qName = this.xslt.getDefaultNamespace() + ":" + qName;
        }
        
        if ( isRevision(qName) ) {
            ExtractRevision rev = this.revisionExtractStack.pop();
            final Range range = new Range(rev.startPosition, this.currPos);
            this.revisions.add( new RevisionInfo(qName, range, rev.content.toString(), !rev.isExcluded) );

            
        } else if ( isNote(qName) ) {
            this.currNote.setContent(this.currNoteContent.toString().replaceAll("\\s+", " ").trim());
            if ( this.currNote.getContent().length() == 0 ) {
                this.notes.remove(this.currNote);
            }
            this.currNote = null;
            this.currNoteContent = null;
        } else if ( isPageBreak(qName) ) {
            // pagebreaks always include a linebreak. add 1 to
            // current position to account for this
            if ( this.currNote == null ) {
                this.currPos++;
                if ( this.psWitnessContent != null ) {
                    this.psWitnessContent.append("\n");
                }
            }
        } else {
            if ( this.choiceStack.empty() == false ) {
                this.choiceStack.pop();
                // back down to one tag in stack (choice). this means the first 
                // content nested under the choice tag has been processed. all the
                // rest will be excluded until choice is empty
                if ( this.choiceStack.size() == 1) {
                    this.choiceIncluded = true;
                }
            }
            // if the tag has an identifier, save it off for crossreference with targeted notes
            if ( this.xmlIdStack.empty() == false ) {
                final String xmlId = this.xmlIdStack.pop();
                if (xmlId.equals("NA") == false ) {
                    this.identifiedRanges.put(xmlId, new Range(this.identifiedRanges.get(xmlId).getStart(), this.currPos));
                }
            }
            
            // if this tag is in the midst of a note, check it for 
            // linebreaks and add a hard break now. Also, do NOT
            // increment position count if we are collecting a note.
            if ( this.currNote != null ) {
                if ( this.xslt.hasLineBreak(qName, this.tagOccurences.get(qName)) ){ 
                    this.currNoteContent.append("<br/>");
                }
            } else  if ( this.xslt.hasLineBreak(qName, this.tagOccurences.get(qName)) ){
                // Only add 1 for the linebreak if we are non-revision or included revision
                if ( this.revisionExtractStack.empty() || this.revisionExtractStack.peek().isExcluded == false) {
                    this.currPos++;
                    if ( this.psWitnessContent != null ) {
                        this.psWitnessContent.append("\n");
                    }
                }
            }
        } 
    }
    
    private void handleContent() {
        String txt = this.contentBuffer.toString();
        if ( txt.length() == 0 ) {
            return;
        }

        txt = txt.replaceAll("[\\n]\\s*$", " ");
        txt = txt.replaceAll("^[\\n]\\s*$", " ");
        txt = txt.replaceAll("\\n+", " ");
        txt = txt.replaceAll("\\s+", " ");
        
//        if ( txt.length() > 0 ) {
//            System.err.println("["+txt+"]");
//        }
        
        if ( this.currNote != null ) {
            this.currNoteContent.append(txt);
        } else {
            if ( this.choiceStack.size() >= 1 && this.choiceIncluded == false ) {
                this.currPos += txt.length();
                if ( this.psWitnessContent != null ) {
                    this.psWitnessContent.append(txt);
                }
            } else {
                if ( this.revisionExtractStack.empty() || this.revisionExtractStack.peek().isExcluded == false) {
                    this.currPos += txt.length();
                    if ( this.psWitnessContent != null ) {
                        this.psWitnessContent.append(txt);
                    }
                }
                
                if ( this.revisionExtractStack.empty() == false ) {
                    this.revisionExtractStack.peek().content.append(txt);
                }
            }
        }
        
        this.contentBuffer.reset();
    }
    
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if ( this.isExcluding == false ) {
            // just buffer data here.. it may not be complete
            this.contentBuffer.write( ch, start, length );
        }
    }
    
    @Override
    public void endDocument() throws SAXException {
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
    
    /**
     * Track extraction of revision info during parse pass
     */
    static class ExtractRevision  {
        final boolean isExcluded;
        final long startPosition;
        StringBuilder content = new StringBuilder();
        ExtractRevision( boolean exclude, long start) {
            this.isExcluded = exclude;
            this.startPosition = start;
        }
    }
}   