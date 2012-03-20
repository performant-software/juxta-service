package org.juxtasoftware.service.importer;

import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.util.BackgroundTaskStatus;

/**
 * Interface for all types of web service imports
 * @author loufoster
 *
 */
public interface ImportService<T> {
    
    /**
     * Import data from the generic source into a comparison set. <code>baseDoc</code> may be
     * null, or it may specify the base witness for this set
     * 
     * @param set
     * @param importSource
     * @param status
     * @throws Exception
     */
    void doImport(final ComparisonSet set, final T importSource, BackgroundTaskStatus status) throws Exception;
}
