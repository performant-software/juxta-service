package org.juxtasoftware.resource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.service.SourceTransformer;
import org.restlet.data.CharacterSet;
import org.restlet.data.Language;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class XsltResource extends BaseResource {
    @Autowired private JuxtaXsltDao xsltDao;
    @Autowired private SourceDao sourceDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private SourceTransformer transformer;
    @Autowired private ComparisonSetDao setDao;
    private Long xsltId = null;
    private boolean templateRequest = false;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        if ( getRequest().getAttributes().containsKey("id")) {
            this.xsltId = Long.parseLong( (String)getRequest().getAttributes().get("id"));
        }
        
        String lastSeg  = getRequest().getResourceRef().getLastSegment();
        this.templateRequest = ( lastSeg.equalsIgnoreCase("template"));
    }
    
    @Get("json")
    public Representation getJson() {
        if ( this.xsltId == null ) {
            if ( this.templateRequest ) {
                return getXsltTemplates();
            } else {
                List<JuxtaXslt> list = this.xsltDao.list(this.workspace);
                Gson gson = new Gson();
                return toJsonRepresentation( gson.toJson(list)); 
            }
        } else {
            JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
            Gson gson = new Gson();
            return toJsonRepresentation( gson.toJson(xslt)); 
        }
    }
    
    private Representation getXsltTemplates() {
        try {
            Map<String,String> templates = new HashMap<String,String>();
            templates.put("main", IOUtils.toString( ClassLoader.getSystemResourceAsStream("xslt/basic.xslt"), "utf-8"));
            templates.put("singleExclude", IOUtils.toString( ClassLoader.getSystemResourceAsStream("xslt/single-exclusion.xslt"), "utf-8"));
            templates.put("globalExclude", IOUtils.toString( ClassLoader.getSystemResourceAsStream("xslt/global-exclusion.xslt"), "utf-8"));
            templates.put("breaks", IOUtils.toString( ClassLoader.getSystemResourceAsStream("xslt/breaks.xslt"), "utf-8"));
            templates.put("linebreak", "<xsl:value-of select=\"$display-linebreak\"/>");
            
            Gson gson = new Gson();
            return toJsonRepresentation( gson.toJson(templates));
        } catch (IOException e ) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Unable to retrieve XSLT templates: " +e.getMessage());
        }
    }

    @Get("html")
    public Representation getHtml() {
        if ( this.xsltId == null ) {
            if ( this.templateRequest ) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return null;
            } else {
                List<JuxtaXslt> list = this.xsltDao.list(this.workspace);
                Gson gson = new Gson();
                return toJsonRepresentation( gson.toJson(list)); 
            }
        } else {
            JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
            return new StringRepresentation("<h2>"+xslt.getName()+"</h2><textarea style=\"width:90%;height:90%\" value=\""+xslt.getXslt()+"\"/>", 
                MediaType.TEXT_HTML,
                Language.DEFAULT,
                CharacterSet.UTF_8); 
        }
    }
    
    @Get("xml")
    public Representation getXml() {
        if ( this.xsltId == null ) {
            setStatus(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE);
            return null;
        }
        if ( this.templateRequest ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return null;
        } 
        JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
        return toXmlRepresentation( xslt.getXslt() );
    }
    
    @Post("json")
    public Representation createXslt( final String jsonData ) {
        if ( this.templateRequest || this.xsltId != null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return null;
        } 
        Gson gson = new Gson();
        JuxtaXslt xslt = gson.fromJson(jsonData, JuxtaXslt.class);
        Long id = this.xsltDao.create(xslt);
        return toTextRepresentation( id.toString() );
    }
    
    @Put
    public Representation updateXslt( final Representation entity ) {
        if ( this.xsltId == null ) {
            setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
            return null;
        }
        
        if ( this.templateRequest ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return null;
        } 
        
        if (MediaType.TEXT_XML.equals(entity.getMediaType()) == false) {
            setStatus(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE);
            return null;
        }
        
        JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
        if ( validateModel(xslt) == false ) {
            return null;
        }
  
        try {
            this.xsltDao.update(this.xsltId, new InputStreamReader(entity.getStream()) );
            
            // Get the witness that uses this XSLT. List should be of size 1.
            List<Usage> usage = this.xsltDao.getUsage(xslt);
            for(Usage u : usage) {
                if ( u.getType().equals(Usage.Type.WITNESS)) {
                    Witness origWit = this.witnessDao.find( u.getId() );
                    Source src = this.sourceDao.find(this.workspace.getId(), origWit.getSourceId());
                    this.transformer.redoTransform(src, origWit);
                } else if ( u.getType().equals(Usage.Type.COMPARISON_SET)) {
                    ComparisonSet set = this.setDao.find( u.getId());
                    this.setDao.clearCollationData(set);
                    
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
        if ( this.xsltId == null ) {
            setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
            return;
        }
        if ( this.templateRequest ) {
            setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
            return;
        } 
        JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
        if ( validateModel(xslt) != false ) {
            this.xsltDao.delete(xslt);
        } 
    }
}
