package org.juxtasoftware.resource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.juxtasoftware.dao.QNameFilterDao;
import org.juxtasoftware.model.QNameFilter;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.interedition.text.Name;
import eu.interedition.text.NameRepository;
import eu.interedition.text.mem.SimpleName;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class QNameFiltersResource extends BaseResource {
    @Autowired private QNameFilterDao qnameFilterDao;
    @Autowired private NameRepository nameRepository;
       
    @Get("html")
    public Representation toHtml() {
        List<QNameFilter> filters = this.qnameFilterDao.list( this.workspace );
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("items", filters);
        map.put("title", "Juxta QName Filters");
        return toHtmlRepresentation("qname_filter_list.ftl",map,false);
    }
    
    @Get("json")
    public Representation toJson() {       
        List<QNameFilter> filters = this.qnameFilterDao.list( this.workspace );
        Gson gson = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();
        String json  = gson.toJson(filters);
        return toJsonRepresentation(json); 
    }
    
    @Post("json")
    public Representation acceptJson(final String jsonStr) {
        LOG.info("Create QName Filter from "+jsonStr);
        JsonParser parser = new JsonParser();
        JsonObject jsonObj = parser.parse(jsonStr).getAsJsonObject();
        QNameFilter filter = new QNameFilter();
        filter.setName( jsonObj.get("name").getAsString() );
        
        QNameFilter bogus =this.qnameFilterDao.find(this.workspace, filter.getName());
        if ( bogus != null ) {
            setStatus(Status.CLIENT_ERROR_CONFLICT);
            return toTextRepresentation("Filter \""+filter.getName()+"\" already exists");
        }
        
        JsonArray notes = jsonObj.get("qnames").getAsJsonArray();
        for ( Iterator<JsonElement> itr = notes.iterator(); itr.hasNext(); ) {
            JsonObject obj = itr.next().getAsJsonObject();
            SimpleName qname = new SimpleName(
                obj.get("namespace").getAsString(), 
                obj.get("localName").getAsString() );
            Name actualQname = this.nameRepository.get(qname);
            filter.getQNames().add(actualQname);
        }
        filter.setWorkspaceId( this.workspace.getId() );
        Long id = this.qnameFilterDao.create(filter);
        return toTextRepresentation(id.toString() ); 
    }
}
