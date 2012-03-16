package org.juxtasoftware.resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.juxtasoftware.dao.TemplateDao;
import org.juxtasoftware.model.Template;
import org.juxtasoftware.service.XmlTemplateParser;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Server resource for the RESTful parsing templates.
 * Handles GET and POST; 
 *   GET returns a json list of available templates
 *   POST creates a new template from json and returns its ID
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class TemplatesResource extends BaseResource {

    @Autowired private TemplateDao templateDao;    
    private Gson gson = new Gson();
    
    /**
     * Get a HTML representation of all available parsing templates
     * @return
     */
    @Get("html")
    public Representation toHtml() {
        List<Template> templates = this.templateDao.list( this.workspace );
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("templates", templates);
        map.put("page", "parse_template");
        map.put("title", "Juxta Parsing Templates");
        return toHtmlRepresentation("templates.ftl",map);
    }
    
    /**
     * Get a JSON representation of all available parsing templates
     * @return
     */
    @Get("json")
    public Representation toJson() {
        List<Template> templates = this.templateDao.list( this.workspace );
        Gson excludeGson = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .setExclusionStrategies( new DetailsExclusion() )
            .create();
        String out = excludeGson.toJson(templates);
        return toJsonRepresentation(out);
    }
    
    /**
     * Accept a json post of template data and use it to create a new template
     * @param jsonData
     * @return
     */
    @Post("json")
    public Representation acceptJson( final String jsonData ) {
        LOG.info("Create template with " +jsonData);
        Template template = this.gson.fromJson(jsonData, Template.class);
        template.setWorkspaceId( this.workspace.getId() );
        if ( this.templateDao.find(this.workspace, template.getName()) != null ) {
            setStatus(Status.CLIENT_ERROR_CONFLICT);
            return toTextRepresentation("Parse template \""+template.getName()+"\" already exists");
        }
        Long newID = this.templateDao.create(template);
        return toTextRepresentation( newID.toString() );
    }
    
    /**
     * Accept an XML post of a juxta desktop parse template 
     * @return
     */
    @Post("xml")
    public Representation acceptXmpParseTemplate( Representation entity ) {
        LOG.info("Create template with XML upload");
        if (entity == null) {
            getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return null;
        }
        
        try {
            XmlTemplateParser parser = new XmlTemplateParser();
            parser.parse( entity.getStream() );
            StringBuilder idList = new StringBuilder();
            for ( Template template : parser.getTemplates() ) {
                template.setWorkspaceId( this.workspace.getId() );
                Long newID = this.templateDao.create(template);
                if ( idList.length() > 0 ) {
                    idList.append(",");
                }
                idList.append( newID );
            }
            idList.insert(0, "[");
            idList.append("]");
              
            return toJsonRepresentation( idList.toString());
        } catch (Exception e) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST );
            return toTextRepresentation("Upload failed: "+e.getMessage());
        }
    }

    /**
     * An exclusion strategy to skip template details when returning a
     * list of all availble templates. In this case, the details are
     * all contained in a Set<>, so just skip Set.class.
     * @author loufoster
     *
     */
    private static class DetailsExclusion implements ExclusionStrategy {
        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            return false;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return (clazz == Set.class);
        }
    }
}
