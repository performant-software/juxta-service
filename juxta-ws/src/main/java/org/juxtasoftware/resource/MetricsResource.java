package org.juxtasoftware.resource;

import org.juxtasoftware.dao.MetricsDao;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Read-only resourcce for accessing juxta workspace metrics
 * 
 * @author loufoster
 *
 */
public class MetricsResource extends BaseResource  {
    
    @Autowired MetricsDao metricsDao;
    
    @Get("json")
    public Representation jsonReport() {
        return toJsonRepresentation("");
    }

}
