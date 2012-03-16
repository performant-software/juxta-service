package org.juxtasoftware.dao;

import java.util.List;

import org.juxtasoftware.model.Template;
import org.juxtasoftware.model.Workspace;

/**
 * Data access object for parsing templates
 * 
 * @author loufoster
 *
 */
public interface TemplateDao extends JuxtaDao<Template> {
    /**
     * Update the template with new data
     * @param template
     */
    void update(Template template);
    
    /**
     * Find the default template for an xml document with the specified root.
     * The search is confined to the specified workspace
     * @param rootElement
     * @return
     */
    Template findDefault( final Workspace ws, final String rootElement );
    
    /**
     * Find a template by name in the specified workspace
     * @param templateName
     * @return
     */
    Template find( final Workspace ws, final String templateName );
    
    
    /**
     * Check if a template with the given name exists
     * in the specified wokspace
     */
    boolean exists( final Workspace ws, final String templateName );
    
    /**
     * List all paraing templates in the workspace.
     * @param ws
     * @return
     */
    List<Template> list( final Workspace ws);
}