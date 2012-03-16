package org.juxtasoftware.resource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Witness;
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

    private ComparisonSet set;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        Long id = Long.parseLong( (String)getRequest().getAttributes().get("id"));
        this.set = this.comparionSetDao.find(id);
        validateModel(this.set);
    }
    
    @Get("html")
    public Representation toHtml() {
        Set<Witness> witnesses = this.comparionSetDao.getWitnesses(this.set);
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("set", set);
        map.put("witnesses", witnesses);
        map.put("page", "set");
        map.put("title", "Juxta Comparison Set: "+set.getName());
        return toHtmlRepresentation("comparison_set.ftl",map);
    }
    
    @Get("json")
    public Representation toJson() {
        Set<Witness> witnesses = this.comparionSetDao.getWitnesses(this.set);
        Gson gson = new GsonBuilder()
            .setExclusionStrategies( new SourceExclusion() )
            .create();
        JsonObject setJson = gson.toJsonTree(set).getAsJsonObject();
        JsonElement jsonWitnesses = gson.toJsonTree(witnesses);
        setJson.add("witnesses", jsonWitnesses);
        
        return toJsonRepresentation(setJson.toString());
    }
    
    @Put("json")
    public Representation updateComparisonSet(final String jsonSet ) {
        LOG.info("Update set with "+jsonSet);
        Gson gson = new Gson();
        ComparisonSet updateSet = gson.fromJson(jsonSet, ComparisonSet.class);
        
        // make sure name chages don't cause conflicts
        if ( this.set.getName().equals(updateSet.getName()) == false ) {
            if ( this.comparionSetDao.exists(this.workspace, updateSet.getName())) {
                setStatus(Status.CLIENT_ERROR_CONFLICT);
                return toTextRepresentation("Set "+updateSet.getName()+" already exists in workspace "
                    +this.workspace.getName());
            }
        }
        
        // update the set 
        this.comparionSetDao.update( updateSet );
        
        // update witnesses in the set
        JsonParser parser = new JsonParser();
        JsonObject jsonObj = parser.parse(jsonSet).getAsJsonObject();      
        Set<Witness> witnesses = new HashSet<Witness>();
        JsonArray jsonWitnesses = jsonObj.get("witnesses").getAsJsonArray();
        for ( Iterator<JsonElement> itr = jsonWitnesses.iterator(); itr.hasNext(); ) {
            JsonElement witnessEle = itr.next();
            Long witnessId;
            if  ( witnessEle.isJsonObject() ) {
                witnessId = ((JsonObject)witnessEle).get("id").getAsLong();
            } else {
                witnessId = witnessEle.getAsLong();
            }
            Witness witness = this.witnessDao.find( witnessId );
            witnesses.add( witness );
        }   
        
        this.comparionSetDao.deleteAllWitnesses(updateSet);
        this.comparionSetDao.addWitnesses(updateSet, witnesses);
        return toTextRepresentation( Long.toString(updateSet.getId()) );
    }
    
    @Delete
    public void deleteComparisonSet() {
        LOG.info("Delete set "+this.set.getId());
        this.comparionSetDao.delete(set);
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
