package org.juxtasoftware.resource;

import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * Resource to search documents in a workspace for occurrences of text
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class Searcher extends BaseResource {
    private String searchTerm;
    
    @Autowired SourceDao sourceDao;
    @Autowired WitnessDao witnessDao;

    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        
        this.searchTerm = getQueryValue("term");
        if ( this.searchTerm == null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing search term");
        }
    }
    
    @Get("json")
    public Representation search() {
        return toJsonRepresentation("[]");
    }
}
