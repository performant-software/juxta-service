package org.juxtasoftware.resource;

import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.model.Workspace;
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

/**
 * Server resource for Workspace objects. Allows
 * get/delete
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class WorkspaceResource extends BaseResource {
    private Long workspaceId;
    @Autowired private WorkspaceDao workspaceDao;
    
    @Override
    protected void doInit() throws ResourceException {
        this.workspaceId = Long.parseLong( (String)getRequest().getAttributes().get("id"));
        super.doInit();
    }
    
    @Get("json")
    public Representation toJson() {
        Workspace ws = this.workspaceDao.find(this.workspaceId);
        if ( ws == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation( "Invalid Workspace ID: "+this.workspaceId);
        }
        
        Gson gson = new Gson();
        JsonObject jsonObj = gson.toJsonTree(ws).getAsJsonObject();
        
        return toJsonRepresentation(jsonObj.toString()); 
    }
    
    @Delete
    public Representation deleteWorkspace() {
        if ( this.workspaceId == 1) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation( "Cannot delete the public workspace"); 
        }
        Workspace ws = this.workspaceDao.find(this.workspaceId);
        if ( ws == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation( "Invalid workspace ID: "+this.workspaceId);
        } else {
            this.workspaceDao.delete(ws);
            return null;
        }
    }
}
