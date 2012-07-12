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
    private Workspace workspace;
    @Autowired private WorkspaceDao workspaceDao;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();

        String wsName = (String)getRequest().getAttributes().get("name");
        this.workspace = this.workspaceDao.find(wsName);
        if ( this.workspace == null ) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Workspace "+wsName+" not found");
        }
    }
    
    @Get("json")
    public Representation toJson() {
        Gson gson = new Gson();
        JsonObject jsonObj = gson.toJsonTree(this.workspace).getAsJsonObject(); 
        return toJsonRepresentation(jsonObj.toString()); 
    }
    
    @Delete
    public Representation deleteWorkspace() {
        LOG.info("Delete "+this.workspace+" and all content");
        this.workspaceDao.delete(this.workspace);
        return null;
    }
}
