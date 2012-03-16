package org.juxtasoftware.resource.heatmap;

import java.util.List;

/**
 * Interface for classes that listen to the heatmap text stream
 * and inject content into it
 * 
 * @author loufoster
 *
 */
public interface StreamInjector<T> {
    
    /**
     * Initialize the injector. 
     */
    void initialize(List<T> data);
    
    /**
     * Get the data source that this injector uses
     * @return
     */
    List<T> getData();
    
    boolean hasContent( final long pos);
            
    /**
     * If applicable, inject the start of new content and tagging
     * into the current line of heatmap text.
     * 
     * @param line The current version of a single line of heatmap text
     * @param currPositon The current character offset in the base witness
     */
    void injectContentStart( StringBuilder line, final long currPositon );
    
    /**
     * If applicable, inject the tagging necessary to end any outstanding 
     * new content that was initiated by <code>injectContentStart</code>
     * 
     * @param line
     * @param currPosition
     */
    void injectContentEnd( StringBuilder line, final long currPosition);
}
