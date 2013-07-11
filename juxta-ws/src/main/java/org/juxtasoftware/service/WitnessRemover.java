package org.juxtasoftware.service;

import java.util.List;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Transactional
public class WitnessRemover {
    @Autowired private WitnessDao witnessDao;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private JuxtaXsltDao xsltDao;
   
    public List<Usage> remove( Witness witness ) throws ResourceException {
        // delete the witness  - this will schedule deletion of all
        // witness text, annotations and sets collation data that used it
        List<Usage> usage = this.witnessDao.getUsage( witness ); 
        for (Usage u : usage ) {
            if ( u.getType().equals(Usage.Type.COMPARISON_SET)) {
                ComparisonSet s = this.setDao.find(u.getId());
                if ( s.getStatus().equals(ComparisonSet.Status.COLLATING) ||
                     s.getStatus().equals(ComparisonSet.Status.TOKENIZING) ) {
                    throw new ResourceException(Status.CLIENT_ERROR_CONFLICT, 
                        "Cannot delete witness; related set '"+s.getName()+"' is collating.");
                }
            }
        }
        
        this.witnessDao.delete( witness );
        JuxtaXslt xslt = this.xsltDao.find( witness.getXsltId() );
        try {
            this.xsltDao.delete( xslt );
        } catch ( DataIntegrityViolationException e) {
            // This happens for TEI ps imports. One XSLT
            // has multiple witnesses. Only when the last witness
            // is deleted will this succeed
        }
        return usage;
    }
}
