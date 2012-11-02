package org.juxtasoftware.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.juxtasoftware.dao.MetricsDao;
import org.juxtasoftware.model.Metrics;
import org.juxtasoftware.util.MetricsHelper;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.gson.Gson;

/**
 * Read-only resourcce for accessing juxta workspace metrics
 * 
 * @author loufoster
 *
 */
public class MetricsResource extends BaseResource  {
    
    @Autowired private MetricsDao metricsDao;
    @Autowired private MetricsHelper metrics;
    
    @Get("html")
    public Representation htmlReport() {
        Map<String,Object> map = new HashMap<String,Object>();
        List<Metrics> metricsList;
        if ( this.workspace.getName().equalsIgnoreCase("public")) {
            metricsList =  this.metricsDao.list();
        } else {
            metricsList = new ArrayList<Metrics>();
            metricsList.add( this.metricsDao.get(this.workspace));
        }
        
        map.put("metrics",metricsList);
        map.put("title", "Juxta Metrics");
        return toHtmlRepresentation("metrics.ftl",map,false);
    }
    
    @Get("json")
    public Representation jsonReport() {
        Gson gson = new Gson();
        String out;
        if ( this.workspace.getName().equalsIgnoreCase("public")) {
            out = gson.toJson( this.metricsDao.list() );
        } else {
            Metrics m = this.metricsDao.get(this.workspace);
            out = gson.toJson(m);
        }
        return toJsonRepresentation(out);
    }
    
    @Get("txt")
    public Representation csvReport() {
        if ( this.workspace.getName().equalsIgnoreCase("public") == false) {
            Metrics m = this.metricsDao.get(this.workspace);
            return toTextRepresentation( this.metrics.toCsv(m).toString() );  
        } 
        StringBuilder out = new StringBuilder();
        for ( Metrics m : this.metricsDao.list() ) {
            if ( out.length() > 0 ) {
                out.append("\n");
            }
            out.append( this.metrics.toCsv(m) );
        }
        return toTextRepresentation(out.toString());
    }
    
   

}
