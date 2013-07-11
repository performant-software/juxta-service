package org.juxtasoftware.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.model.Workspace;
import org.juxtasoftware.util.MetricsHelper;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Transactional
public class SourceRemover {
    @Autowired private SourceDao sourceDao;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private JuxtaXsltDao xsltDao;
    @Autowired private MetricsHelper metrics;
    
    public List<Usage> removeSource( Workspace workspace, Source source ) throws ResourceException {
        // Get a list of all uses of this source
        List<Usage> usage = this.sourceDao.getUsage( source );
        
        // first, be sure this is not part of a collating set
        for (Usage u : usage) {
            if ( u.getType().equals(Usage.Type.COMPARISON_SET)) {
                ComparisonSet s = this.setDao.find(u.getId());
                if ( s.getStatus().equals(ComparisonSet.Status.COLLATING)||
                     s.getStatus().equals(ComparisonSet.Status.TOKENIZING) ) {
                    throw new ResourceException(Status.CLIENT_ERROR_CONFLICT, 
                        "Cannot delete source; related set '"+s.getName()+"' is collating.");
                }
            }
        }
        Set<JuxtaXslt> xslts = new HashSet<JuxtaXslt>();
        for (Usage u : usage) {
            // Manually delete the witness. This is necessary
            // cuz witness content is stored in the text_content area
            // and will not cascade delete itself. 
            if ( u.getType().equals(Usage.Type.WITNESS)) {
                Witness w = this.witnessDao.find(u.getId());
                
                // NOTE: This will delete the witness immediately and mark
                // all comparison sets that use it as NOT_COLLATED. It will
                // kick off a worker thread to do two things: 
                //    1 - clear all collation data for related sets
                //    2 - wipe out the text_content for the witness
                this.witnessDao.delete(w);
                
                // save the XSLTs to delete later. This is necessary in the
                // case of witnesses generated from TEI PS source: each witness
                // will refer to the SAME xslt.
                JuxtaXslt xslt = this.xsltDao.find( w.getXsltId() );
                if ( xslt != null ) {
                    xslts.add(xslt);
                }
            } 
        }
        
        // Once everything else is gone, clear out the XSLT
        for ( JuxtaXslt xslt : xslts ) {
            this.xsltDao.delete(xslt);
        }

        // LASTLY, delete the source itself
        this.sourceDao.delete(source);        
        this.metrics.sourceRemoved(workspace, source);
        return usage;
    }
}
