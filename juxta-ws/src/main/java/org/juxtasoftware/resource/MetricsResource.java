package org.juxtasoftware.resource;

import java.util.List;

import org.juxtasoftware.dao.MetricsDao;
import org.juxtasoftware.model.Metrics;
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
    
    @Get("json")
    public Representation jsonReport() {
        Gson gson = new Gson();
        String out;
        if ( this.workspace.getName().equalsIgnoreCase("public")) {
            List<Metrics> metrics = this.metricsDao.list();
            out = gson.toJson(metrics);
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
            return toTextRepresentation( toCsv(m).toString() ); 
            
        } 
        StringBuilder out = new StringBuilder();
        for ( Metrics m : this.metricsDao.list() ) {
            if ( out.length() > 0 ) {
                out.append("\n");
            }
            out.append( toCsv(m) );
        }
        return toTextRepresentation(out.toString());
    }
    
    private StringBuilder toCsv( Metrics m ) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getWorkspace()).append(",");
        sb.append(m.getNumSources()).append(",");
        sb.append(m.getMinSourceSize()).append(",");
        sb.append(m.getMaxSourceSize()).append(",");
        sb.append(m.getMeanSourceSize()).append(",");
        sb.append(m.getTotalSourcesSize()).append(",");
        sb.append(m.getTotalSecsCollating()).append(",");
        sb.append(m.getNumCollationsStarted()).append(",");
        sb.append(m.getNumCollationsFinished());
        return sb;
    }

}
