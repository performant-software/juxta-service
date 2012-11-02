package org.juxtasoftware.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.juxtasoftware.dao.MetricsDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Metrics;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Workspace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Helper component used to initialze the metrics for any new users since the
 * last time the service was started
 * 
 * @author lfoster
 * 
 */
@Component
@Scope(BeanDefinition.SCOPE_SINGLETON)
public class MetricsHelper {
    @Autowired private WorkspaceDao workspaceDao;
    @Autowired private MetricsDao metricsDao;
    @Autowired private SourceDao srcDao;
    private Map<ComparisonSet, Long> collationStartTimes = new HashMap<ComparisonSet, Long>();
    
    public void init() {
        // start by getting all known workspaces
        for (Workspace ws : this.workspaceDao.list()) {
            Metrics m = this.metricsDao.get(ws);
            if (m == null) {
                // New! Create a metrics entry for it
                m = new Metrics();
                m.setWorkspace(ws.getName());
                updateSourceMetrics(ws, m);
                this.metricsDao.create( m );
            }
        }
    }
    
    public void sourceAdded(final Workspace ws, final Source src) {
        Metrics m = this.metricsDao.get(ws);
        m.setNumSources( m.getNumSources()+1 );
        int size = (int)src.getText().getLength();
        m.setTotalSourcesSize( m.getTotalSourcesSize()+size);
        if ( size < m.getMinSourceSize() ) {
            m.setMinSourceSize(size);
        } 
        if ( size > m.getMaxSourceSize() ) {
            m.setMaxSourceSize(size);
        }
        int oldMean = m.getMeanSourceSize();
        m.setMeanSourceSize( (oldMean+size)/2 );
        this.metricsDao.update( m );
    }
    
    private void updateSourceMetrics( final Workspace ws, Metrics m) {
        List<Source> srcs = this.srcDao.list(ws);
        m.setNumSources(srcs.size());
        if ( srcs.size() > 0 ) {
            int minSize = Integer.MAX_VALUE;
            int maxSize = -1;
            int total = 0;
            for (Source s : srcs) {
                int size = (int)s.getText().getLength();
                total += size;
                if (size > maxSize) {
                    maxSize = size;
                }
                if (size < minSize) {
                    minSize = size;
                }
            }
            m.setMaxSourceSize(maxSize);
            m.setMinSourceSize(minSize);
            m.setMeanSourceSize(total / srcs.size() );
            m.setTotalSourcesSize(total);
        } else {
            m.setMaxSourceSize(0);
            m.setMinSourceSize(0);
            m.setMeanSourceSize(0);
            m.setTotalSourcesSize(0);
        }
    }
    
    public void sourceRemoved(final Workspace ws, final Source src) {
        Metrics m = this.metricsDao.get(ws);
        updateSourceMetrics(ws, m); 
        this.metricsDao.update( m );
    }
    
    public void collationStarted( final Workspace ws, final ComparisonSet set ) {
        Metrics m = this.metricsDao.get(ws);
        m.setNumCollationsStarted( m.getNumCollationsStarted()+1 );
        this.metricsDao.update(m);
        this.collationStartTimes.put(set, System.currentTimeMillis());
    }
    
    public void collationFinished( final Workspace ws, final ComparisonSet set  ) {
        Metrics m = this.metricsDao.get(ws);
        m.setNumCollationsFinished( m.getNumCollationsFinished()+1 );
        
        Long startTime = this.collationStartTimes.remove(set);
        long deltaMs = System.currentTimeMillis() - startTime;
        m.setTotalTimeCollating( m.getTotalTimeCollating()+deltaMs );
        
        this.metricsDao.update(m);
    }
}
