package org.juxtasoftware.dao;

import java.io.Reader;

/**
 * DAO to access collation cache
 * @author loufoster
 *
 */
public interface CacheDao {
    
    /**
     * Check if a heamap view exists
     * @param setId
     * @param baseId
     * @return
     */
    boolean heatmapExists(  final Long setId, final Long baseId  );
    
    /**
     * Get a reader for a heatmap view on the specified set/base witness pair
     * @param setId
     * @param baseId
     * @return
     */
    Reader getHeatmap( final Long setId, final Long baseId );
    
    /**
     * Cache a heatmap view with for the specified set/base witness pair
     * @param setId
     * @param baseId
     * @param data
     */
    void cacheHeatmap( final Long setId, final Long baseId, Reader data);
    
    /**
     * Remove ALL cached heatmap views for this set
     * @param setId
     */
    void deleteHeatmap( final Long setId );
    
    /**
     * Remove ALL cached side-by-side views for this set
     * @param setId
     */
    void deleteSideBySide( final Long setId );
    
    
    /**
     * Cache a side by side view with for the witness pair
     * @param setId
     * @param witness1
     * @param witness2
     * @param data
     */
    void cacheSideBySide( final Long setId, final Long witness1, final Long witness2, Reader data);
    
    /**
     * Check if a side-by-side view exists
     * @param setId
     * @param witness1
     * @param witness2
     * @return
     */
    boolean sideBySideExists(  final Long setId, final Long witness1, final Long witness2  );
    
    /**
     * Get a reader for a side-by-side view on the specified witness pair
     * @param setId
     * @param witness1
     * @param witness2
     * @return
     */
    Reader getSideBySide( final Long setId, final Long witness1, final Long witness2 );
}
