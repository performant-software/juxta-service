package org.juxtasoftware.service;

import static eu.interedition.text.query.Criteria.text;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.dao.NoteDao;
import org.juxtasoftware.dao.PageBreakDao;
import org.juxtasoftware.dao.RevisionDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Note;
import org.juxtasoftware.model.PageBreak;
import org.juxtasoftware.model.RevisionSet;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.service.importer.jxt.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.collect.Maps;

import eu.interedition.text.AnnotationRepository;
import eu.interedition.text.Range;
import eu.interedition.text.Text;
import eu.interedition.text.TextConsumer;
import eu.interedition.text.TextRepository;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Transactional
public class SourceTransformer {
    @Autowired private AnnotationRepository annotationRepository;
    @Autowired private TextRepository textRepository;
    @Autowired private WitnessDao witnessDao;
    @Autowired private RevisionDao revisionDao;
    @Autowired private JuxtaXsltDao xsltDao;
    @Autowired private NoteDao noteDao;
    @Autowired private PageBreakDao pbDao;
    @Autowired private SourceDao sourceDao;

    /**
     * RE-run the transform of <code>srcDoc</code> into a prior witness
     * identified as <code>witness</>. The orignal witness text and annotations
     * will be replaced with new versions that result from appling the parse template
     * to the source again.
     * 
     * @param srcDoc
     * @param witness
     * @throws XMLStreamException 
     * @throws IOException 
     * @throws TransformerException 
     * @throws TransformerFactoryConfigurationError 
     * @throws TransformerConfigurationException 
     */
    public void redoTransform( Source srcDoc, Witness origWit ) throws SAXException, IOException, TransformerException  {

        // get original parse template
        JuxtaXslt xslt = null;
        if (srcDoc.getText().getType().equals(Text.Type.XML)) {
            xslt = this.xsltDao.find( origWit.getXsltId());
        }
        
        // clear out old witness stuff; annotations, page breaks and notes - BUT NOT text
        // can't kill it yet cuz witness refers to it. Must wait til after witness
        // text is updated!
        this.annotationRepository.delete( text(origWit.getText()) );
        this.noteDao.deleteAll( origWit.getId() );
        this.pbDao.deleteAll( origWit.getId() );
        
        // redo the transform
        Text parsedContent = srcDoc.getText();
        if (srcDoc.getText().getType().equals(Text.Type.XML)) {
            parsedContent = doTransform(srcDoc, xslt);
        } else {
            NullTransformReader rdr = new NullTransformReader();
            this.textRepository.read(srcDoc.getText(), rdr);
            parsedContent = rdr.getContent();
        }  
        
        // dump the transform results 
        this.witnessDao.updateContent(origWit, parsedContent);
        
        // Extract information about tags that require special handling
        RevisionSet revSet = null;
        if ( origWit.getRevisionSetId() != null ) {
            revSet = this.revisionDao.getRevsionSet(origWit.getRevisionSetId());
        }
        extractSpecialTags(srcDoc, origWit.getId(), xslt, revSet);
        
        // now it is safe to kill the original text text
        this.textRepository.delete( origWit.getText() );
    }
    
    /**
     * Transform <code>srcDoc</code> into a witness with the name <code>finalName</code>
     * using XSLT contained in <code>xslt</code>.  The resulting witness ID
     * is returned.
     * 
     * @param srcDoc The JuxtaSource to be transformed into a witness
     * @param template The parse template used to do the transform
     * @param revSet 
     * @param finalName The name of the resulting witness (optional)
     * @return The new witness ID
     * @throws SAXException 
     * @throws IOException
     * @throws TransformerException 
     */
    public Long transform(Source srcDoc, JuxtaXslt xslt, RevisionSet revSet, String finalName) throws SAXException, IOException, TransformerException {
        
        // transform into a new text_content object        
        Text parsedContent = srcDoc.getText();
        if (srcDoc.getText().getType().equals(Text.Type.XML)) {     
            parsedContent = doTransform(srcDoc, xslt);
        } else {
            NullTransformReader rdr = new NullTransformReader();
            this.textRepository.read(srcDoc.getText(), rdr);
            parsedContent = rdr.getContent();
        }   
        
        // use the transformed content to create a juxta witness
        Witness witness = new Witness();
        witness.setName(finalName);
        witness.setSourceId(srcDoc.getId());
        if ( xslt != null ) {
            witness.setXsltId(xslt.getId());
        }
        witness.setText(parsedContent);
        witness.setWorkspaceId( srcDoc.getWorkspaceId() );
        if ( revSet != null ) {
            witness.setRevisionSetId(revSet.getId());
        }
        Long id = this.witnessDao.create(witness);
        
        // pull info for tags that require special handling: note,pb & revs
        extractSpecialTags(srcDoc, id, xslt, revSet);
        
        return id;
    }

    private Text doTransform(Source srcDoc, JuxtaXslt xslt) throws IOException, TransformerException, FileNotFoundException {
        
        // be sure to use the saxon parser
        System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");
        
        // setup source, xslt and result
        File outFile = File.createTempFile("xform"+srcDoc.getId(), "xml");
        outFile.deleteOnExit();
        javax.xml.transform.Source xmlSource = new StreamSource( this.sourceDao.getContentReader(srcDoc) );
        javax.xml.transform.Source xsltSource =  new StreamSource( new StringReader(xslt.getXslt()) );
        javax.xml.transform.Result result = new StreamResult( outFile );
 
        // create an instance of TransformerFactory and do the transform
        TransformerFactory factory = TransformerFactory.newInstance(  );
        Transformer transformer = factory.newTransformer(xsltSource);  
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        transformer.setOutputProperty(OutputKeys.MEDIA_TYPE, "text");
        transformer.transform(xmlSource, result);
        
        // create a text repo entry for the new text
        Text parsedContent = this.textRepository.create( new FileReader(outFile) );
        outFile.delete();
        return parsedContent;
    }

    private void extractSpecialTags(final Source source, final Long witnessId, final JuxtaXslt xslt, final RevisionSet revSet) throws SAXException, IOException  {
        JuxtaExtractor extract = new JuxtaExtractor( source, witnessId, xslt, revSet);
        this.noteDao.create( extract.getNotes() );
        this.pbDao.create( extract.getPageBreaks() );
    }
    
    /**
     * Helper class to stream content from an existing plain txt source to
     * a new text_content entry.
     * @author loufoster
     *
     */
    private class NullTransformReader implements TextConsumer {
        private Text content;
        public NullTransformReader() {
            this.content = textRepository.create( Text.Type.TXT );
        }

        public Text getContent() {
            return this.content;
        }

        @Override
        public void read(Reader rdrContent, long contentLength) throws IOException {
            textRepository.write(this.content, rdrContent, contentLength);
        }
    }
    
    /**
     * JuxtaExtractor is a SAX xml parser that will collect the position information
     * for tags that require special handling by Juxta. Notes & Page Breaks
     * @author loufoster
     */
    private class JuxtaExtractor extends DefaultHandler  {
        private Note currNote = null;
        private StringBuilder currNoteContent;
        private List<Note> notes = new ArrayList<Note>();
        private List<PageBreak> breaks = new ArrayList<PageBreak>();
        private Map<String, Range> identifiedRanges = Maps.newHashMap();
        private Map<String,Integer> tagOccurences = Maps.newHashMap();
        private JuxtaXslt xslt;     
        private Long witnessId;
        private long currPos = 0;
        private boolean isExcluding = false;
        private Stack<String> exclusionContext = new Stack<String>();
        private Stack<String> xmlIdStack = new Stack<String>();

        private JuxtaExtractor(final Source source, final Long witnessId, final JuxtaXslt xslt, RevisionSet revSet) throws SAXException, IOException {          
            this.xslt = xslt;
            this.witnessId = witnessId;
            Util.saxParser().parse( new InputSource( sourceDao.getContentReader(source) ), this);
        }
        
        public List<Note> getNotes() {
            return this.notes;
        }
        public List<PageBreak> getPageBreaks() {
            return this.breaks;
        }
        
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            // always count up the number of occurrences for this tag
            countOccurrences(qName);
            
            if ( this.isExcluding ) {
                this.exclusionContext.push(qName);
                return;
            }
            
            if ( isSpecialTag(qName) == false && this.xslt.isExcluded(qName, this.tagOccurences.get(qName))) {
                this.isExcluding = true;
                this.exclusionContext.push(qName);
                System.err.println(qName+"["+this.tagOccurences.get(qName)+"] is excluded");
            }
                        
            if ( qName.equals("note") ) {
                handleNote(attributes);
            } else if (qName.equals("pb") ) {
                handlePageBreak(attributes);
            } else {
                final String idVal = getIdValue(attributes);
                if ( idVal != null ) {
                    this.identifiedRanges.put(idVal, new Range(this.currPos, this.currPos));
                    this.xmlIdStack.push(idVal);
                } else  {
                    this.xmlIdStack.push("NA");
                }
            }
        }
        
        private void countOccurrences(String qName) {
            Integer cnt = this.tagOccurences.get(qName);
            if ( cnt == null ) {
                this.tagOccurences.put(qName, 1);
            } else {
                this.tagOccurences.put(qName, cnt+1);
            }
        }

        private boolean isSpecialTag( final String qName ) { 
            return (qName.equals("note") || qName.equals("pb"));
        }
        
        private void handleNote(Attributes attributes) {
            this.currNote = new Note();
            this.currNote.setWitnessId( this.witnessId );
            this.currNote.setAnchorRange(new Range(this.currPos, this.currPos));
            this.currNoteContent = new StringBuilder();

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
            PageBreak pb = new PageBreak();
            pb.setWitnessId(this.witnessId);
            pb.setOffset(this.currPos);
            
            for (int idx = 0; idx<attributes.getLength(); idx++) {  
                String name = attributes.getQName(idx);
                if ( name.contains(":")) {
                    name = name.split(":")[1];
                }
                if ("n".equals(name)) {
                    pb.setLabel( attributes.getValue(idx) );
                } 
            }
            this.breaks.add(pb);
        }

        private String getIdValue( Attributes attributes ){
            for (int idx = 0; idx<attributes.getLength(); idx++) {  
                String val = attributes.getQName(idx);
                if ( val.contains(":")) {
                    val = val.split(":")[1];
                }
                if ( val.equals("id")) {
                    return attributes.getValue(idx);
                }
            }
            return null;
        }
        
        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if ( this.isExcluding ) {
                this.exclusionContext.pop();
                this.isExcluding = !this.exclusionContext.empty();
                return;
            }
            
            if (qName.equals("note")) {
                this.currNote.setContent(this.currNoteContent.toString().replaceAll("\\s+", " ").trim());
                if ( this.currNote.getContent().length() == 0 ) {
                    this.notes.remove(this.currNote);
                }
                this.currNote = null;
                this.currNoteContent = null;
            } else if (qName.equals("pb")) {
                // pagebreaks always include a linebreak. add 1 to
                // current position to account for this
                if ( this.currNote == null ) {
                    this.currPos++;
                }
            } else {
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
                    if ( this.xslt.hasLineBreak(qName) ){ 
                        this.currNoteContent.append("<br/>");
                    }
                } else  if ( this.xslt.hasLineBreak(qName) ){
                    this.currPos++;
                }
               
            }            
        }
        
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if ( this.isExcluding == false ) {
                String txt = new String(ch, start, length);
                
                // remove last newline and trailing space (right trim)
                txt = txt.replaceAll("[\\n]\\s*$", "");
                
                // remove first newline and traiing whitespace.
                // this will leave any leading whitespace before the 1st newline
                txt = txt.replaceAll("[\\n]\\s*", "");

                //System.err.println("["+txt+"]");
                if ( this.currNote != null ) {
                    this.currNoteContent.append(txt);
                } else {
                    this.currPos += txt.length();
                }
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
    }   
}
