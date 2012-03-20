package org.juxtasoftware.dao;

import java.util.List;

import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.AlignmentConstraint;
import org.juxtasoftware.model.ComparisonSet;

/**
 * Data access object for annotaion links
 * 
 * @author loufoster
 *
 */
public interface AlignmentDao {
    /**
     * Create entries for all of the alignments in the list
     * @param alignments List of alignments to create
     * @return Number of alignments created
     */
    int create( final List<Alignment> alignments);
    
    /**
     * Delete an alignment
     * @param id
     */
    void delete( final Long id );
    
    /**
     * Remove all automatic alignment data from a comparison set. This will leave alignments
     * that were created manually (transpositions, direct posts to service)
     * @param set
     */
    void clear( final ComparisonSet set);
    
    /**
     * Remove all alignments created by juxta and all manual alignments if
     * force is set to true. 
     * @param set
     * @param force
     */
    void clear( final ComparisonSet set, boolean force);
    
    /**
     * Get a list of alignments matching the given constraints
     * @param constraints
     * @return
     */
    List<Alignment> list( final AlignmentConstraint constraints );
    
    
    /**
     * Find a specific alignment in a comparison set
     * @param set
     * @param id
     * @return
     */
    Alignment find( final ComparisonSet set, final Long id);
    
    
}
