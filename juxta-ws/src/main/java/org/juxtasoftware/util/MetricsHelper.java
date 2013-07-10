package org.juxtasoftware.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.juxtasoftware.Constants;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.MetricsDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Metrics;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
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
    @Autowired private ComparisonSetDao setDao;
    @Autowired private Boolean captureMetrics;
    
    private ConcurrentHashMap<Long, Long> collationStartTimes = new ConcurrentHashMap<Long, Long>();
    private static final Logger LOG = LoggerFactory.getLogger( Constants.METRICS_LOGGER_NAME );
    private static final Logger DEBUG_LOG = LoggerFactory.getLogger( Constants.WS_LOGGER_NAME );
    
    @Scheduled(cron="0 0 12 * * *")
    public void logMetrics() {
        if ( this.captureMetrics ) {
            for ( Metrics m : this.metricsDao.list() ) {
                LOG.info( this.toCsv(m, true) );
            }
        }
    }
    
    public String toCsv( Metrics m ) {
        return this.toCsv(m,false);
    }
    public String toCsv( Metrics m, boolean dateStamp ) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getWorkspace()).append(",");
        sb.append(m.getNumSources()).append(",");
        sb.append(m.getMinSourceSize()).append(",");
        sb.append(m.getMaxSourceSize()).append(",");
        sb.append(m.getMeanSourceSize()).append(",");
        sb.append(m.getTotalSourcesSize()).append(",");
        sb.append(m.getTotalTimeCollating()).append(",");
        sb.append(m.getNumCollationsStarted()).append(",");
        sb.append(m.getNumCollationsFinished());
        if ( dateStamp ) {
            sb.append(",");
            sb.append( new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format( new Date()));
        }
        return sb.toString();
    }
    
    
    public void init() {
        if ( this.captureMetrics ) {
            for (Workspace ws : this.workspaceDao.list()) {
                Metrics m = this.metricsDao.get(ws);
                if (m == null) {
                    // New! Create a metrics entry for it
                    m = new Metrics();
                    m.setWorkspace(ws.getName());
                    updateSourceMetrics(ws, m);
                    updateSetMetrics(ws, m);
                    this.metricsDao.create( m );
                } else {
                    updateSourceMetrics(ws, m);
                    updateSetMetrics(ws, m);
                    this.metricsDao.update(m);
                }
            }
        }
    }
    
    public void workspaceAdded( final Workspace ws ) {
        if ( this.captureMetrics ) {
            try {
                DEBUG_LOG.info("Created new user workspace "+ws.getName());
                Metrics m = new Metrics();
                m.setWorkspace(ws.getName());
                this.metricsDao.create(m);
            } catch (Exception e) {
                DEBUG_LOG.error("Unable to create metrics entry for " + ws, e);
            }
        }
    }
    
    public void workspaceRemoved(final Workspace ws) {
        if ( this.captureMetrics ) {
            try {
                DEBUG_LOG.info("Deleted user workspace "+ws.getName());
                Metrics m = new Metrics();
                m.setWorkspace(ws.getName());
                this.metricsDao.delete(m);
            } catch (Exception e) {
                DEBUG_LOG.error("Unable to delete metrics entry for " + ws, e);
            }
        }
    }
    
    public void sourceAdded(final Workspace ws, final Source src) {
        if ( this.captureMetrics ) {
            try {
                Metrics m = this.metricsDao.get(ws);
                m.setNumSources( m.getNumSources()+1 );
                int size = (int)src.getText().getLength();
                m.setTotalSourcesSize( m.getTotalSourcesSize()+size);
                if ( size < m.getMinSourceSize() ||  m.getMinSourceSize() == 0) {
                    m.setMinSourceSize(size);
                } 
                if ( size > m.getMaxSourceSize() ) {
                    m.setMaxSourceSize(size);
                }
                int oldMean = m.getMeanSourceSize();
                m.setMeanSourceSize( (oldMean+size)/2 );
                this.metricsDao.update( m );
            } catch (Exception e) {
                DEBUG_LOG.error("Metrics error tracking add "+src, e);
            }
        }
    }
    
    private void updateSetMetrics( final Workspace ws, Metrics m ) {
        if ( this.captureMetrics ) {
            try {
                int max = -1;
                int min = Integer.MAX_VALUE;
                int total = 0;
                List<ComparisonSet> sets = this.setDao.list(ws);
                if ( sets.size() > 0 ) {
                    for (ComparisonSet set : sets) {
                        int witCnt = this.setDao.getWitnesses(set).size();
                        if ( witCnt > max ) {
                            max = witCnt;
                        }
                        if ( witCnt < min ) {
                            min = witCnt;
                        }
                        total += witCnt;
                    }
                    float mean = (float)total / (float)sets.size();
                    m.setMinSetWitnesses(min);
                    m.setMaxSetWitnesses(max);
                    m.setMeanSetWitnesses(Math.round(mean));
                }
            } catch (Exception e ) {
                DEBUG_LOG.error("Metrics error tracking set witness counts ", e);
            }
        }
    }
    
    private void updateSourceMetrics( final Workspace ws, Metrics m) {
        if ( this.captureMetrics ) {
            try {
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
            } catch ( Exception e) {
                DEBUG_LOG.error("Metrics error updating sources", e);
            }
        }
    }
    
    public void sourceRemoved(final Workspace ws, final Source src) {
        if ( this.captureMetrics ) {
            try {
                Metrics m = this.metricsDao.get(ws);
                updateSourceMetrics(ws, m); 
                this.metricsDao.update( m );
            } catch ( Exception e ) {
                DEBUG_LOG.error("Metrics error tracking "+src+" removal", e);
            }
        }
    }
    
    public void setWitnessCountChanged( final Workspace ws) {
        if ( this.captureMetrics ) {
            Metrics m = this.metricsDao.get(ws);
            updateSetMetrics(ws, m);
            this.metricsDao.update(m);
        }
    }
    
    public void collationStarted( final Workspace ws, final ComparisonSet set ) {
        if ( this.captureMetrics ) {
            try {
                Metrics m = this.metricsDao.get(ws);
                m.setNumCollationsStarted( m.getNumCollationsStarted()+1 );
                this.metricsDao.update(m);
                this.collationStartTimes.put(set.getId(), System.currentTimeMillis());
                DEBUG_LOG.info("Mark collation start of "+set);
                DEBUG_LOG.info("Timestamp "+this.collationStartTimes.get(set.getId()));
            } catch ( Exception e ) {
                DEBUG_LOG.error("Metrics error tracking "+set+" collation start", e);
            }
        }
    }
    
    public void collationFinished( final Workspace ws, final ComparisonSet set  ) {
        if ( this.captureMetrics ) {
            try {
                Metrics m = this.metricsDao.get(ws);
                m.setNumCollationsFinished( m.getNumCollationsFinished()+1 );
                
                Long startTime = this.collationStartTimes.get(set.getId());
                DEBUG_LOG.info("Mark collation END of "+set);
                DEBUG_LOG.info("Timestamp "+this.collationStartTimes.get(set.getId()));
                this.collationStartTimes.remove(set.getId());
                
                if  (startTime != null ) {
                    long deltaMs = System.currentTimeMillis() - startTime;
                    m.setTotalTimeCollating( m.getTotalTimeCollating()+deltaMs );
                } else {
                    DEBUG_LOG.error("No start time for collating "+set+". Can't track timing metrics");
                }
                
                this.metricsDao.update(m);
            } catch ( Exception e ) {
                DEBUG_LOG.error("Metrics error tracking "+set+" collation end", e);
            }
        }
    }
}
