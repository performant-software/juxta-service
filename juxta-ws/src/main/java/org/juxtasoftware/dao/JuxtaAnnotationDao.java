package org.juxtasoftware.dao;

import java.util.List;

import org.juxtasoftware.model.AnnotationConstraint;
import org.juxtasoftware.model.JuxtaAnnotation;

/**
 * Special case extension of the AnnotationRepo. It does not use
 * the standard juxta dao interface as a base
 * @author loufoster
 *
 */
public interface JuxtaAnnotationDao  {
    Long create(JuxtaAnnotation annotation);
    void delete( JuxtaAnnotation annotation );
    JuxtaAnnotation find( Long id, boolean includeText);
    List<JuxtaAnnotation> list( final AnnotationConstraint constraint );
    long findNextTokenStart( final long textId, final long fromPos);
    
}
