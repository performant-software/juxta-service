package org.juxtasoftware.resource;

import java.util.List;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Resource used to GET a json representation of the 
 * usage for a particular resource (source, witness or set).
 * This json details all of the other resources that the resource
 * in question is derived from or used by
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class UsageResource extends BaseResource {
    private enum UsageType {SOURCE, WITNESS, SET};
    
    @Autowired private SourceDao sourceDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private ComparisonSetDao setDao;
    
    private UsageType usageType;
    private Long resourceId;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        
        try {
            this.usageType = UsageType.valueOf(getRequestAttributes().get("type").toString().toUpperCase());
        } catch ( Exception e ) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return;
        }
        
        this.resourceId = Long.parseLong((String) getRequestAttributes().get("id"));
    }
    
    @Get("json")
    public Representation getUsage() {
        if ( this.usageType.equals(UsageType.SOURCE)) {
            return getSourceUsage();
        } else if (this.usageType.equals(UsageType.WITNESS)) {
            return getWitnessUsage();
        } else if ( this.usageType.equals(UsageType.SET)) {
            return getSetUsage();
        }
        setStatus(Status.CLIENT_ERROR_NOT_FOUND);
        return null;
    }

    private Representation getSourceUsage() {
        Source src = this.sourceDao.find(this.workspace.getId(), this.resourceId);
        if ( src == null ) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return toTextRepresentation("source id "+this.resourceId+" does not exist in workspace "+this.workspace.getName());
        }
        List<Usage> usage =  this.sourceDao.getUsage(src);
        return toJsonRepresentation( toUsageJson(usage) );
    }

    private Representation getWitnessUsage() {
        Witness witness = this.witnessDao.find(this.resourceId);
        if ( witness == null || witness.isMemberOf(this.workspace) == false ) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return toTextRepresentation("witness id "+this.resourceId+" does not exist in workspace "+this.workspace.getName());
        }
        List<Usage> usage =  this.witnessDao.getUsage(witness);
        return toJsonRepresentation( toUsageJson(usage) );
    }

    private Representation getSetUsage() {
        ComparisonSet set = this.setDao.find(this.resourceId);
        if ( set == null || set.isMemberOf(this.workspace) == false ) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return toTextRepresentation("set id "+this.resourceId+" does not exist in workspace "+this.workspace.getName());
        }
        List<Usage> usage =  this.setDao.getUsage(set);
        return toJsonRepresentation( toUsageJson(usage) );
    }
    
    private String toUsageJson( List<Usage> usage ) {
        JsonArray jsonArray = new JsonArray();
        for ( Usage u :usage ) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", u.getType().toString());
            obj.addProperty("id", u.getId().toString());
            obj.addProperty("name", u.getName());
            jsonArray.add(obj);
        }
        Gson gson = new Gson();
        return gson.toJson(jsonArray);
    }

}
