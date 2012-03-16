package org.juxtasoftware.dao;


/**
 * Basic CRUD opertaions for all data access objects
 * @author loufoster
 *
 * @param <T>
 */
public interface JuxtaDao<T> {
    
    /**
     * Create a new object and return it ID
     * @param object
     * @return
     */
    long create(final T object);
    
    /**
     * Delete the object
     * @param obj
     */
    void delete(final T obj);
    
    /**
     * Find an object with the specified ID
     * @param id
     * @return
     */
    T find(final Long id);
}
