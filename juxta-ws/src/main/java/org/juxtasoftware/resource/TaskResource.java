package org.juxtasoftware.resource;

import org.juxtasoftware.util.TaskManager;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;

public class TaskResource extends BaseResource {
    private enum Action { STATUS, CANCEL }
    private String taskId;
    private Action action;
    
    @Autowired private TaskManager taskManager;
    
    @Override
    protected void doInit() throws ResourceException {
        
        super.doInit();
        
        // get the task requested
        this.taskId = (String)getRequest().getAttributes().get("id");
        
        // validate the action requested
        String act  = getRequest().getResourceRef().getLastSegment().toUpperCase();
        if ( act.equals("CANCEL")) {
            this.action = Action.CANCEL;
        } else if ( act.equals("STATUS")) {
            this.action = Action.STATUS;
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid task action '"+act+"' specified");
        }
    } 
    
    @Get("json")
    public Representation getTaskStatus() {
        if ( this.action.equals(Action.STATUS) == false) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Invalid task request");
        }
        
        String json = this.taskManager.getStatus( this.taskId );
        return toJsonRepresentation(json); 
    }
    
    @Post("json")
    public Representation cancelTask( final String json ) {
        if ( this.action.equals(Action.CANCEL) == false) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Invalid task request");
        }
        
        this.taskManager.cancel( this.taskId );
        return toTextRepresentation(this.taskId+" canceled");
    }
}
