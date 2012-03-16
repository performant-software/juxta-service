package org.juxtasoftware.resource;

import java.util.HashMap;
import java.util.Map;

import org.juxtasoftware.dao.TemplateDao;
import org.juxtasoftware.model.Template;
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

/**
 * Server resource for an individual parsing template
 * Handles GET and PUT and DELETE; 
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class TemplateResource extends BaseResource {
    
    @Autowired private TemplateDao templateDao;
    private Template template;

    /**
     * Extract the template ID from the request attributes. 
     */
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        
        Long id = Long.parseLong((String) getRequestAttributes().get("id"));
        this.template = this.templateDao.find(id);
        validateModel(this.template);
    }
    
    @Get("html")
    public Representation toHtml() {
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("template", this.template);
        map.put("page", "parse_template");
        map.put("title", "Juxta Parsing Template: "+template.getName());
        return toHtmlRepresentation("template.ftl",map);
    }
    
    /**
     * Get the template referenced by <code>templateId</code> and return its
     * json representation as the response
     * @return
     */
    @Get("json")
    public Representation toJson() {
        Gson gson = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();
        String out = gson.toJson(this.template);
        return toJsonRepresentation(out);
    }
    
    @Delete
    public void deleteTemplate() {
        LOG.info("Delete template "+this.template);
        this.templateDao.delete(this.template);
    }
    
    @Put("json")
    public void updateTemplate( String jsonData ) {
        LOG.info("Update template with "+jsonData);
        Gson gson = new Gson();
        Template updatedTemplate = gson.fromJson(jsonData, Template.class);
        
        // if name was changed, make sure it doesn't collide with other name
        if ( this.template.getName().equals(updatedTemplate.getName()) == false ) {
            if ( this.templateDao.exists( this.workspace, updatedTemplate.getName() )) {
                setStatus(Status.CLIENT_ERROR_CONFLICT, 
                    "Template '"+updatedTemplate.getName()+"' already exists in workspace "+
                    this.workspace.getName());
                return;
            }
        }
        this.templateDao.update(updatedTemplate);
    }
}
