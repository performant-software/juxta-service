package org.juxtasoftware.dao;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Workspace;

public interface SourceDao  {
    /**
     * Create a source in the specified workspace using the supplied name
     * and content reader
     * @param ws Workspace in which to add the source
     * @param name Name for the new source
     * @param contentReader Content reader
     * @return The ID of the newly created source
     * @throws XMLStreamException 
     * @throws IOException 
     */
    Long create(final Workspace ws, final String name, final Boolean isXml, Reader contentReader) throws IOException, XMLStreamException;
    
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
    Source find( final Long id );
    
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
}
