package org.juxtasoftware.resource;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Witness;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;

public class ExistResource extends BaseResource {
    private String type;
    private String testName;
    @Autowired private SourceDao srcDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private ComparisonSetDao setDao;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        
        this.type = getRequestAttributes().get("type").toString().toLowerCase();
        if ( getQuery().getValuesMap().containsKey("name")  ) {
            this.testName = getQuery().getValuesMap().get("name");
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing required name parameter");
        }
    }
    
    @Get("json")
    public Representation toJson() {
        Boolean exists = false;
        Long id = null;
        if ( this.type.equals("source")) {
            Source s = this.srcDao.find(this.workspace.getId(), this.testName );
            if ( s != null ) {
                exists = true;
                id = s.getId();
            }
        } else if ( this.type.equals("witness")) {
            Witness w = this.witnessDao.find(this.workspace, this.testName );
            if ( w != null ) {
                exists = true;
                id = w.getId();
            }
        } else if ( this.type.equals("set")) {
            ComparisonSet cs = this.setDao.find(this.workspace, this.testName );
            if ( cs != null ) {
                exists = true;
                id = cs.getId();
            }
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Invalid resource type '"+this.type+"'");
        }
        
        if ( exists ) {
            return toJsonRepresentation( "{\"exists\": "+exists+", \"id\": "+id+"}" );
        }
        return toJsonRepresentation( "{\"exists\": "+exists+"}" );
    }
}
