package org.juxtasoftware.dao;

import java.io.Reader;

/**
 * DAO to access cached collation / visualization information
 * @author loufoster
 *
 */
public interface CacheDao {
    
    boolean heatmapExists(  final Long setId, final Long key, boolean condensed );
    Reader getHeatmap( final Long setId, final Long key, boolean condensed  );
    void cacheHeatmap( final Long setId, final Long key, Reader data, boolean condensed );
    void deleteHeatmap( final Long setId );
    
    boolean editionExists(  final Long setId, final long token  );
    Reader getEdition( final Long setId,  final long token  );
    void cacheEdition( final Long setId,  final long token, Reader data);
    
    boolean exportExists(  final Long setId, final Long baseId  );
    Reader getExport( final Long setId, final Long baseId );
    void cacheExport( final Long setId, final Long baseId, Reader data);
    
    boolean histogramExists(  final Long setId, final Long key  );
    Reader getHistogram( final Long setId, final Long key   );
    void cacheHistogram( final Long setId, final Long key, Reader data);
    
    boolean sideBySideExists(  final Long setId, final Long witness1, final Long witness2  );
    void cacheSideBySide( final Long setId, final Long witness1, final Long witness2, Reader data);
    void deleteSideBySide( final Long setId );
    Reader getSideBySide( final Long setId, final Long witness1, final Long witness2 );
    

    void deleteAll( final Long setId );
    
    void purgeExpired();
}
