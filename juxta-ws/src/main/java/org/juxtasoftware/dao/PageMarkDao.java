package org.juxtasoftware.dao;

import java.util.List;

import org.juxtasoftware.model.PageMark;

/**
 * Data access object for Page Breaks
 * 
 * @author loufoster
 *
 */
public interface PageMarkDao {

    /**
     * Create a set of page break tags for a witness
     * @param pbs
     */
    void create(List<PageMark> pbs);
    
    /**
     * Get the set of all page marks for <code>witnessId</code>
     * 
     * @param witnessId The identifier for a witness
     * @param type The type of marks to find (optional; if omitted, all marks on the page are returned)
     * @return Set of all PBs associated with witness <code>witnessId</code> 
     */
    List<PageMark> find( final Long witnessId);
    List<PageMark> find( final Long witnessId, PageMark.Type type);
    
    /**
     * Delete all marks for the witness identified by <code>witnessId</code>.
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
    
    /**
     * Determine if the witness identified by <code>witnessId</code> has
     * any line numbers on the page
     * 
     * @param witnessId
     * @return
     */
    boolean hasLineNumbers( final Long witnessId );
}
