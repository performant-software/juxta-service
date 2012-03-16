package org.juxtasoftware.dao;

import java.util.List;

import org.juxtasoftware.model.Revision;
import org.juxtasoftware.model.RevisionSet;
import org.juxtasoftware.model.Witness;

/**
 * Data access for revisions
 * 
 * @author loufoster
 *
 */
public interface RevisionDao {
   
    /**
     * Convert ordered list of revision sequence numbers, create
     * a set of accepted revsions in the db
     */
    Long createRevisionSet( final RevisionSet revSet );
    
    /**
     * Get a list of all named revisions that exist for the
     * source identified by <code>sourceId</code>
     * @return
     */
    List<RevisionSet> listRevisionSets( final Long sourceId);
    
    /**
     * Delete all of the revision sets associated with <code>sourceId</code>
     * @param sourceId
     */
    void deleteSourceRevisionSets(final Long sourceId);
    
    /**
     * Delete the revision set identified by <code>revisionSetId</code>
     * @param revisonSetId
     */
    void deleteRevisionSet(final Long revisonSetId);

    /**
     * Update an existing revision set with new date contained in <code>
     * @param rs
     */
    void updateRevisionSet( final RevisionSet rs);
    
    /**
     * REturns true if the source identified by <code>sourceId</code>
     * has any named revision sets defined.
     * 
     * @param sourceId
     * @return
     */
    boolean hasRevisionSets( final Long sourceId );
    
    /**
     * Get the specified revision set. A revsion set contains a 
     * zero-based list in order of revsion
     * occurance within a Source. For example; a source has
     * 5 revision sites. The first (in document order) and third
     * have been accepted. The seqence is [0,2].
     */
    RevisionSet getRevsionSet( final Long revisionSetId );
    
    /**
     * Get a list of add/addSpan/del/delSpan annotations that were
     * for witness <code>witnessId</code> in comparison set <code>setId</code>
     * 
     * @param witness
     * @return A list of juxta annotations
     */
    List<Revision> getRevisions(final Witness witness);
    
    /**
     * Detect if the specified witness has any revisions
     * @param witness
     * @return
     */
    boolean hasRevisions( final Witness witness );    
}
