package org.juxtasoftware.dao;

import java.util.List;

import org.juxtasoftware.model.PageBreak;

/**
 * Data access object for Page Breaks
 * 
 * @author loufoster
 *
 */
public interface PageBreakDao {

    /**
     * Create a set of page break tags for a witness
     * @param pbs
     */
    void create(List<PageBreak> pbs);
    
    /**
     * Get the set of all page breaks for <code>witnessId</code>
     * 
     * @param witnessId The identifier for a witness
     * @return Set of all PBs associated with witness <code>witnessId</code> 
     */
    List<PageBreak> find( final Long witnessId);
    
    /**
     * Delete all PBs for the witness identified by <code>witnessId</code>.
     * @param witness
     */
    void deleteAll( final Long witnessId );
    
    /**
     * Determine if the witness identified by <code>witnessId</code> has
     * any page breaks
     * 
     * @param witnessId
     * @return
     */
    boolean hasBreaks( final Long witnessId );
}
