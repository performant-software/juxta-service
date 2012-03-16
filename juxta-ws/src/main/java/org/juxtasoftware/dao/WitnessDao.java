package org.juxtasoftware.dao;

import java.io.Reader;
import java.util.List;
import java.util.Set;

import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.model.Workspace;

/**
 * Witness DAO - lookup witness data by set or name. Access to content stream
 */
public interface WitnessDao extends JuxtaDao<Witness> {
    
    /**
     * Find all of the witnesses associated with the set
     * @param set
     * @return
     */
    Set<Witness> find(final ComparisonSet set);
    
    /**
     * Get a content reader for the witness
     * @param witness
     * @return
     */
    Reader getContentStream( final Witness witness );
    
    /**
     * Find a witness in a comparison set by name
     * @param set
     * @param title
     * @return
     */
    Witness find( final ComparisonSet set, final String title);
    
    /**
     * List all witnesses in a workspace
     * @param ws
     * @return
     */
    List<Witness> list( final Workspace ws);
    
    /**
     * Check if a witness exists in the workspace
     * 
     * @param ws
     * @param title
     * @return
     */
    boolean exists( final Workspace ws, final String title);
}
