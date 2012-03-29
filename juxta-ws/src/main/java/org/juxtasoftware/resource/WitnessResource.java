package org.juxtasoftware.resource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringEscapeUtils;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.CacheDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.RangedTextReader;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
    private Long setId = null;
    private Range range = null;
    
    @Autowired private ComparisonSetDao setDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private CacheDao cacheDao;
    @Autowired private AlignmentDao alignmentDao;

    /**
     * Extract the text ID and range info from the request attributes
     */
    @Override
    protected void doInit() throws ResourceException {
        
        super.doInit();
        
        // this request must contain a witness if
        Long id = Long.parseLong( (String)getRequest().getAttributes().get("witnessId"));
        this.witness = this.witnessDao.find(id);
        
        // it may contain a set if the user is referencing a witness via a set api
        if ( getRequest().getAttributes().containsKey("setId")) {
            this.setId = Long.parseLong( (String)getRequest().getAttributes().get("setId"));
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
     * Delete the specified witness
     */
    @Delete
    public void deleteWitness() {   
        // Delete witness entirely, or just delete from a set?
        if ( this.setId != null ) {
            LOG.info("Delete witness "+this.witness.getId()+" from set "+this.setId);
            ComparisonSet set = this.setDao.find(this.setId);
            if ( set == null ) {
                setStatus(Status.CLIENT_ERROR_NOT_FOUND,"Invalid set "+this.setId);
                return;
            }
            if ( this.setDao.isWitness(set, witness) == false ) {
                setStatus(Status.CLIENT_ERROR_NOT_FOUND, 
                    "Witness "+this.witness.getId()+" does not exist in set "+this.setId);
                return;
            }
            this.setDao.deleteWitness(set, witness); 
        } else {
            LOG.info("Delete witness "+this.witness.getId());
            
            // get a list of all uses of this witness.
            // Mark sets as NOT collated, clear their collation cache and remove all alignments
            List<Usage> usage = this.witnessDao.getUsage(this.witness);
            for (Usage u : usage) {
                if ( u.getType().equals(Usage.Type.COMPARISON_SET)) {
               
                    // clear cached data
                    Long setId = u.getId();
                    this.cacheDao.deleteAll(setId);
                    
                    // set status to NOT collated
                    ComparisonSet set = this.setDao.find(setId);
                    set.setCollated(false);
                    this.setDao.update(set);
                    
                    // manually purge all alignments for this set
                    this.alignmentDao.clear(set);
                }
            }
            
            this.witnessDao.delete( witness ); 
        }
    }
}