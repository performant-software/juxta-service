package org.juxtasoftware.resource;

import java.io.Reader;
import java.util.Map;

import org.juxtasoftware.Constants;
import org.juxtasoftware.JuxtaWS;
import org.juxtasoftware.JuxtaWsApplication;
import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.model.Workspace;
import org.juxtasoftware.model.WorkspaceMember;
import org.restlet.Request;
import org.restlet.data.CharacterSet;
import org.restlet.data.Encoding;
import org.restlet.data.Language;
import org.restlet.data.MediaType;
import org.restlet.data.Preference;
import org.restlet.data.Status;
import org.restlet.engine.application.EncodeRepresentation;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.representation.ReaderRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import freemarker.template.Configuration;

/**
 * Base class for all JuxtaWS resources. It defines the DB transaction 
 * boundary to be one request. To do som it must override the default
 * handle method and annotate it as transactional. It is the only external
 * call that is mave via the cglib proxy during the request so here is where
 * the transaction must be made. Also must 
 * override the default doCatch behavior and rethrow the exception in order
 * for the transaction to be rolled back.
 * 
 * @author loufoster
 *
 */
public class BaseResource extends ServerResource {

    private static final String FTL_ROOT = "clap://class/templates/ftl/";
    private static final Configuration FTL_CONFIG; 
    protected static final Logger LOG = LoggerFactory.getLogger( Constants.WS_LOGGER_NAME );
    
    private boolean zipSupported = false;
    protected boolean embedded = false;
    protected Workspace workspace;
    @Autowired protected WorkspaceDao workspaceDao;
    
    static {
        FTL_CONFIG = new Configuration();
        FTL_CONFIG.setClassForTemplateLoading(JuxtaWS.class, "/templates/ftl");
        FTL_CONFIG.setNumberFormat("computer");
    }
    
    @Override
    protected void doInit() throws ResourceException {   
        
        if (getQuery().getValuesMap().containsKey("embed") ) {
            this.embedded = true;
        }
        
        if ( getRequestAttributes().containsKey("workspace") ) {
            String name = (String) getRequestAttributes().get("workspace");
            this.workspace = this.workspaceDao.find( name );
            if ( this.workspace == null ) {
                setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Workspace not found");
                return;
            }
        } else {
            this.workspace = this.workspaceDao.getPublic();
        }
        
        JuxtaWsApplication app = (JuxtaWsApplication) getApplication();
        if ( app.authenticate(getRequest(), getResponse()) == false ) {
            setStatus(Status.CLIENT_ERROR_UNAUTHORIZED);
            return;
        }
        
        // See if this client can handle zipped responses
        Request r = getRequest();
        for ( Preference<Encoding> enc : r.getClientInfo().getAcceptedEncodings() ) {
            if ( enc.getMetadata().equals( Encoding.GZIP ) ) {
                this.zipSupported = true;
                break;
            }
        }
        
        super.doInit();
    }
    
    protected Long getIdFromAttributes( final String name ) {
        Long val = null;
        if ( getRequestAttributes().containsKey(name) ) {
            String strVal = (String)getRequestAttributes().get(name);
            try {
                val = Long.parseLong(strVal);
            } catch (NumberFormatException e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid identifier specified");
            }
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing required "+name+" parameter");
        }
        return val;
    }
    
    protected boolean isZipSupported() {
        return this.zipSupported;
    }
    
    /**
     * Ensure that any model data used by this resource exists
     * and is accessible within the specified workspace
     * 
     * @param model
     */
    protected boolean validateModel( final WorkspaceMember model ) {
        if ( model == null ) {
            LOG.error("Resource is null");
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, 
                "Invalid resource identifier specified");
            return false;
        } else if ( model.isMemberOf( this.workspace) == false ) {
            LOG.error("Resource "+model.getId()+" is not a member of workspace "+this.workspace.getName());
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, 
                "Resource "+model.getId()+" does not exist in workspace " +
                this.workspace.getName());
            return false;
        }
        return true;
    }
    
    /**
     * Override and append transactional annotation. This defines DB
     * trasaction boundary.
     */
    @Override
    @Transactional
    public Representation handle() {
        return super.handle();
    }
    
    /**
     * Must override here re-throw exceptions. This allows transactions
     * to be rolled back on errors
     */
    @Override
    protected void doCatch(Throwable throwable) {
        super.doCatch(throwable);
        LOG.error("Request Failed", throwable);
        throw new RuntimeException(throwable);
    }
    
    /**
     * Get the trimmed and lowercased workspace name to be used in URLs
     * @return
     */
    public String getWorkspace() {
        return this.workspace.getName().toLowerCase().trim();
    }
    
    /**
     * Convert the data in msg to a plain text, UTF-8 representation
     * @param msg string message to convert
     * @return
     */
    public Representation toTextRepresentation( final String msg ) {
        return new StringRepresentation(msg, 
                MediaType.TEXT_PLAIN,
                Language.DEFAULT,
                CharacterSet.UTF_8);
    }
    
    /**
     * Convert the Reader into an HTML representation, zipping if possible
     * @param reader Reader containing the html data
     * @return
     */
    public Representation toHtmlRepresentation( final Reader reader) {
        Representation r = new ReaderRepresentation(reader, MediaType.TEXT_HTML);
        if ( this.zipSupported ) {
            return new EncodeRepresentation(Encoding.GZIP, r);
        }
        return r;   
    }
    
    /**
     * Convert the Reader into an XML representation, zipping if possible
     * @param reader Reader containing the html data
     * @return
     */
    public Representation toXmlRepresentation( final String content ) {
        Representation r = new StringRepresentation(content, 
            MediaType.TEXT_PLAIN,
            Language.DEFAULT,
            CharacterSet.UTF_8);
        if ( this.zipSupported ) {
            return new EncodeRepresentation(Encoding.GZIP, r);
        }
        return r;   
    }
    
    /**
     * Convert the JSON data in jsonString to aa application/json, 
     * UTF-8 representation
     * @param jsonString JSON data in string format
     * @return
     */
    public Representation toJsonRepresentation( final String jsonString ) {
        Representation r = new StringRepresentation(jsonString, 
            MediaType.APPLICATION_JSON,
            Language.DEFAULT,   
            CharacterSet.UTF_8);
        if ( this.zipSupported ) {
            return new EncodeRepresentation(Encoding.GZIP, r);
        }
        return r;   
    }
    
    /**
     * Using the freemarker template <code>ftlName</code> and the supporting data 
     * found in <code>map</code>, generate a UTF-8 encoded HTML represenation.
     * using a standard juxta layout as the base template.
     * 
     * @param ftlName name of the content template
     * @param map map of name-value pairs that will be used to fill in the template
     * @return
     */
    public Representation toHtmlRepresentation( final String ftlName, Map<String,Object> map) {
        return toHtmlRepresentation(ftlName, map, true);
    }
    
    /**
     * Using the freemarker template <code>ftlName</code> and the supporting data 
     * found in <code>map</code>, generate a UTF-8 encoded HTML represenation.
     * If the <code>useLayout</code> flag is true, this representation will be 
     * embedded as content within the base juxta layout. If false, it will 
     * stand on its own.
     * 
     * @param ftlName name of the content template
     * @param map map of name-value pairs that will be used to fill in the template
     * @param useLayout Flag to indicate if this template will be embeddded within
     *        the standard juxta layout.
     * @return
     */
    public Representation toHtmlRepresentation( final String ftlName, Map<String,Object> map, boolean useLayout ) {
        return toHtmlRepresentation(ftlName, map, useLayout, true);
    }
    public Representation toHtmlRepresentation( final String ftlName, Map<String,Object> map, boolean useLayout, boolean gzip ) {
        Representation ftlRepresentation = null;
        if ( useLayout == false ) {
            ftlRepresentation = getTemplate(ftlName);
        } else { 
            ftlRepresentation = getTemplate("layout.ftl");
            map.put("content", "/"+ftlName);
            map.put("embedded", this.embedded);
            
            if ( map.containsKey("workspace") == false) {
                map.put("workspace", this.workspace.getName().toLowerCase().trim());
                map.put("workspaceId", this.workspace.getId());
                map.put("workspaceCount", this.workspaceDao.getWorkspaceCount());
                map.put("workspaces", this.workspaceDao.list());
            }
        }
        
        map.put("baseUrl", getRequest().getHostRef().toString()+"/juxta"); 
        Representation r = new TemplateRepresentation(ftlRepresentation, FTL_CONFIG, map, MediaType.TEXT_HTML);
        if ( this.zipSupported && gzip ) {
            return new EncodeRepresentation(Encoding.GZIP, r);
        }
        return r;  
    }
    
    private final Representation getTemplate( String ftlName ) {
        return new ClientResource(FTL_ROOT+ftlName).get();
    }
}
