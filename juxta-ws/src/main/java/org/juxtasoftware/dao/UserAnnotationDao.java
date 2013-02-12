package org.juxtasoftware.dao;

import java.util.List;

import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.UserAnnotation;

import eu.interedition.text.Range;

public interface UserAnnotationDao {

    /**
     * Create a new user annotation
     * @param ua
     */
    void create(UserAnnotation ua);
    
    /**
     * List all of the user annotations on a base witness
     * @param set
     * @param baseId
     * @return
     */
    List<UserAnnotation> list(ComparisonSet set, Long baseId, Range r);
    
    /**
     * Update an existing user annotation with new data
     * @param ua
     */
    void update(UserAnnotation ua);
    
    /**
     * Delete user annotations. All but set are optional.
     * If baseId is null, remove all user annotations for a set.
     * If range is null, remove all annotations on a base.
     * @param set
     * @param baseId
     * @param r
     */
    void delete(ComparisonSet set, Long baseId, Range r);
    
    /**
     * Check if any annotations exist for this set/base.
     * @param set
     * @param baseId
     * @return
     */
    boolean hasUserAnnotations(ComparisonSet set, Long baseId);
    
    /**
     * Check if any annotations exist for this set/base.
     * @param set
     * @param baseId
     * @return
     */
    int count(ComparisonSet set, Long baseId);

}
