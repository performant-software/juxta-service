package org.juxtasoftware.service;

import static eu.interedition.text.query.Criteria.text;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

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
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Witness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.SAXException;

import eu.interedition.text.AnnotationRepository;
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
        
        // extract pb, note and revision tags
        extractSpecialTags(srcDoc, this.witnessDao.find(origWit.getId()), xslt );
               
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
    public Long transform(Source srcDoc, JuxtaXslt xslt, String finalName) throws SAXException, IOException, TransformerException {        
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
        Long id = this.witnessDao.create(witness);
        witness.setId(id);
        
        // extract pb, note and revision tags
        extractSpecialTags(srcDoc, witness, xslt);
        
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
    
    /**
     * Extract tags that require special handling in juxta: Notes, PageBreaks and revisions
     * @param source
     * @param witnessId
     * @param xslt
     * @throws SAXException
     * @throws IOException
     */
    public void extractSpecialTags(final Source source, final Witness w, final JuxtaXslt xslt )  throws SAXException, IOException {
        JuxtaTagExtractor extractor = new JuxtaTagExtractor( );
        extractor.setWitnessId( w.getId() );
        extractor.extract( this.sourceDao.getContentReader(source), xslt);
        this.noteDao.create( extractor.getNotes() );
        this.pbDao.create( extractor.getPageBreaks() );
        this.witnessDao.addRevisions(  extractor.getRevisions() );
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
}
