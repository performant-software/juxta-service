package org.juxtasoftware.dao;

import java.io.Reader;

/**
 * DAO to access cached collation / visualization information
 * @author loufoster
 *
 */
public interface CacheDao {
    
    boolean heatmapExists(  final Long setId, final Long baseId, boolean condensed );
    Reader getHeatmap( final Long setId, final Long baseId, boolean condensed  );
    void cacheHeatmap( final Long setId, final Long baseId, Reader data, boolean condensed );
    void deleteHeatmap( final Long setId );
    
    boolean criticalApparatusExists(  final Long setId, final Long baseId  );
    Reader getCriticalApparatus( final Long setId, final Long baseId );
    void cacheCriticalApparatus( final Long setId, final Long baseId, Reader data);
    
    boolean exportExists(  final Long setId, final Long baseId  );
    Reader getExport( final Long setId, final Long baseId );
    void cacheExport( final Long setId, final Long baseId, Reader data);
    
    boolean histogramExists(  final Long setId, final Long baseId  );
    Reader getHistogram( final Long setId, final Long baseId );
    void cacheHistogram( final Long setId, final Long baseId, Reader data);
    
    boolean sideBySideExists(  final Long setId, final Long witness1, final Long witness2  );
    void cacheSideBySide( final Long setId, final Long witness1, final Long witness2, Reader data);
    void deleteSideBySide( final Long setId );
    Reader getSideBySide( final Long setId, final Long witness1, final Long witness2 );
    

    void deleteAll( final Long setId );
}
