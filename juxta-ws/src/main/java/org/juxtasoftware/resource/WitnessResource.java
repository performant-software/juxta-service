package org.juxtasoftware.resource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringEscapeUtils;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.RangedTextReader;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.dao.DataIntegrityViolationException;
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
    @Autowired private JuxtaXsltDao xsltDao;
    @Autowired private ComparisonSetDao setDao;

    /**
     * Extract the text ID and range info from the request attributes
     */
    @Override
    protected void doInit() throws ResourceException {
        
        super.doInit();
        
        // this request must contain a witness if
        Long id = Long.parseLong( (String)getRequest().getAttributes().get("witnessId"));
        this.witness = this.witnessDao.find(id);
        
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
        }
        
        validateModel(this.witness);
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
            final RangedTextReader reader = new RangedTextReader();
            reader.read( this.witnessDao.getContentStream(this.witness), this.range );
            JsonObject obj = new JsonObject();
            obj.addProperty("id", this.witness.getId());
            obj.addProperty("name", this.witness.getName());
            obj.addProperty("sourceId", this.witness.getSourceId());
            obj.addProperty("xsltId", this.witness.getXsltId());
            obj.addProperty("content", reader.toString());
            Gson gson = new Gson();
            return toTextRepresentation(gson.toJson(obj));
        } catch (IOException e) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Error retrieving witness: "+e.getMessage());
        }
    }
    
    @Get("html")
    public Representation toHtml() {   
        try {
            final RangedTextReader reader = new RangedTextReader();
            reader.read( this.witnessDao.getContentStream(this.witness), this.range );
            Map<String,Object> map = new HashMap<String,Object>();
            map.put("name", witness.getName());
            map.put("text", StringEscapeUtils.escapeHtml( reader.toString()).replaceAll("\n", "<br/>"));
            map.put("page", "witness");
            map.put("title", "Juxta Witness: "+witness.getName());
            return toHtmlRepresentation("witness.ftl", map);
        } catch (IOException e) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Error retrieving witness: "+e.getMessage());
        }
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
        LOG.info("Delete witness "+this.witness.getId());
        
        // delete the witness  - this will schedule deletion of all
        // witness text, annotations and sets collation data that used it
        LOG.info("DELETE "+this.witness);
        List<Usage> usage = this.witnessDao.getUsage( this.witness ); 
        for (Usage u : usage ) {
            if ( u.getType().equals(Usage.Type.COMPARISON_SET)) {
                ComparisonSet s = this.setDao.find(u.getId());
                if ( s.getStatus().equals(ComparisonSet.Status.COLLATING)) {
                    setStatus(Status.CLIENT_ERROR_CONFLICT);
                    return toTextRepresentation("Cannot delete witness; related set '"+s.getName()+"' is collating.");
                }
            }
        }
        
        this.witnessDao.delete( this.witness );
        JuxtaXslt xslt = this.xsltDao.find( this.witness.getXsltId() );
        try {
            this.xsltDao.delete( xslt );
        } catch ( DataIntegrityViolationException e) {
            // This happens for TEI ps imports. One XSLT
            // has multiple witnesses. Only when the last witness
            // is deleted will this succeed
        }
        
        // return the json list of itmes that were affected
        Gson gson = new Gson();
        return toJsonRepresentation( gson.toJson(usage) );
    }
}
