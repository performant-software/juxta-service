package org.juxtasoftware.resource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.juxtasoftware.dao.QNameFilterDao;
import org.juxtasoftware.model.QNameFilter;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
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
public class QNameFilterResource extends BaseResource {
    @Autowired private QNameFilterDao qnameFilterDao;
    @Autowired private NameRepository nameRepository;
    private QNameFilter filter;
    private Gson gson;
    private JsonParser parser;
    
    @Override
    protected void doInit() throws ResourceException {

        super.doInit();
        
        Long id = getIdFromAttributes("id");
        if ( id == null ) {
            return;
        }
        this.filter = this.qnameFilterDao.find(id);
        if ( validateModel( this.filter ) == false ) {
            return;
        }
        this.parser = new JsonParser();
        this.gson = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();
        
        
    }
    
    @Get("html")
    public Representation toHtml() {
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("filter", this.filter);
        return toHtmlRepresentation("qname_filter.ftl",map,false);
    }
    
    @Get("json")
    public Representation toJson() {
        
        JsonObject jsonObj = gson.toJsonTree( this.filter).getAsJsonObject();
        JsonParser parser = new JsonParser();
  
        // note: gson is unable to map the list of QNames to json for us
        // so we do it manually. grr.
        StringBuilder zzz = new StringBuilder();
        zzz.append("[");
        for (Name qname :  this.filter.getQNames()) {
            zzz.append("{namespace: '").append(qname.getNamespace())
                .append("', localName: '").append(qname.getLocalName()).append("'}");
        }
        zzz.append("]");
        jsonObj.add("qnames", parser.parse(zzz.toString()));
        
        return toJsonRepresentation(jsonObj.toString()); 
    }
    
    @Put("json")
    public void updateFromJson( final String jsonStr ) {
        LOG.info("Update filter with "+jsonStr);
        QNameFilter newFilter = this.gson.fromJson(jsonStr, QNameFilter.class );
        JsonObject jsonObj = this.parser.parse(jsonStr).getAsJsonObject();
        JsonArray notes = jsonObj.get("qnames").getAsJsonArray();
        for ( Iterator<JsonElement> itr = notes.iterator(); itr.hasNext(); ) {
            JsonObject obj = itr.next().getAsJsonObject();
            SimpleName qname = new SimpleName(
                obj.get("namespace").getAsString(), 
                obj.get("localName").getAsString() );
            Name actualQname = this.nameRepository.get(qname);
            newFilter.getQNames().add(actualQname);
        }
        
        // was this a name change?
        if ( this.filter.getName().equals(newFilter.getName()) == false ) {
            // make sure it doesn't match an existing name
            if ( this.qnameFilterDao.find(this.workspace, newFilter.getName()) != null ) {
                setStatus(Status.CLIENT_ERROR_CONFLICT, "Filter \""+filter.getName()+"\" already exists");
                return;
            } 
        }
        this.qnameFilterDao.update(newFilter);
    }
    
    @Delete
    public void deleteFilter() {
        LOG.info("Delete filter "+this.filter.getId());
        this.qnameFilterDao.delete(this.filter);
    }
}
