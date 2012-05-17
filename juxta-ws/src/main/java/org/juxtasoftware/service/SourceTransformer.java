package org.juxtasoftware.service;

import static eu.interedition.text.query.Criteria.text;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import javax.xml.stream.XMLStreamException;
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
import org.juxtasoftware.model.RevisionSet;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Witness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.interedition.text.AnnotationRepository;
import eu.interedition.text.Text;
import eu.interedition.text.TextConsumer;
import eu.interedition.text.TextRepository;
import eu.interedition.text.xml.XMLParser;

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
        
//        // get original revision set info
//        RevisionSet revSet = null;
//        if ( origWit.getRevisionSetId() != null ) {
//            revSet = this.revisionDao.getRevsionSet(origWit.getRevisionSetId());
//        }
        // TODO revision set stuff
        
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
        
        // dump the results 
        this.witnessDao.updateContent(origWit, parsedContent);
        
        // TODO
        //writeNotesAndPageBreaks(pc, origWit.getId());
        
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

        // TODO
        //writeNotesAndPageBreaks(pc, id);
        
        return id;
    }

    private Text doTransform(Source srcDoc, JuxtaXslt xslt) throws IOException, TransformerException, FileNotFoundException {

        // setup source, xslt and result
        File outFile = File.createTempFile("xform"+srcDoc.getId(), "xml");
        outFile.deleteOnExit();
        javax.xml.transform.Source xmlSource = new StreamSource( this.sourceDao.getContentReader(srcDoc) );
        javax.xml.transform.Source xsltSource =  new StreamSource( new StringReader(xslt.getXslt()) );
        javax.xml.transform.Result result = new StreamResult( outFile );
 
        // create an instance of TransformerFactory and do the transform
        TransformerFactory factory = TransformerFactory.newInstance(  );
        Transformer transformer = factory.newTransformer(xsltSource);  
        transformer.transform(xmlSource, result);
        
        // create a text repo entry for the new text
        Text parsedContent = this.textRepository.create( new FileReader(outFile) );
        outFile.delete();
        return parsedContent;
    }

//    private void writeNotesAndPageBreaks(final XMLParserConfigurationAdapter pc, Long witnessId) {
//        // add in any note tag information that was discovered
//        // during the transformation process.
//        if ( pc.notesIncluded() ) {
//            final List<Note> notes = pc.noteModule.getNotes();
//            if (!notes.isEmpty()) {
//                for (Note note : notes) {
//                    note.setWitnessId(witnessId);
//                }
//                this.noteDao.create(notes);
//            }
//        }
//        
//        // add page breaks too!
//        if ( pc.pageBreaksIncluded() ) {
//            final List<PageBreak> pbs = pc.pbModule.getPageBreaks();
//            if ( pbs.isEmpty() == false ) {
//                for ( PageBreak pb : pbs ) {
//                    pb.setWitnessId(witnessId);
//                }
//                this.pbDao.create(pbs);
//            }
//        }
//    }
    
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
    
//    /**
//     * Adapt parse template into an XMLParserConfiguration
//     * @author loufoster
//     *
//     */
//    private class XMLParserConfigurationAdapter implements XMLParserConfiguration {
//        private final Template template;
//        private final char notableCharacter = '\u25CA';
//        private final boolean compressingWhitespace = true;
//        private final int textBufferSize = 102400;
//        private final boolean removeLeadingWhitespace = true;
//        private NoteCollectingXMLParserModule noteModule;
//        private PageBreakXmlParserModule pbModule;
//        private JuxtaTextXmlParserModule textModule;
//
//        private List<XMLParserModule> modules = Lists.<XMLParserModule>newArrayList(
//                new LineElementXMLParserModule(),
//                new NotableCharacterXMLParserModule()
//        );
//
//        private XMLParserConfigurationAdapter(final Template template, RevisionSet revSet) {
//            this.template = new Template(template);
//            if ( revSet != null ) {
//                this.textModule = new JuxtaTextXmlParserModule(
//                   SourceTransformer.this.annotationRepository, revSet.getRevisionIndexes());
//            } else {
//                this.textModule = new JuxtaTextXmlParserModule(
//                    SourceTransformer.this.annotationRepository, new ArrayList<Integer>());
//            }
//            this.modules.add(this.textModule);
//                        
//            // specialized tag handling. If a user has opted to EXCLUDE
//            // notes, be sure that the note parser module is NOT added to the
//            // module list. If notes are INCLUDED, add the filter and 
//            // EXCLUDE them from the main template.
//            boolean notesExcluded = false;
//            boolean pbExcluded = false;
//            for ( TagAction tagAct : this.template.getTagActions() ) {
//                WildcardQName tagName = tagAct.getTag();
//                if ( tagName.equals("*:*:note") &&
//                    tagAct.getAction().equalsIgnoreCase("exclude")) {
//                        notesExcluded = true;
//                }
//                if (tagAct.getTag().equals("*:*:pb") &&
//                    tagAct.getAction().equalsIgnoreCase("exclude")) {
//                        pbExcluded = true;
//                }
//            }
//            
//            if (pbExcluded == false ) {
//                this.pbModule = new PageBreakXmlParserModule();
//                this.modules.add(this.pbModule);
//            }
//            if (notesExcluded == false) {
//                this.noteModule = new NoteCollectingXMLParserModule();
//                this.modules.add(this.noteModule);
//                this.template.exclude("note"); 
//            }
//        }
//        
//        public final boolean notesIncluded() {
//            return (this.noteModule != null);
//        }
//        public final boolean pageBreaksIncluded() {
//            return (this.pbModule != null);
//        }
//
//        /**
//         * implementation of XMLParserConfiguration interface
//         */
//        @Override
//        public boolean included(XMLEntity entity) {
//            return hasTemplateMatch( TagAction.Action.INCLUDE, entity );
//        }
//
//        @Override
//        public boolean excluded(XMLEntity entity) {
//            return hasTemplateMatch( TagAction.Action.EXCLUDE, entity );
//        }
//
//        @Override
//        public boolean isLineElement(XMLEntity entity) {
//            if ( this.template.getName().equals("includeAll")) {
//                return true;
//            }
//            return hasTemplateMatch( TagAction.Action.NEW_LINE, entity );
//        }
//
//        @Override
//        public boolean isNotable(XMLEntity entity) {
//            return hasTemplateMatch( TagAction.Action.NOTABLE, entity );
//        }
//
//        private boolean hasTemplateMatch( final TagAction.Action action, XMLEntity entity ) {
//            String uri = "";
//            if ( entity.getName().getNamespace() != null ) {
//                uri = entity.getName().getNamespace().toString();
//            }
//            final String prefix = entity.getPrefix();
//            final String localName = entity.getName().getLocalName();
//
//            for (TagAction act : template.getTagActions() ) {
//                // quick test; make sure actions match
//                if ( act.getActionAsEnum().equals( action ) == false ) {
//                    continue;
//                }
//
//                // harder test; make sure names match with wildcards
//                if ( isWildCardStringMatch(act.getTag().getNamespaceUri(), uri) &&
//                     isWildCardStringMatch(act.getTag().getNamespacePrefix(), prefix) &&
//                     isWildCardStringMatch(act.getTag().getLocalName(), localName) ) {
//                    return true;
//                }
//            }
//            return false;
//        }
//
//        private boolean isWildCardStringMatch(final String src, final String tgt) {
//            return (src.equals("*") || src.equals(tgt));
//        }
//
//        @Override
//        public boolean isContainerElement(XMLEntity entity) {
//            return false;
//        }
//
//        @Override
//        public List<XMLParserModule> getModules() {
//            return this.modules;
//        }
//
//        @Override
//        public char getNotableCharacter() {
//            return this.notableCharacter;
//        }
//
//        @Override
//        public int getTextBufferSize() {
//            return this.textBufferSize;
//        }
//
//        @Override
//        public boolean isCompressingWhitespace() {
//            return this.compressingWhitespace;
//        }
//
//        @Override
//        public boolean isRemoveLeadingWhitespace() {
//            return this.removeLeadingWhitespace;
//        }
//
//    }
}
