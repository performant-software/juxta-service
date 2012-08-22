package org.juxtasoftware.resource;

import java.util.HashMap;
import java.util.Map;

import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.util.AlignmentSerializer;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class AlignmentResource extends BaseResource {
    private Long alignmentId;
    private ComparisonSet set = null;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private ComparisonSetDao setDao;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        
        // make sure the set exists and is accessable
        Long setId = getIdFromAttributes("setId");
        if ( setId == null ) {
            return;
        }
        this.set = this.setDao.find( setId);
        if ( validateModel(this.set) == false ) {
            return;
        }
        
        // get/validate alignment ID
        this.alignmentId = getIdFromAttributes("id");
        if ( this.alignmentId == null ) {
            return;
        }
        
        // make sure the alignment is part of the set
        if ( this.setDao.hasAlignment(this.set, this.alignmentId) == false)  {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Alignment " +this.alignmentId+" does exist in set "+setId);
            return;
        }  
    }
    
    @Get("html")
    public Representation toHtml() {     
        Alignment align = this.alignmentDao.find(this.set, this.alignmentId);       
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("align", align);
        map.put("setId", this.set.getId());
        map.put("setName", this.set.getName());
        map.put("page", "set");
        map.put("title", "Juxta \""+set.getName()+"\" Difference");
        return toHtmlRepresentation("alignment.ftl", map);
    }
    
    @Get("json")
    public Representation toJson() {
        Alignment align = this.alignmentDao.find(this.set, this.alignmentId);
        Gson gson = new GsonBuilder()
            .registerTypeAdapter(Alignment.class, new AlignmentSerializer())
            .create();
        String json = gson.toJson(align);
        return toJsonRepresentation(json);
    }
    
    @Delete
    public void deleteAlignment() {
        LOG.info("Delete alignment " + this.alignmentId);
        this.alignmentDao.delete(this.alignmentId);
    }
}
