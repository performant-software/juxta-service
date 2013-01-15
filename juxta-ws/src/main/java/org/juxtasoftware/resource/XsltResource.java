package org.juxtasoftware.resource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.dao.PageMarkDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.service.SourceTransformer;
import org.juxtasoftware.service.importer.JuxtaXsltFactory;
import org.juxtasoftware.service.importer.ps.ParallelSegmentationImportImpl;
import org.juxtasoftware.util.ConversionUtils;
import org.juxtasoftware.util.RangedTextReader;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.FileRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * REsource to get/update XSLT for a witness.
 * Also get the generic XSLT template.
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class XsltResource extends BaseResource  {
    @Autowired private JuxtaXsltDao xsltDao;
    @Autowired private SourceDao sourceDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private SourceTransformer transformer;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private ApplicationContext context;
    @Autowired private PageMarkDao pageMarkDao;
    
    private Long xsltId = null;
    private Long witnessId = null;
    private boolean templateRequest = false;
    private boolean previewRequest = false;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        if ( getRequest().getAttributes().containsKey("id")) {
            this.witnessId = Long.parseLong( (String)getRequest().getAttributes().get("id"));
        }
        if ( getRequest().getAttributes().containsKey("xsltId")) {
            this.xsltId = Long.parseLong( (String)getRequest().getAttributes().get("xsltId"));
        }
        this.previewRequest =  getQuery().getValuesMap().containsKey("preview");
        
        String lastSeg  = getRequest().getResourceRef().getLastSegment();
        this.templateRequest = ( lastSeg.equalsIgnoreCase("template"));
        
        validateParams();
    }
    
    private void validateParams() {
        if ( this.templateRequest && (this.witnessId != null || this.xsltId != null) ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return;
        }
        
        if ( this.witnessId != null && this.xsltId != null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return;
        }
        
        if (this.witnessId != null ) {
            Witness w = this.witnessDao.find(witnessId);
            if ( validateModel(w) == false ) {
                return;
            }
            this.xsltId = w.getXsltId();
        }
    }
    
    @Get("json")
    public Representation getJson() {
        if ( this.templateRequest ) {
            return getXsltTemplates();
        }
        
        if ( this.xsltId != null ) {
            JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
            Gson gson = new Gson();
            return toJsonRepresentation( gson.toJson(xslt));
        }
        
        // if all else has failed, just return the list of xslts
        List<JuxtaXslt> list = this.xsltDao.list(this.workspace);
        Gson gson = new Gson();
        return toJsonRepresentation( gson.toJson(list));
    }

    
    private Representation getXsltTemplates() {
        try {
            Map<String,String> templates = new HashMap<String,String>();
            templates.put("main", JuxtaXsltFactory.getGenericTemplate() );
            templates.put("singleExclude", JuxtaXsltFactory.getSingleExclusionTemplate() );
            templates.put("globalExclude", JuxtaXsltFactory.getGlobalExclusionTemplate() );
            templates.put("breaks", JuxtaXsltFactory.getBreaksTemplate() );
            templates.put("linebreak", "<xsl:value-of select=\"$display-linebreak\"/>");
            
            Gson gson = new Gson();
            return toJsonRepresentation( gson.toJson(templates));
        } catch (IOException e ) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Unable to retrieve XSLT templates: " +e.getMessage());
        }
    }
    
    @Get("xml")
    public Representation getXml() {
        
        if ( this.xsltId != null ) {
            JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
            if ( xslt == null ) {
                setStatus(Status.CLIENT_ERROR_NOT_FOUND);
                return toTextRepresentation("xslt "+this.xsltId+" does not exist");
            }
            return toXmlRepresentation( xslt.getXslt() );
        }
        
        setStatus(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE);
        return null;
    }
    
    @Post("json")
    public Representation createXslt( final String jsonData ) {
        Gson gson = new Gson();
        JuxtaXslt xslt = gson.fromJson(jsonData, JuxtaXslt.class);
        if ( this.previewRequest ) {
            return previewWitness(xslt);
        }
        
        xslt.setWorkspaceId(this.workspace.getId());
        if ( xslt.getName() == null || xslt.getXslt() == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("missing required data in json payload");
        }

        Long id = this.xsltDao.create(xslt);
        return toTextRepresentation( id.toString() );
    }
    
    private Representation previewWitness(JuxtaXslt xslt) {
        Witness w = this.witnessDao.find(this.witnessId);
        Source src = this.sourceDao.find(this.workspace.getId(), w.getSourceId());
        if (src.getType().equals(Source.Type.XML)) {
            try {
                final File out = doTransform(src, xslt);
                Reader r = new FileReader(out);
                File html = ConversionUtils.witnessToHtml(r, null, this.pageMarkDao.find(this.witnessId));
                out.delete();
                FileRepresentation rep = new FileRepresentation(html, MediaType.TEXT_HTML);
                rep.setAutoDeleting(true);
                return rep;
            } catch (Exception e) {
                LOG.error("Unable to preview XML witness", e);
                setStatus(Status.SERVER_ERROR_INTERNAL);
                return toTextRepresentation("Unable to preview witness at this time");
            }
        } else {
            final RangedTextReader reader = new RangedTextReader();
            try {
                reader.read( this.sourceDao.getContentReader(src) );
                return toTextRepresentation(reader.toString());
            } catch (IOException e) {
                LOG.error("Unable to preview TXT witness", e);
                setStatus(Status.SERVER_ERROR_INTERNAL);
                return toTextRepresentation("Unable to preview witness at this time");
            }
        }
    }

    private File doTransform(Source srcDoc, JuxtaXslt xslt) throws IOException, TransformerException, FileNotFoundException, SAXException {        
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
        javax.xml.transform.Source xsltSource =  new StreamSource( new StringReader(xslt.getXslt()) );
        javax.xml.transform.Result result = new StreamResult( new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8"));
        TransformerFactory factory = TransformerFactory.newInstance(  );
        Transformer transformer = factory.newTransformer(xsltSource);  
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        transformer.setOutputProperty(OutputKeys.MEDIA_TYPE, "text");
        transformer.transform(xmlSource, result);
        return outFile;
    }
    
    
    @Put("json")
    public Representation updateXslt( final String json ) {
        if ( this.templateRequest || this.xsltId == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return null;
        } 
        
        JsonParser parser = new JsonParser();
        JsonObject jsonObj = parser.parse(json).getAsJsonObject();
        final String updatedXslt = jsonObj.get("xslt").getAsString();
        final boolean isPs = jsonObj.get("tei_ps").getAsBoolean();

        JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
        if ( validateModel(xslt) == false ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("xslt "+this.xsltId+" does not exist");
        }        
  
        try {
            // make sure this doesn't break an in-process collation
            List<Usage> usage = this.xsltDao.getUsage(xslt);
            for (Usage u : usage) {
                if ( u.getType().equals(Usage.Type.COMPARISON_SET)) {
                    ComparisonSet s = this.setDao.find(u.getId());
                    if ( s.getStatus().equals(ComparisonSet.Status.COLLATING)) {
                        setStatus(Status.CLIENT_ERROR_CONFLICT);
                        return toTextRepresentation("Cannot prepare witness; related set '"+s.getName()+"' is collating.");
                    }
                }
            }
            
            // update the DB with new content for XSLT
            this.xsltDao.update(this.xsltId, new StringReader(updatedXslt) );
            
            
            // If this witness was generated from a TEI parallel segmented
            // source, it must be handled differently. Re-Import the source!
            if ( isPs ) {
                Witness w = this.witnessDao.find(this.witnessId);
                Source src = this.sourceDao.find(this.workspace.getId(), w.getSourceId());
                for(Usage u : usage) {
                    if ( u.getType().equals(Usage.Type.COMPARISON_SET)) {
                        ComparisonSet set = this.setDao.find( u.getId());
                        ParallelSegmentationImportImpl importService = this.context.getBean(ParallelSegmentationImportImpl.class);
                        importService.reimportSource(set, src);
                    }
                }
            } else {
                // FIRST PASS: clear collation data
                for(Usage u : usage) {
                    if ( u.getType().equals(Usage.Type.COMPARISON_SET)) {
                        ComparisonSet set = this.setDao.find( u.getId());
                        this.setDao.clearCollationData(set);
                    }
                }
                // SECOND PASS: re-transform
                for(Usage u : usage) {
                    if ( u.getType().equals(Usage.Type.WITNESS)) {
                        Witness origWit = this.witnessDao.find( u.getId() );
                        Source src = this.sourceDao.find(this.workspace.getId(), origWit.getSourceId());
                        this.transformer.redoTransform(src, origWit);
                    }
                }
            }
            Gson gson = new Gson();
            return toJsonRepresentation( gson.toJson(usage));
        } catch (Exception e) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation(e.getMessage());
        }
    }
    
    @Delete
    public void deletXslt( ) {
        if ( this.xsltId != null ) {
            JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
            if ( validateModel(xslt) != false ) {
                try {
                    this.xsltDao.delete(xslt);
                    return;
                } catch ( Exception e ) {
                    setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "cannot to delete xslt that is in use");
                    return;
                }
            } 
        }

        setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
    }
}
