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
     * Get a list of alignments matching the given constraints
     * @param constraints
     * @return
     */
    List<Alignment> list( final AlignmentConstraint constraints );
    
    /**
     * Get a count of alignments that match the constraints
     * @param constraints
     * @return
     */
    Long count( final AlignmentConstraint constraints );
    
    
    /**
     * Find a specific alignment in a comparison set
     * @param set
     * @param id
     * @return
     */
    Alignment find( final ComparisonSet set, final Long id);
    
    
}
