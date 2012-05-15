package org.juxtasoftware.resource;

import java.util.List;

import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.model.JuxtaXslt;
import org.restlet.data.CharacterSet;
import org.restlet.data.Language;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class XsltResource extends BaseResource {
    @Autowired private JuxtaXsltDao xsltDao;
    private Long xsltId = null;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        if ( getRequest().getAttributes().containsKey("id")) {
            this.xsltId = Long.parseLong( (String)getRequest().getAttributes().get("id"));
        }
    }
    
    @Get("json")
    public Representation getJson() {
        if ( this.xsltId == null ) {
            List<JuxtaXslt> list = this.xsltDao.list(this.workspace);
            Gson gson = new Gson();
            return toJsonRepresentation( gson.toJson(list)); 
        } else {
            JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
            Gson gson = new Gson();
            return toJsonRepresentation( gson.toJson(xslt)); 
        }
    }
    
    @Get("html")
    public Representation getHtml() {
        if ( this.xsltId == null ) {
            List<JuxtaXslt> list = this.xsltDao.list(this.workspace);
            Gson gson = new Gson();
            return toJsonRepresentation( gson.toJson(list)); 
        } else {
            JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
            return new StringRepresentation("<h2>"+xslt.getName()+"</h2><textarea style=\"width:90%;height:90%\" value=\""+xslt.getXslt()+"\"/>", 
                MediaType.TEXT_HTML,
                Language.DEFAULT,
                CharacterSet.UTF_8); 
        }
    }
    
    @Get("xml")
    public Representation getXml() {
        if ( this.xsltId == null ) {
            setStatus(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE);
            return null;
        }
        JuxtaXslt xslt = this.xsltDao.find(this.xsltId);
        return toXmlRepresentation( xslt.getXslt() );
    }
    
    @Post("json")
    public Representation createXslt( final String jsonData ) {
        return toTextRepresentation( "newID" );
    }
    
    @Put("json")
    public Representation updateXslt( final String jsonData ) {
        if ( this.xsltId == null ) {
            setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
            return null;
        }
        return toTextRepresentation("id of changed XSLT");
    }
    
    @Delete
    public void deletXslt( ) {
        if ( this.xsltId == null ) {
            setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
            return;
        }
    }
}
