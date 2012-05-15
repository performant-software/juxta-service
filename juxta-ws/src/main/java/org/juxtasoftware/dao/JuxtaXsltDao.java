package org.juxtasoftware.dao;

import java.util.List;

import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Workspace;

public interface JuxtaXsltDao extends JuxtaDao<JuxtaXslt> {
    
    /**
     * Update the XSLT with new data
     * @param template
     */
    void update(JuxtaXslt juxtaXslt);
    
    /**
     * Get the basic tag stripper xslt
     * @return
     */
    JuxtaXslt getTagStripper( );
    
    /**
     * List all XSLTs in the workspace.
     * @param ws
     * @return
     */
    List<JuxtaXslt> list( final Workspace ws);
}
