package org.juxtasoftware.resource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.service.SetRemover;
import org.juxtasoftware.util.MetricsHelper;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Get/Delete/update a specific instances of a <code>ComparisonSet</code>
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ComparisonSetResource extends BaseResource {
    @Autowired private ComparisonSetDao comparionSetDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private Integer maxSetWitnesses;
    @Autowired private MetricsHelper metrics;
    @Autowired private SetRemover remover;
    
    private enum PostAction {INVALID, ADD_WITNESSES, DELETE_WITNESSES};
    private ComparisonSet set;
    private PostAction postAction = PostAction.INVALID;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        Long setId = getIdFromAttributes("id");
        if ( setId == null ) {
            return;
        }
        this.set = this.comparionSetDao.find(setId);
        if ( validateModel(this.set) == false ) {
            return;
        }
        
        String lastSeg  = getRequest().getResourceRef().getLastSegment().toUpperCase();
        if (  lastSeg.equals("ADD")) {
            this.postAction = PostAction.ADD_WITNESSES;
        } else if (lastSeg.equals("DELETE")) {
            this.postAction = PostAction.DELETE_WITNESSES;
        }
    }
    
    @Get("html")
    public Representation toHtml() {
        List<Witness> witnesses = this.comparionSetDao.getWitnesses(this.set);
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("set", set);
        map.put("witnesses", witnesses);
        map.put("page", "set");
        map.put("title", "Juxta Comparison Set: "+set.getName());
        return toHtmlRepresentation("comparison_set.ftl",map);
    }
    
    @Get("json")
    public Representation toJson() {
        List<Witness> witnesses = this.comparionSetDao.getWitnesses(this.set);
        Gson gson = new GsonBuilder()
            .setExclusionStrategies( new SourceExclusion() )
            .create();
        JsonObject setJson = gson.toJsonTree(set).getAsJsonObject();
        JsonElement jsonWitnesses = gson.toJsonTree(witnesses);
        setJson.add("witnesses", jsonWitnesses);
        
        return toJsonRepresentation(setJson.toString());
    }
    
    /**
    /* Accept json data to update the name and/or status of a comparison set.
    /* format: { "name": "newName", "status": "newStatus" }
     * where newStatus can be: NOT_COLLATED, COLLATING, COLLATED, ERROR
     */
    @Put("json")
    public Representation rename(final String jsonStr ) {
        JsonParser parser = new JsonParser();
        JsonObject jsonObj =  parser.parse(jsonStr).getAsJsonObject();
        String name = null;
        String status = null;
        if (jsonObj.has("name")) {
            name = jsonObj.get("name").getAsString();
        }
        if (jsonObj.has("status")) {
            status = jsonObj.get("status").getAsString();
        }
        
        if ( name == null && status == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Missing name and/or status in json payload");
        }
        
        ComparisonSet.Status setStatus = null;
        if ( status != null ) {
            setStatus = ComparisonSet.Status.valueOf(status.toUpperCase());
            if ( setStatus == null ) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Invalid status '"+status+"' specified in json payload");
            }
        }        
        
        // make sure name chages don't cause conflicts
        if ( name != null ) {
            if ( this.set.getName().equals(name) == false ) {
                if ( this.comparionSetDao.exists(this.workspace, name)) {
                    setStatus(Status.CLIENT_ERROR_CONFLICT);
                    return toTextRepresentation("Set "+name+" already exists in workspace "
                        +this.workspace.getName());
                }
            }
            this.set.setName( name );
        }
        
        if ( status != null ) {
            this.set.setStatus(setStatus);
        }
        
        // update the set 
        this.comparionSetDao.update( this.set );
        return toTextRepresentation( Long.toString(this.set.getId()) );
    }
    
    @Post("json")
    public Representation jsonPost( final String jsonData ) {
        if ( this.postAction.equals(PostAction.INVALID) ) {
            setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
            return toTextRepresentation("set POST is not allowed");
        }
        
        if ( jsonData == null || jsonData.length() == 0 ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Missing json payload in request");
        }
        
        if ( postAction.equals(PostAction.ADD_WITNESSES)) {
            return addWitnesses( jsonData);
        } else {
            return deleteWitnesses( jsonData );
        } 
    }

    private Representation deleteWitnesses(String jsonWitnesses) {
        LOG.info("Delete Witnesses "+jsonWitnesses+" from set "+this.set.getId());
        if ( this.set.getStatus().equals(ComparisonSet.Status.COLLATING)) {
            setStatus(Status.CLIENT_ERROR_CONFLICT);
            return toTextRepresentation("Cannot alter set; collation is in progress");
        }
        JsonParser parser = new JsonParser();
        JsonArray jsonArray = parser.parse(jsonWitnesses).getAsJsonArray();
        List<Witness> currWits = this.comparionSetDao.getWitnesses(this.set);
        for ( Iterator<JsonElement>  itr = jsonArray.iterator(); itr.hasNext(); ) {
            Long witId = itr.next().getAsLong();
            Witness delWitness = null;
            for ( Witness w : currWits ) {
                if ( w.getId().equals(witId)) {
                    delWitness = w;
                    break;
                }
            }
            
            if ( delWitness != null ) {
                this.comparionSetDao.deleteWitness(this.set, delWitness) ;
            } else {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Witness "+witId+" does not exist in set "+this.set.getId());
            }
        }
        Integer added = jsonArray.size();
        this.metrics.setWitnessCountChanged(this.workspace);
        return toTextRepresentation( added.toString() );
    }

    private Representation addWitnesses( final String jsonWitnesses ) {
        LOG.info("Add Witnesses "+jsonWitnesses+" to set "+this.set.getId());
        if ( this.set.getStatus().equals(ComparisonSet.Status.COLLATING)) {
            setStatus(Status.CLIENT_ERROR_CONFLICT);
            return toTextRepresentation("Cannot alter set; collation is in progress");
        }
        JsonParser parser = new JsonParser();
        JsonArray jsonArray = parser.parse(jsonWitnesses).getAsJsonArray();
        List<Witness> currWits = this.comparionSetDao.getWitnesses(this.set);
        
        int newWitnessTotal =  currWits.size() + jsonArray.size();
        if ( newWitnessTotal > this.maxSetWitnesses ) {
            setStatus(Status.CLIENT_ERROR_PRECONDITION_FAILED);
            return toTextRepresentation("Witnesses per set limit ("+this.maxSetWitnesses+") exceeded");
        }
        
        Set<Witness> addWits = new HashSet<Witness>();
        for ( Iterator<JsonElement>  itr = jsonArray.iterator(); itr.hasNext(); ) {
            Long newWitId = itr.next().getAsLong();
            for ( Witness w : currWits ) {
                if (w.getId().equals(newWitId) ) {
                    setStatus(Status.CLIENT_ERROR_CONFLICT);
                    return toTextRepresentation("Witness "+newWitId+" already exists in set "+this.set.getId());
                }
            }
            
            // at this point, the witness ID does not exist. get the witnesss
            Witness w = this.witnessDao.find(newWitId);
            if ( w == null || w.getWorkspaceId().equals(this.workspace.getId()) == false ){
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Witness "+newWitId+" does not exist");
            } 
            addWits.add(w);
        }
        
        this.comparionSetDao.addWitnesses(this.set, addWits);
        Integer added = addWits.size();
        
        this.metrics.setWitnessCountChanged(this.workspace);
        
        return toTextRepresentation( added.toString() );
    }
   
    @Delete
    public Representation deleteComparisonSet() {
        LOG.info("Delete set "+this.set.getId());
        try {
            this.remover.remove(this.workspace, this.set);
            return toTextRepresentation("ok");
        } catch (ResourceException e) {
            Status statusCode = e.getStatus();
            setStatus( statusCode);
            return toTextRepresentation( e.getStatus().getDescription() );
        }
    }

    private static class SourceExclusion implements ExclusionStrategy {
        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            return ( f.getName().equals("source") || 
                     f.getName().equals("text") || 
                     f.getName().equals("fragment"));
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
        
    }
}
