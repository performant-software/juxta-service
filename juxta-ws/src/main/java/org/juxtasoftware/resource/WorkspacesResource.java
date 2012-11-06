package org.juxtasoftware.resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.model.Workspace;
import org.juxtasoftware.util.MetricsHelper;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Server resource for workspaces. Allows listing and creation.
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class WorkspacesResource extends BaseResource {
    @Autowired private WorkspaceDao workspaceDao;
    @Autowired private MetricsHelper metrics;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
    }
    
    @Get("json")
    public Representation toJson() {
        List<Workspace> list = this.workspaceDao.list();
        Gson gson = new Gson();
        return toJsonRepresentation( gson.toJson(list));     
    }
    
    @Get("html")
    public Representation toHtml() {
        List<Workspace> list = this.workspaceDao.list();
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("workspaces", list);
        map.put("page", "workspace");
        map.put("title", "Juxta Workspaces");
        return toHtmlRepresentation("workspaces.ftl", map);     
    }
    
    @Post("json")
    public Representation acceptJson(final String jsonStr) {
        JsonParser parser = new JsonParser();
        JsonObject jsonObj = null;
        try {
            jsonObj = parser.parse(jsonStr).getAsJsonObject();
        } catch (Exception e ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("malformed json payload");
        }
        Workspace ws = new Workspace();
        if ( jsonObj.has("name") ) {
            ws.setName( jsonObj.get("name").getAsString() );
            if ( ws.getName().length() > 255 ) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("workspace name too long");
            }
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("missing required json data");
        }
        JsonElement desc = jsonObj.get("description");
        if ( desc != null ) {
            ws.setDescription( desc.getAsString() );
        }
     
        if ( this.workspaceDao.find(ws.getName()) != null ) {
            setStatus(Status.CLIENT_ERROR_CONFLICT);
            return toTextRepresentation("Workspace \""+ws.getName()+"\" already exists");
        }
        
        Long id = this.workspaceDao.create(ws);
        ws.setId(id);
        this.metrics.workspaceAdded(ws);
        return toTextRepresentation(id.toString() );
    }
}
