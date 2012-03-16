package org.juxtasoftware.service.importer;

import java.io.InputStream;

import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.util.BackgroundTaskStatus;

/**
 * Interface for all types of web service imports
 * @author loufoster
 *
 */
public interface ImportService {
    
    /**
     * Import data from the stream into a comparison set. <code>baseDoc</code> may be
     * null, or it may specify the base witness for this set
     * 
     * @param set
     * @param baseDoc
     * @param importStream
     * @param status
     * @throws Exception
     */
    void doImport(final ComparisonSet set, final InputStream importStream,
        BackgroundTaskStatus status) throws Exception;
}
