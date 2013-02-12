package org.juxtasoftware.dao;

import java.util.List;
import java.util.Set;

import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.ResourceInfo;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.model.Workspace;

/**
 * Data access object for Comparison Set Objects
 * @author loufoster
 *
 */
public interface ComparisonSetDao extends JuxtaDao<ComparisonSet> {
    void update(final ComparisonSet set);
    
    boolean exists( final Workspace ws, final String setName );
    ComparisonSet find( final Workspace ws, final String setName );
    List<ComparisonSet> list( final Workspace ws);
    ResourceInfo getInfo(final Long setId);
    
    // witness management
    List<Witness> getWitnesses( final ComparisonSet set );
    void addWitnesses( final ComparisonSet set, final Set<Witness> witnesses);
    void deleteAllWitnesses( final ComparisonSet set );
    void deleteWitness( final ComparisonSet set, final Witness witness );
    boolean isWitness( final ComparisonSet set, final Witness witness );
    void setTokenzedLength(final ComparisonSet set, final Witness witness, final long tokenizedLength );
    long getTokenzedLength(final ComparisonSet set, final Witness witness );
    
    // collation reset
    void clearCollationData( final ComparisonSet set);
    
    // alignment validation
    boolean hasAlignment( final ComparisonSet set, Long alignmentId );
    
    // config management
    CollatorConfig getCollatorConfig( final ComparisonSet set );
    void updateCollatorConfig( final ComparisonSet set, final CollatorConfig cfg );

    // get the list of items used to populate this comparions set
    List<Usage> getUsage(ComparisonSet set);
}
