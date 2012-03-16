package org.juxtasoftware.resource;

import java.util.HashMap;
import java.util.Map;

import org.juxtasoftware.util.ftl.FileDirective;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class RootResource extends BaseResource {
    @Autowired
    @Qualifier("version")
    private String version;
    
    @Get("html")
    public Representation toHtml() {
        
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("version", this.version);
        map.put("page", "root");
        map.put("srcFile", "public/home.html");
        map.put("fileReader", new FileDirective());   
        map.put("title", "Juxta Web Service");
        map.put("longTitle", "Juxta Web Service <span class=\"small\">(Version: "+this.version+")</span>");
        return toHtmlRepresentation("root.ftl", map);
    }
    
    @Get("json")
    public Representation jJson() {
        return toJsonRepresentation("{\"name\": \"Juxta WS\", \"version\": \""+version+"\"}");
    }
}
