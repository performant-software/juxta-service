package org.juxtasoftware.resource.sidebyside;

import java.util.List;

public interface OverlapInjector<T> {
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
     * @return True if start content was injected
     */
    boolean injectContentStart( StringBuilder line, final long currPositon );
    
    void restartContent( StringBuilder line );
    
    /**
     * If applicable, inject the tagging necessary to end any outstanding 
     * new content that was initiated by <code>injectContentStart</code>
     * 
     * @param line
     * @param currPosition
     * @return True end was enjected
     */
    boolean injectContentEnd( StringBuilder line, final long currPosition);
}
