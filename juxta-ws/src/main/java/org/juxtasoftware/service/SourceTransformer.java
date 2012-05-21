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
import org.juxtasoftware.util.xml.module.JuxtaTextXmlParserModule;
import org.juxtasoftware.util.xml.module.NoteCollectingXMLParserModule;
import org.juxtasoftware.util.xml.module.PageBreakXmlParserModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.interedition.text.AnnotationRepository;
import eu.interedition.text.Text;
import eu.interedition.text.TextConsumer;
import eu.interedition.text.TextRepository;
import eu.interedition.text.xml.XMLEntity;
import eu.interedition.text.xml.XMLParser;
import eu.interedition.text.xml.XMLParserConfiguration;
import eu.interedition.text.xml.XMLParserModule;
import eu.interedition.text.xml.module.LineElementXMLParserModule;

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
    @Autowired private XMLParser xmlParser;
    @Autowired private SourceDao sourceDao;
    
    private List<Note> notes;
    private List<PageBreak> pageBeaks;

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
    public void redoTransform( Source srcDoc, Witness origWit ) throws IOException, XMLStreamException, TransformerException {

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

        // Extract tags that jave special handling in juxta FIRST
        RevisionSet revSet = null;
        if ( origWit.getRevisionSetId() != null ) {
            revSet = this.revisionDao.getRevsionSet(origWit.getRevisionSetId());
        }
        extractSpecialTags(srcDoc.getText(), xslt, revSet);
        writeExtractedTags(origWit.getId());

        // if notes or pbs are INCLUDED they have just been handled above. Remove them now
        if ( xslt.isExcluded("note") == false) {
            xslt.addExclusion( "note");
        }
        if ( xslt.isExcluded("pb") == false) {
            xslt.addExclusion("pb");
        }
        
        // redo the transform
        Text parsedContent = srcDoc.getText();
        if (srcDoc.getText().getType().equals(Text.Type.XML)) {
            parsedContent = doTransform(srcDoc, xslt);
        } else {
            NullTransformReader rdr = new NullTransformReader();
            this.textRepository.read(srcDoc.getText(), rdr);
            parsedContent = rdr.getContent();
        }  
        
        // dump the results 
        this.witnessDao.updateContent(origWit, parsedContent);
        
        // now it is safe to kill the original text text
        this.textRepository.delete( origWit.getText() );
    }
    
    /**
     * Transform <code>srcDoc</code> into a witness with the name <code>finalName</code>
     * using parse template <code>template</code>.  The resulting witness ID
     * is returned.
     * 
     * @param srcDoc The JuxtaSource to be transformed into a witness
     * @param template The parse template used to do the transform
     * @param revSet 
     * @param finalName The name of the resulting witness (optional)
     * @return The new witness ID
     * @throws IOException
     * @throws XMLStreamException
     * @throws TransformerException 
     */
    public Long transform(Source srcDoc, JuxtaXslt xslt, RevisionSet revSet, String finalName) throws IOException, XMLStreamException, TransformerException {

        // pull info for tags that require special handling: note,pb & revs
        extractSpecialTags(srcDoc.getText(), xslt, revSet);
        
        // if notes or pbs are INCLUDED they have just been handled above. Remove them now
        if ( xslt.isExcluded("note") == false) {
            xslt.addExclusion( "note");
        }
        if ( xslt.isExcluded("pb") == false) {
            xslt.addExclusion("pb");
        }
        
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
        writeExtractedTags(id);
        
        return id;
    }

    private Text doTransform(Source srcDoc, JuxtaXslt xslt) throws IOException, TransformerException, FileNotFoundException {
        
        // use the saxon transformer cuz it understands xslt with REPLACE() which
        // is necessary to make the output match the client-size xslt output
        System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");
        
        // setup source, xslt and result
        File outFile = File.createTempFile("xform"+srcDoc.getId(), "xml");
        outFile.deleteOnExit();
        javax.xml.transform.Source xmlSource = new StreamSource( this.sourceDao.getContentReader(srcDoc) );
        //final String stringRegex = "\"replace(replace(.,'^\\s+',''),'\\s+',' ')\"";
        final String fixedXslt = xslt.getXslt();//.replace("\".\"", stringRegex);
        //System.out.println(fixedXslt);
        javax.xml.transform.Source xsltSource =  new StreamSource( new StringReader(fixedXslt) );
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

    private void extractSpecialTags(final Text source, final JuxtaXslt xslt, final RevisionSet revSet) throws IOException, XMLStreamException {
        
        // if everything has been excluded, there is noting to do here!
        if ( xslt.isExcluded("note") && xslt.isExcluded("pb") && revSet == null ) {
            return;
        }
        
        // create config for parser. Note that the parser by default creates
        // a new text record. In this case it is useless. Just delete it immediately
        JuxtaExtractorConfig pc = new JuxtaExtractorConfig( xslt, revSet);
        Text junk = this.xmlParser.parse(source, pc);
        this.textRepository.delete(junk);
        this.notes = pc.getNotes();
        this.pageBeaks = pc.getPageBreaks();
    }
    
    private void writeExtractedTags( final Long witnessId ) {
        // add in any note tag information that was discovered
        // during the transformation process.
        if (!this.notes.isEmpty()) {
            for (Note note : this.notes) {
                note.setWitnessId( witnessId );
            }
            this.noteDao.create(this.notes);
        }
        
        // add page breaks too!
        if ( this.pageBeaks.isEmpty() == false ) {
            for ( PageBreak pb : this.pageBeaks ) {
                pb.setWitnessId( witnessId );
            }
            this.pbDao.create(this.pageBeaks);
        }
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
     * Adapt parser to handle only special case tags: notes, page breaks and revision sites
     * @author loufoster
     *
     */
    private class JuxtaExtractorConfig implements XMLParserConfiguration {
        private NoteCollectingXMLParserModule noteModule;
        private PageBreakXmlParserModule pbModule;
        private JuxtaTextXmlParserModule textModule;
        private List<XMLParserModule> modules ;
        private JuxtaXslt xslt;

        private JuxtaExtractorConfig(final JuxtaXslt xslt, RevisionSet revSet) {
            if ( revSet != null ) {
                this.textModule = new JuxtaTextXmlParserModule(
                   SourceTransformer.this.annotationRepository, revSet.getRevisionIndexes());
            } else {
                this.textModule = new JuxtaTextXmlParserModule(
                    SourceTransformer.this.annotationRepository, new ArrayList<Integer>());
            }
            this.modules = new ArrayList<XMLParserModule>(4);
            this.modules.add( this.textModule );
            this.modules.add( new LineElementXMLParserModule() );
                        
            if ( xslt.isExcluded("pb") == false ) {
                this.pbModule = new PageBreakXmlParserModule();
                this.modules.add(this.pbModule);
            }
            if ( xslt.isExcluded("note") == false) {
                this.noteModule = new NoteCollectingXMLParserModule();
                this.modules.add(this.noteModule);
            }
            this.xslt = xslt;
        }
        
        public List<Note> getNotes() {
            if ( this.noteModule != null ) {
                return this.noteModule.getNotes();
            } 
            return new ArrayList<Note>();
        }
        public List<PageBreak> getPageBreaks() {
            if ( this.pbModule != null ) {
                return this.pbModule.getPageBreaks();
            }
            return new ArrayList<PageBreak>();
        }

        /**
         * implementation of XMLParserConfiguration interface
         */
        @Override
        public boolean included(XMLEntity entity) {
            // if notes ane pb are to be handled correctly, they are INCLUDED in the xslt at this point
            // for spacing to work out correctly we must tell the parer that they are NOT included
            if ( this.noteModule != null && entity.getName().getLocalName().equalsIgnoreCase("note")) {
                return false;
            }
            if ( this.pbModule != null && entity.getName().getLocalName().equalsIgnoreCase("pb")) {
                return false;
            }
            // in all other cases, default to the rules of the xslt
            return !this.xslt.isExcluded(entity.getName().getLocalName());
        }

        @Override
        public boolean excluded(XMLEntity entity) {
            // if notes ane pb are to be handled correctly, they are INCLUDED in the xslt at this point
            // for spacing to work out correctly we must tell the parer that they are NOT included
            if ( this.noteModule != null && entity.getName().getLocalName().equalsIgnoreCase("note")) {
                return true;
            }
            if ( this.pbModule != null && entity.getName().getLocalName().equalsIgnoreCase("pb")) {
                return true;
            }
            // in all other cases, default to the rules of the xslt
            return this.xslt.isExcluded(entity.getName().getLocalName());
        }

        @Override
        public boolean isLineElement(XMLEntity entity) {
            if ( this.noteModule != null && entity.getName().getLocalName().equalsIgnoreCase("note")) {
                return false;
            }
            if ( this.pbModule != null && entity.getName().getLocalName().equalsIgnoreCase("pb")) {
                return false;
            }
            return this.xslt.hasLineBreak( entity.getName().getLocalName() );
        }

        @Override
        public boolean isNotable(XMLEntity entity) {
            return false;
        }

        @Override
        public boolean isContainerElement(XMLEntity entity) {
            return false;
        }

        @Override
        public List<XMLParserModule> getModules() {
            return this.modules;
        }

        @Override
        public char getNotableCharacter() {
            return '\u25CA';
        }

        @Override
        public int getTextBufferSize() {
            return 102400;
        }

        @Override
        public boolean isCompressingWhitespace() {
            return true;
        }

        @Override
        public boolean isRemoveLeadingWhitespace() {
            return true;
        }

    }
}
