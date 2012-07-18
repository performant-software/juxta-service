package org.juxtasoftware.resource;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ResourceInfo;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.gson.Gson;

public class InfoResource extends BaseResource {
    private String type;
    private Long id;
    
    @Autowired private SourceDao sourceDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private ComparisonSetDao setDao;

    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        
        this.type = (String) getRequestAttributes().get("type");
        this.id = Long.parseLong((String) getRequestAttributes().get("id"));
    }
    
    @Get("json")
    public Representation getJsonInfo() {
        ResourceInfo info = null;
        if ( type.equals("source") ) {
            info = this.sourceDao.getInfo(this.workspace, this.id);
        } else if ( type.equals("witness") ) {
            info = this.witnessDao.getInfo(this.workspace, this.id);
        } else if ( type.equals("set") ) {
            info = this.setDao.getInfo(this.workspace, this.id);
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("IInvalid resource type requested");
        }
        
        if ( info == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Invaid resource identifer specified");
        }
        
        Gson gson = new  Gson();
        return toJsonRepresentation( gson.toJson(info)); 
    }
}
