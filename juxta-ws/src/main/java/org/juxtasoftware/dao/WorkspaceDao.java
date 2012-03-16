package org.juxtasoftware.dao;

import java.util.List;

import org.juxtasoftware.model.Workspace;

/**
 * Data access for workspace objects
 * @author loufoster
 *
 */
public interface WorkspaceDao extends JuxtaDao<Workspace> {
    /**
     * Get the default public workspace
     * @return
     */
    Workspace getPublic();
    
    /**
     * find a specific workspace by name
     * @param name
     * @return
     */
    Workspace find(final String name);
    
    /**
     * List all available workspaces
     * @return
     */
    List<Workspace> list();
    
    /**
     * Get the total number of workspaces
     * @return
     */
    int getWorkspaceCount();
}
