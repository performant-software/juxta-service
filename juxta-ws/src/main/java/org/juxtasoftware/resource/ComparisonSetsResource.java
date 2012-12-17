package org.juxtasoftware.resource;

import java.util.Date;
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

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ComparisonSetsResource extends BaseResource {

    @Autowired private ComparisonSetDao comparionSetDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private MetricsHelper metrics;
    @Autowired private Integer maxSetWitnesses;
    @Autowired private SetRemover remover;
    
    boolean batchDelete;
    
    @Override
    protected void doInit() throws ResourceException { 
        super.doInit();
        String lastSeg  = getRequest().getResourceRef().getLastSegment().toLowerCase();
        this.batchDelete =  lastSeg.equals("delete");
    }
    
    @Get("html")
    public Representation toHtml() {
        List<ComparisonSet> sets = this.comparionSetDao.list( this.workspace );
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("items", sets);
        map.put("title", "Juxta Comparison Sets");
        map.put("page", "set");
        map.put("title", "Juxta Comparison Sets");
        return toHtmlRepresentation("set_list.ftl",map);
    }
    
    @Get("json")
    public Representation toJson() {
        List<ComparisonSet> sets = this.comparionSetDao.list( this.workspace );
        Gson gson = new GsonBuilder()
            .setExclusionStrategies(new SetsExclusion())
            .setDateFormat("MM/dd/yyyy HH:mm:ss")
            .create();
        String json = gson.toJson(sets);
        return toJsonRepresentation(json);
    }
    
    @Post("json")
    public Representation acceptJson( final String jsonData ) {
        if ( this.batchDelete) {
            return batchDelete(jsonData);
        }
        LOG.info("Create comparison set from "+jsonData);
        
        // parse out the main set object from string
        Gson gson = new Gson();
        ComparisonSet set = gson.fromJson(jsonData, ComparisonSet.class);
        set.setCreated( new Date() );
        set.setWorkspaceId( this.workspace.getId() );
        
        // flag duplicates in the same workspace as an error
        if ( this.comparionSetDao.exists(this.workspace, set.getName())) {
            setStatus(Status.CLIENT_ERROR_CONFLICT);
            return toTextRepresentation("Set "+set.getName()+" already exists in workspace "+
                this.workspace.getName());
        }
        
        // do the creation
        Long id = this.comparionSetDao.create(set);
        
        // If present, lookup each witnessID found and generate a list for this set
        JsonParser parser = new JsonParser();
        JsonObject jsonObj = parser.parse(jsonData).getAsJsonObject();
        if ( jsonObj.has("witnesses")) {
            Set<Witness> witnesses = new HashSet<Witness>();
            JsonArray ids = jsonObj.get("witnesses").getAsJsonArray();
            if ( ids.size() > this.maxSetWitnesses ) {
                setStatus(Status.CLIENT_ERROR_PRECONDITION_FAILED);
                return toTextRepresentation("Witnesses per set limit ("+this.maxSetWitnesses+") exceeded");
            }
            for ( Iterator<JsonElement> itr = ids.iterator(); itr.hasNext(); ) {
                Long witnessId = itr.next().getAsLong();
                Witness witness = this.witnessDao.find( witnessId );
                witnesses.add( witness );
            } 
            
            // add all witnesses to the set
            this.comparionSetDao.addWitnesses(set, witnesses);
        }
        
        this.metrics.setWitnessCountChanged(this.workspace);
        return toTextRepresentation(id.toString());
    }
    
    @Delete("json")
    public Representation batchDelete( final String jsonContent) {
        LOG.info("Batch delete sets "+jsonContent);
        JsonParser parser = new JsonParser();
        JsonArray jsonArray = parser.parse(jsonContent).getAsJsonArray();
        int delCnt = 0;
        for ( Iterator<JsonElement>  itr = jsonArray.iterator(); itr.hasNext(); ) {
            JsonElement ele = itr.next();
            Long id = ele.getAsLong();
            ComparisonSet set = this.comparionSetDao.find(id);
            if ( set != null ) {
                try {
                    this.remover.remove(this.workspace, set);
                    delCnt++;
                } catch ( ResourceException e ) {
                    LOG.warn(e.toString());
                }
            } else {
                LOG.warn("Set ID "+id+" is not valid for this workspace");
            }
        }
        return toTextRepresentation(""+delCnt);
    }
    
    private static class SetsExclusion implements ExclusionStrategy {
        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            return f.getName().equals("baseWitnessId");
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
        
    }
}
