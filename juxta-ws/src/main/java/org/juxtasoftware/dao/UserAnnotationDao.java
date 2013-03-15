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
    Long create(UserAnnotation ua);
    
    /**
     * Add a note for a specific witness to an existing user annotation
     * @param ua
     * @param witnessId
     * @param text
     */
    void addWitnessNote( UserAnnotation ua, Long witnessId, String text);
    
    /**
     * Find a specific user annotation
     */
    UserAnnotation find(ComparisonSet set, Long baseId, Range r);
    
    /**
     * List all of the user annotations on a base witness
     * @param set
     * @param baseId
     * @return
     */
    List<UserAnnotation> list(ComparisonSet set, Long baseId);
    
    void updateGroupNote( Long groupId, String newNote);
    void updateWitnessNote( Long noteId, String text);
    List<Long> getGroupWitnesses( Long groupId );
    
    void updateGroupId(UserAnnotation ua, Long groupId);
    Long findGroupId( ComparisonSet set, Long baseId, Range r);
    
    /**
     * Delete user annotations. All but set are optional.
     * If baseId is null, remove all user annotations for a set.
     * If range is null, remove all annotations on a base.
     * @param set
     * @param baseId
     * @param r
     */
    void delete(ComparisonSet set, Long baseId, Range r);
    void deleteGroupNote(ComparisonSet set, Long groupId);
    void deleteWitnessNote(Long noteId);
    
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
