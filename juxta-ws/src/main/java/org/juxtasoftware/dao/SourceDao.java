package org.juxtasoftware.dao;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.juxtasoftware.model.ResourceInfo;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Workspace;

public interface SourceDao  {
    /**
     * Create a source in the specified workspace using the supplied name
     * and content reader
     * @param ws Workspace in which to add the source
     * @param name Name for the new source
     * @param type The source content type
     * @param contentReader Content reader
     * @return The ID of the newly created source
     * @throws XMLStreamException 
     * @throws IOException 
     */
    Long create(final Workspace ws, final String name, final Source.Type type, Reader contentReader) throws IOException, XMLStreamException;
    
    /**
     * Get brief info on this source: name and dates
     * @param sourceId
     * @return
     */
    ResourceInfo getInfo(final Long sourceId);
    
    /**
     * Update the source name
     * @param src
     * @param newName
     */
    void update(Source src, final String newName);
    
    /**
     * Update th name and content of the specified source
     * @param src
     * @param newName
     * @param contentReader
     * @throws XMLStreamException 
     * @throws IOException 
     */
    void update(Source src, final String newName, Reader contentReader) throws IOException, XMLStreamException;
    
    /**
     * Delete the specifed source
     * @param src
     */
    void delete(Source src);
    
    /**
     * Find a source by ids ID
     * @param id
     * @return
     */
    Source find( final Long workspaceId, Long id );
    Source find( final Long workspaceId, String name );
    
    /**
     * Given a source, find its root element
     * @param src
     * @return
     */
    String getRootElement( final Source src );
    
    /**
     * Get a reader for the content of the source
     * @param src
     * @return
     */
    Reader getContentReader( final Source src );
    
    /**
     * List all of the sources in a workspace
     * @param ws
     * @return
     */
    List<Source> list( final Workspace ws);
    
    /**
     * Check if a named source exists in the workspace
     * @param ws
     * @param name
     * @return
     */
    boolean exists( final Workspace ws, final String name);
    
    /**
     * Transform the non-unique <code>origName</code> into a name that 
     * is unique for workspace <code>ws</code>. Uniqne names are generated
     * by adding a '-#' extension to the end, where # is an sequentially increasing
     * number
     * @param ws
     * @param origName
     * @return
     */
    String makeUniqueName(final Workspace ws, final String origName);
    
    /**
     * Get a list of usage information for this source. The list
     * details all of the witnesses based upon this source, and
     * all of the sets using these witnesses
     * @param src
     * @return
     */
    List<Usage> getUsage( final Source src );
}
