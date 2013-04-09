package org.juxtasoftware.service;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.model.ComparisonSet;
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
public class SetRemover {
    @Autowired ComparisonSetDao setDao;
    @Autowired private MetricsHelper metrics;
    
    public void remove(Workspace ws, ComparisonSet set) throws ResourceException  {
        if ( set.getStatus().equals(ComparisonSet.Status.COLLATING) ||
             set.getStatus().equals(ComparisonSet.Status.TOKENIZING) ) {
            throw new ResourceException(
                Status.CLIENT_ERROR_CONFLICT,
                "Cannot delete set; collation is in progress");
        }
        this.setDao.delete(set);
        this.metrics.setWitnessCountChanged( ws );
    }

}
