package org.juxtasoftware.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.input.ReaderInputStream;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.dao.NoteDao;
import org.juxtasoftware.dao.PageMarkDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Note;
import org.juxtasoftware.model.PageMark;
import org.juxtasoftware.model.RevisionInfo;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.HtmlUtils;
import org.juxtasoftware.util.WikiTextUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import eu.interedition.text.Text;
import eu.interedition.text.TextConsumer;
import eu.interedition.text.TextRepository;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Transactional
public class SourceTransformer {
    @Autowired private TextRepository textRepository;
    @Autowired private WitnessDao witnessDao;
    @Autowired private JuxtaXsltDao xsltDao;
    @Autowired private NoteDao noteDao;
    @Autowired private PageMarkDao pbDao;
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
        if (srcDoc.getType().equals(Source.Type.XML)) {
            xslt = this.xsltDao.find( origWit.getXsltId());
        }
        
        // clear out old witness stuff; annotations, page breaks and notes - BUT NOT text
        // can't kill it yet cuz witness refers to it. Must wait til after witness text is updated!
        this.noteDao.deleteAll( origWit.getId() );
        this.pbDao.deleteAll( origWit.getId() );
        this.witnessDao.clearRevisions( origWit );
        
        // redo the transform
        Text parsedContent = srcDoc.getText();
        if (srcDoc.getType().equals(Source.Type.XML)) {
            parsedContent = doTransform(srcDoc, xslt);
        } else if  ( srcDoc.getType().equals(Source.Type.HTML) ) {
            parsedContent = doHtmlTransform(srcDoc);
        } else if  ( srcDoc.getType().equals(Source.Type.WIKI) ) {
            parsedContent = doWikiTransform(srcDoc);
        }else {
            NullTransformReader rdr = new NullTransformReader();
            this.textRepository.read(srcDoc.getText(), rdr);
            parsedContent = rdr.getContent();
        }  
        
        // dump the transform results 
        this.witnessDao.updateContent(origWit, parsedContent);
        
        // extract pb, note and revision tags of xml documents
        if ( xslt != null ) {
            extractSpecialTags(srcDoc, this.witnessDao.find(origWit.getId()), xslt );
        }
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
    public Long transform(final Source srcDoc, final JuxtaXslt xslt, final String finalName) throws SAXException, IOException, TransformerException {        
        String witnessName = finalName;
        
        // transform into a new text_content object        
        Text parsedContent = null;
        if (srcDoc.getType().equals(Source.Type.XML)) {     
            parsedContent = doTransform(srcDoc, xslt);
        } else if  ( srcDoc.getType().equals(Source.Type.HTML) ) {
            parsedContent = doHtmlTransform(srcDoc);
        } else if  ( srcDoc.getType().equals(Source.Type.WIKI) ) {
            parsedContent = doWikiTransform(srcDoc);
        } else {
            NullTransformReader rdr = new NullTransformReader();
            this.textRepository.read(srcDoc.getText(), rdr);
            parsedContent = rdr.getContent();
        }   
        
        // use the transformed content to create a juxta witness
        Witness witness = new Witness();
        witness.setName(witnessName);
        witness.setSourceId(srcDoc.getId());
        if ( xslt != null ) {
            witness.setXsltId(xslt.getId());
        }
        witness.setText(parsedContent);
        witness.setWorkspaceId( srcDoc.getWorkspaceId() );
        Long id = this.witnessDao.create(witness);
        witness.setId(id);
        
        // extract pb, note and revision tags of xml documents
        if ( xslt != null ) {
            extractSpecialTags(srcDoc, witness, xslt);
        }
        
        return id;
    }
    
    private Text doWikiTransform(Source srcDoc) throws IOException {
        File htmlOut = WikiTextUtils.toTxt( new ReaderInputStream(this.sourceDao.getContentReader(srcDoc), "UTF-8") );
        FileInputStream fis = new FileInputStream(htmlOut);
        InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
        Text parsedContent = this.textRepository.create( isr );
        isr.close();
        htmlOut.delete();
        return parsedContent;
    }

    private Text doHtmlTransform(Source srcDoc) throws IOException {
        File htmlOut = HtmlUtils.toTxt( new ReaderInputStream(this.sourceDao.getContentReader(srcDoc), "UTF-8") );
        FileInputStream fis = new FileInputStream(htmlOut);
        InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
        Text parsedContent = this.textRepository.create( isr );
        isr.close();
        htmlOut.delete();
        return parsedContent;
    }

    private Text doTransform(Source srcDoc, JuxtaXslt xslt) throws IOException, TransformerException, FileNotFoundException, SAXException {        
        // setup source, xslt and result
        File outFile = File.createTempFile("xform"+srcDoc.getId(), "xml");
        outFile.deleteOnExit();
        
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setEntityResolver(new EntityResolver() {

            @Override
            public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
                if (systemId.endsWith(".dtd") || systemId.endsWith(".ent")) {
                    StringReader stringInput = new StringReader(" ");
                    return new InputSource(stringInput);
                }
                else {
                    return null; // use default behavior
                }
            }
        });
        SAXSource xmlSource = new SAXSource(reader, new InputSource( this.sourceDao.getContentReader(srcDoc) ));

        
        //javax.xml.transform.Source xmlSource = new StreamSource( this.sourceDao.getContentReader(srcDoc) );
        javax.xml.transform.Source xsltSource =  new StreamSource( new StringReader(xslt.getXslt()) );
        javax.xml.transform.Result result = new StreamResult( new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8"));
 
        // create an instance of TransformerFactory and do the transform
        TransformerFactory factory = TransformerFactory.newInstance(  );
        Transformer transformer = factory.newTransformer(xsltSource);  
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        transformer.setOutputProperty(OutputKeys.MEDIA_TYPE, "text");
        transformer.transform(xmlSource, result);
        
        // create a text repo entry for the new text
        FileInputStream fis = new FileInputStream(outFile);
        InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
        Text parsedContent = this.textRepository.create( isr );
        isr.close();
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
        extractor.extract( this.sourceDao.getContentReader(source), xslt); 
        for (Note note : extractor.getNotes()  ) {
            note.setWitnessId(w.getId());
        }
        this.noteDao.create(extractor.getNotes());
        
        for (PageMark pb : extractor.getPageMarks()  ) {
            pb.setWitnessId(w.getId());
        }
        this.pbDao.create( extractor.getPageMarks() );
        
        for (RevisionInfo rev : extractor.getRevisions()  ) {
            rev.setWitnessId(w.getId());
        }
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
