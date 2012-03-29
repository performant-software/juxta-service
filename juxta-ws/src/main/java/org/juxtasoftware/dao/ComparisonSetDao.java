package org.juxtasoftware.dao;

import java.util.List;
import java.util.Set;

import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.ComparisonSet;
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
    
    // witness management
    Set<Witness> getWitnesses( final ComparisonSet set );
    void addWitnesses( final ComparisonSet set, final Set<Witness> witnesses);
    void deleteAllWitnesses( final ComparisonSet set );
    void deleteWitness( final ComparisonSet set, final Witness witness );
    boolean isWitness( final ComparisonSet set, final Witness witness );
    
    // alignment validation
    boolean hasAlignment( final ComparisonSet set, Long alignmentId );
    
    // config management
    CollatorConfig getCollatorConfig( final ComparisonSet set );
    void updateCollatorConfig( final ComparisonSet set, final CollatorConfig cfg );
}