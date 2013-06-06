package org.juxtasoftware.resource;

import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;

public class ExistResource extends BaseResource {
    private String type;
    private String testName;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        
        this.type = (String) getRequestAttributes().get("type");
        if ( getQuery().getValuesMap().containsKey("name")  ) {
            this.testName = getQuery().getValuesMap().get("name");
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing required name parameter");
        }
    }
    
    @Get("json")
    public Representation toJson() {
        // TODO
        return toJsonRepresentation(this.type+" name: "+this.testName);
    }
}
