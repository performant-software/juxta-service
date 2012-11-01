package org.juxtasoftware.util;

import java.util.List;

import org.juxtasoftware.dao.MetricsDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WorkspaceDao;
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
public class MetricsInitializer {
    @Autowired private WorkspaceDao workspaceDao;
    @Autowired private MetricsDao metricsDao;
    @Autowired private SourceDao srcDao;

    public void init() {
        // start by getting all known workspaces
        for (Workspace ws : this.workspaceDao.list()) {
            Metrics m = this.metricsDao.get(ws);
            if (m == null) {
                // New! Create a metrics entry for it
                m = new Metrics();
                m.setWorkspace(ws.getName());

                List<Source> srcs = this.srcDao.list(ws);
                m.setNumSources(srcs.size());
                if ( srcs.size() > 0 ) {
                    int minSize = Integer.MAX_VALUE;
                    int maxSize = -1;
                    int total = 0;
                    for (Source s : srcs) {
                        int sizeK = (int) (s.getText().getLength() / 1024);
                        total += sizeK;
                        if (sizeK > maxSize) {
                            maxSize = sizeK;
                        }
                        if (sizeK < minSize) {
                            minSize = sizeK;
                        }
                    }
                    m.setMaxSourceSize(maxSize);
                    m.setMinSourceSize(minSize);
                    m.setMeanSourceSize(total / srcs.size() );
                    m.setTotalSourcesSize(total);
                }
                this.metricsDao.create(m);
            }
        }
    }
}
