package org.juxtasoftware.dao;

import java.util.List;

import org.juxtasoftware.model.QNameFilter;
import org.juxtasoftware.model.Workspace;

public interface QNameFilterDao extends JuxtaDao<QNameFilter> {
    
    /**
     * Update filter details
     * @param filter
     */
    void update(QNameFilter filter);
    
    /**
     * Find a filter in a specific workspace
     * @param workspace
     * @param name
     * @return
     */
    QNameFilter find( final Workspace workspace, final String name);

    /**
     * Find a filter in the default, public workspace
     * @param name
     * @return
     */
    QNameFilter find( final String name);
    
    /**
     * List all filters in the workspace
     * @param ws
     * @return
     */
    List<QNameFilter> list( final Workspace ws);
}
