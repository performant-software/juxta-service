package org.juxtasoftware.resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.juxtasoftware.dao.RevisionDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.model.RevisionSet;
import org.juxtasoftware.model.Source;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;

/**
 * Resource for getting lists of source revision sets
 * and creating new ones
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class RevisionsResource extends BaseResource {
    @Autowired private RevisionDao revisionDao;
    @Autowired private SourceDao sourceDao;
    private Source source;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        
        // this request must contain a source id
        Long id = Long.parseLong( (String)getRequest().getAttributes().get("id"));
        this.source = this.sourceDao.find(this.workspace.getId(), id );   
        validateModel(this.source);
    }
    /**
     * Get Json representation of all available sources
     * @return
     */
    @Get("json")
    public Representation toJson() {        
        List<RevisionSet> sets = this.revisionDao.listRevisionSets(this.source.getId());
        Gson gson = new Gson();
        return toJsonRepresentation( gson.toJson(sets) );
    }
    
    /**
     * Get HTML representation of all available sources
     * @return
     */
    @Get("html")
    public Representation toHtml() {
        List<RevisionSet> sets = this.revisionDao.listRevisionSets(this.source.getId());
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("sets", sets);
        map.put("page", "source");
        map.put("title", "Revision Sets for Source \""+this.source.getFileName()+"\"");
        return toHtmlRepresentation("revision_sets.ftl", map);
    }
    
    /**
     * Create a new revision set from json data. Format example:
     *  {'name':'some','sourceId':88,'revisionIndexes':[0,1]}
     * 
     * @param jsonData
     * @return
     */
    @Post("json")
    public Representation create( final String jsonData ) {
        LOG.info("Create revision set from "+jsonData);
        Gson gson = new Gson();
        RevisionSet revSet = gson.fromJson(jsonData, RevisionSet.class);
        Long id = this.revisionDao.createRevisionSet(revSet);
        return toTextRepresentation( id.toString() );
    }
}
