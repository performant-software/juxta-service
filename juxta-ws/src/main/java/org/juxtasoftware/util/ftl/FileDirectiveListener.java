package org.juxtasoftware.util.ftl;

import java.io.File;

/**
 * Listner interface used to notify others when the file specified
 * in the FileDirective has been consumed.
 * 
 * @author loufoster
 *
 */
public interface FileDirectiveListener {
    void fileReadComplete( File file);
}
