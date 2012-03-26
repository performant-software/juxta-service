package org.juxtasoftware.dao;

import java.util.List;

import org.juxtasoftware.model.Note;

/**
 * Data access object for juxta Notes
 */
public interface NoteDao {
    
    /**
     * Create a set of notes
     * @param notes
     */
    void create(final List<Note> notes);

    /**
     * Find all of the notes associated with the witness 
     * identified by <code>witnessId</code>
     * 
     * @param witnessId
     * @return
     */
    List<Note> find( final Long witnessId);
    
    /**
     * Delete all notes for the witness identified by <code>witnessId</code>.
     * @param witness
     */
    void deleteAll( final Long witnessId );
    
    /**
     * Determine if the witness identified by <code>witnessId</code>
     * has any notes.
     * 
     * @param witnessId
     * @return
     */
    boolean hasNotes(final Long witnessId);
}
