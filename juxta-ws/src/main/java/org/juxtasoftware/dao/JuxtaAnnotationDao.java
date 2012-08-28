package org.juxtasoftware.dao;

import java.util.List;

import org.juxtasoftware.model.AnnotationConstraint;
import org.juxtasoftware.model.JuxtaAnnotation;

/**
 * @author loufoster
 *
 */
public interface JuxtaAnnotationDao  {
    /**
     * Create a entries for all annotations in the lts
     * @param annotations
     * @return Count of annotations created
     */
    int create(List<JuxtaAnnotation> annotations);
    
    /**
     * Create a single annotation
     * @param annotation
     * @return New annotation ID
     */
    Long create(JuxtaAnnotation annotation);
    
    /**
     * Delete a spaecific annotation
     * @param annotation
     */
    void delete( JuxtaAnnotation annotation );
    
    /**
     * Find a specific annotation by ID and optionally return its text
     * @param id
     * @param includeText Flag that indicates if text content shouldbe returned
     * @return
     */
    JuxtaAnnotation find( Long id, boolean includeText);
    
    /**
     * Get a list of all annotations that match the constraints
     * @param constraint
     * @return
     */
    List<JuxtaAnnotation> list( final AnnotationConstraint constraint );
    
    /**
     * Given a textID and a starting position, find the start of the NEXT token
     * @param witnessId
     * @param fromPos
     * @return
     */
    long findNextTokenStart( final Long witnessId, final long fromPos);
    
    long findPriorTokenEnd( final Long witnessId, final long fromPos);
    
}
