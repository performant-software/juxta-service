package org.juxtasoftware.resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.Witness;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Resourcce to GET a json list of all avaiable witnesses
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class WitnessesResource extends BaseResource {

    @Autowired private WitnessDao witnessDao;
       
    /**
     * Get a HTML representation of all available witnesses
     * @return
     */
    @Get("html")
    public Representation toHtml() {
        List<Witness> docs = this.witnessDao.list( this.workspace );
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("items", docs);
        map.put("page", "witness");
        map.put("title", "Juxta Witnesses");
        return toHtmlRepresentation("witness_list.ftl", map);
    }
    
    @Get("json")
    public Representation toJson() {       
        List<Witness> docs = this.witnessDao.list( this.workspace );
        Gson gson = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .setDateFormat("MM/dd/yyyy HH:mm:ss")
            .create();
        String out = gson.toJson(docs);
        return toJsonRepresentation(out);
    }
}
