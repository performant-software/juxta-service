package org.juxtasoftware.resource;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.dao.PageMarkDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.PageMark;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.service.WitnessRemover;
import org.juxtasoftware.util.ConversionUtils;
import org.juxtasoftware.util.RangedTextReader;
import org.juxtasoftware.util.ftl.FileDirective;
import org.juxtasoftware.util.ftl.FileDirectiveListener;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.interedition.text.Range;

/**
 * Server resource for interacting with Witness documents.
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class WitnessResource extends BaseResource {

    private Witness witness = null;
    private Range range = null;
    
    @Autowired private WitnessDao witnessDao;
    @Autowired private WitnessRemover remover;
    @Autowired private JuxtaXsltDao xsltDao;
    @Autowired private PageMarkDao pageMarkDao;

    /**
     * Extract the text ID and range info from the request attributes
     */
    @Override
    protected void doInit() throws ResourceException {
        
        super.doInit();
        
        Long id = getIdFromAttributes("witnessId");
        if ( id == null ) {
            return;
        }
        this.witness = this.witnessDao.find(id);
        if ( validateModel(this.witness) == false ) {
            return;
        }

        // was a range set requested?
        if (getQuery().getValuesMap().containsKey("range") ) {
            String rangeInfo = getQuery().getValues("range");
            String ranges[] = rangeInfo.split(",");
            if ( ranges.length == 2) {
                this.range = new Range( 
                    Integer.parseInt(ranges[0]),
                    Integer.parseInt(ranges[1]) );
            } else {
                getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid Range specified");
            }
        } else {
            this.range = new Range(0, this.witness.getText().getLength());
        }
    }
    
    @Get("txt")
    public Representation toPlainText() {   
        try {
            final RangedTextReader reader = new RangedTextReader();
            reader.read( this.witnessDao.getContentStream(this.witness), this.range );
            return toTextRepresentation(reader.toString());
            
        } catch (IOException e) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Error retrieving witness: "+e.getMessage());
        }
    }
    
    @Get("json")
    public Representation toJson() {   
        try {
            final File wit = witnessToHtml();
            FileReader fr = new FileReader(wit);
            
            JsonObject obj = new JsonObject();
            obj.addProperty("id", this.witness.getId());
            obj.addProperty("name", this.witness.getName());
            obj.addProperty("sourceId", this.witness.getSourceId());
            obj.addProperty("xsltId", this.witness.getXsltId());
            obj.addProperty("content", IOUtils.toString(fr));
            obj.addProperty("hasLineNumbers", this.pageMarkDao.hasLineNumbers(this.witness.getId()) );
            obj.addProperty("hasPageBreaks",  this.pageMarkDao.hasBreaks(this.witness.getId()) );
            if ( this.witness.getUpdated() != null && this.witness.getUpdated().after(this.witness.getCreated() ) ) {
                obj.addProperty("xmlTemplate", "Custom");
            } else {
                if ( this.witness.getXsltId() != null ) {
                    final JuxtaXslt xslt = this.xsltDao.find(this.witness.getXsltId());
                    if ( xslt.getXslt().contains("tei:")) {
                        obj.addProperty("xmlTemplate", "TEI Default");
                    } else if (  xslt.getXslt().contains("ramheader")) {
                        obj.addProperty("xmlTemplate", "RAM Default");
                    } else if (  xslt.getXslt().contains("m_e")) {
                        obj.addProperty("xmlTemplate", "Juxta Default");
                    } else {
                        obj.addProperty("xmlTemplate", "XML Default");
                    }
                }
            }
            wit.delete();
            Gson gson = new Gson();
            return toJsonRepresentation(gson.toJson(obj));
        } catch (IOException e) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Error retrieving witness: "+e.getMessage());
        }
    }
    
    @Get("html")
    public Representation toHtml() {   
        try {
            final File wit = witnessToHtml();
            FileDirective fd = new FileDirective();
            fd.setListener( new FileDirectiveListener() {
                @Override
                public void fileReadComplete(File file) {
                    wit.delete();
                }
            });
            
            Map<String,Object> map = new HashMap<String,Object>();
            map.put("name", witness.getName());
            map.put("witnessFile", wit);
            map.put("fileReader", fd); 
            map.put("page", "witness");
            map.put("title", "Juxta Witness: "+witness.getName());
            
            return toHtmlRepresentation("witness.ftl", map);
        } catch (IOException e) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Error retrieving witness: "+e.getMessage());
        }
    }
    
    private File witnessToHtml() throws IOException {
        Reader reader = this.witnessDao.getContentStream(this.witness);
        List<PageMark> marks = this.pageMarkDao.find(this.witness.getId() );
        return ConversionUtils.witnessToHtml(reader, this.range, marks);
    }
    
    /**
     * Accept json data to update the name of a witness.
     * format: { "name": "newName" }
     * @param jsonStr
     */
    @Put("json")
    public Representation rename( final String jsonStr ) {
        JsonParser parser = new JsonParser();
        JsonObject jsonObj =  parser.parse(jsonStr).getAsJsonObject();
        String name = jsonObj.get("name").getAsString();
        
        // make sure name chages don't cause conflicts
        if ( this.witness.getName().equals(name) == false ) {
            if ( this.witnessDao.exists(this.workspace,name)) {
                setStatus(Status.CLIENT_ERROR_CONFLICT);
                return toTextRepresentation("Set "+name+" already exists in workspace "
                    +this.workspace.getName());
            }
        }
        
        this.witnessDao.rename(this.witness, name);
        return toTextRepresentation( this.witness.getId().toString());
    }
    
    /**
     * Delete the specified witness
     */
    @Delete
    public Representation deleteWitness() {   
        try {
            LOG.info("Delete witness "+this.witness.getId());
            List<Usage> usage = this.remover.remove(this.witness);
            Gson gson = new Gson();
            return toJsonRepresentation( gson.toJson(usage) );
        } catch (ResourceException e) {
            Status statusCode = e.getStatus();
            setStatus( statusCode);
            return toTextRepresentation( e.getStatus().getDescription() );
        }
    }
}
