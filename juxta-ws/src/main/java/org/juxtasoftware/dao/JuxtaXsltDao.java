package org.juxtasoftware.dao;

import java.io.Reader;
import java.util.List;

import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Workspace;

public interface JuxtaXsltDao extends JuxtaDao<JuxtaXslt> {
    
    /**
     * Update the XSLT with new data
     * @param template
     */
    void update(final Long xsltId, Reader xsltReader);
    
    /**
     * Get the usage list for this XSLT
     * @param xslt
     * @return
     */
    List<Usage> getUsage( final JuxtaXslt xslt );
    
    /**
     * List all XSLTs in the workspace.
     * @param ws
     * @return
     */
    List<JuxtaXslt> list( final Workspace ws);
}
