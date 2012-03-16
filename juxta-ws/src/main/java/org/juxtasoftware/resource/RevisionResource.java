package org.juxtasoftware.resource;

import org.juxtasoftware.dao.RevisionDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.model.RevisionSet;
import org.juxtasoftware.model.Source;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;

/**
 * Resource for delete and update a specific revision set
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class RevisionResource extends BaseResource {
    @Autowired private RevisionDao revisionDao;
    @Autowired private SourceDao sourceDao;
    
    private Source source;
    private Long revisonSetId; 

    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        Long id = Long.parseLong( (String)getRequest().getAttributes().get("sourceId"));
        this.source = this.sourceDao.find(id);
        this.revisonSetId = Long.parseLong( (String)getRequest().getAttributes().get("id"));
        validateModel(this.source);
    }
    
    @Delete
    public Representation delete() {
        LOG.info("Delete revision set "+this.revisonSetId);
        RevisionSet rs = this.revisionDao.getRevsionSet(this.revisonSetId);
        if ( rs == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Invalid revsion set id: "+revisonSetId);
        }
        if ( rs.getSourceId().equals(this.source.getId() ) == false) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Revsion set id: "+revisonSetId+" is not valid for source id: "+this.source.getId());
        }
        this.revisionDao.deleteRevisionSet(this.revisonSetId);
        return null;
    }
    
    @Put("json")
    public Representation update( final String jsonData ) {
        LOG.info("Update revision set with "+jsonData);
        RevisionSet rs = this.revisionDao.getRevsionSet(this.revisonSetId);
        if ( rs == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Invalid revsion set id: "+revisonSetId);
        }
        if ( rs.getSourceId().equals(this.source.getId()) == false) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Revsion set id: "+revisonSetId+" is not valid for source id: "+this.source.getId() );
        }
        Gson gson = new Gson();
        rs = gson.fromJson(jsonData, RevisionSet.class);
        rs.setId(this.revisonSetId);
        this.revisionDao.updateRevisionSet(rs);
        return null;
    }
}
