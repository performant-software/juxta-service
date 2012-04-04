package org.juxtasoftware.resource;

import java.io.IOException;
import java.util.Date;
import java.util.Set;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.service.ComparisonSetCollator;
import org.juxtasoftware.util.BackgroundTask;
import org.juxtasoftware.util.BackgroundTaskCanceledException;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.juxtasoftware.util.TaskManager;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class CollatorResource extends BaseResource {

    private enum Action { COLLATE, CONFIG, STATUS, CANCEL }
    
    @Autowired private ComparisonSetDao comparisonSetDao;
    @Autowired private ComparisonSetCollator diffCollator;
    @Autowired private TaskManager taskManager;
    
    private Action action;
    private ComparisonSet set;
        
    @Override
    protected void doInit() throws ResourceException {
        
        super.doInit();
        
        // make sure set exists and is in workspace
        Long id = Long.parseLong( (String)getRequest().getAttributes().get("id"));
        this.set = this.comparisonSetDao.find(id);
        if ( validateModel(this.set) == false) {
            return;
        }
        
        // validate the action requested
        String act  = getRequest().getResourceRef().getLastSegment().toUpperCase();
        if ( act.equals("COLLATE")) {
            this.action = Action.COLLATE;
        } else if ( act.equals("COLLATOR")) {
            this.action = Action.CONFIG;
        } else {
            this.action = Action.valueOf(act);
            if ( this.action == null ) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid collation action specified");
            }
        }
    }
    
    @Post("json")
    public Representation acceptPost( final String json ) {
        
        if ( this.action.equals(Action.CANCEL)) { 
            LOG.info("CANCEL collation of set "+this.set.getId());
            this.taskManager.cancel( generateTaskName(this.set.getId()) );
            return toTextRepresentation("");
        } else if ( this.action.equals(Action.CONFIG)) {
            return configureCollator(json);
        } else if ( this.action.equals(Action.COLLATE)) {
            return doCollation();
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Invalid collation action specified");
        }
    }

    private Representation doCollation() {
        
        if (this.set.getStatus().equals(ComparisonSet.Status.COLLATING)) {
            setStatus(Status.CLIENT_ERROR_CONFLICT);
            return toTextRepresentation("Set "+this.set.getId()+" is currently collating");
        }
        Set<Witness> witnesses = this.comparisonSetDao.getWitnesses(this.set);
        if ( witnesses.size() < 2) {
            setStatus(Status.CLIENT_ERROR_FAILED_DEPENDENCY);
            return toTextRepresentation("Set "+this.set.getId()+
                " has fewer than 2 witnesses; cannot collate");
        }
        
        CollatorConfig cfg = this.comparisonSetDao.getCollatorConfig(set);
        this.taskManager.submit( new CollateTask(this.comparisonSetDao, this.diffCollator, cfg, set) );
        return toTextRepresentation(this.set.getId().toString());
    }

    private Representation configureCollator(String json) {
        Gson gson = new Gson();
        CollatorConfig cfg = gson.fromJson(json, CollatorConfig.class );
        this.comparisonSetDao.updateCollatorConfig(this.set, cfg);
        return toTextRepresentation(this.set.getId().toString());
    }
    
    @Get("json")
    public Representation handeGet() {
        if ( this.action.equals(Action.CONFIG)) {
            return getCollationConfig();
        }
        else if ( this.action.equals(Action.STATUS)) {
            return getCollationStatus();
        }
        
        setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
        return toTextRepresentation("Invalid collation action specified");
    }
    
    private Representation getCollationStatus() {
        String json = this.taskManager.getStatus( generateTaskName(this.set.getId()) );
        return toJsonRepresentation(json);
    }
    
    private static String generateTaskName(long setId) {
        return "collate-"+setId;
    }

    private Representation getCollationConfig() {
        CollatorConfig cfg = this.comparisonSetDao.getCollatorConfig(this.set);
        Gson gson = new Gson();
        String json = gson.toJson(cfg);
        return toJsonRepresentation(json);
    }
    
    /**
     * Task to asynchronously execute the collation
     */
    private static class CollateTask implements BackgroundTask {
        private final String name;
        private BackgroundTaskStatus status;
        private final ComparisonSetCollator collator;
        private final ComparisonSet set;
        private final CollatorConfig config;
        private Date startDate;
        private Date endDate;
        private ComparisonSetDao setDao;
        
        public CollateTask(ComparisonSetDao dao, ComparisonSetCollator collator, CollatorConfig cfg, ComparisonSet set ) {
            this.name =  generateTaskName( set.getId());
            this.status = new BackgroundTaskStatus( this.name );
            this.collator = collator;
            this.set = set;
            this.config = cfg;
            this.startDate = new Date();
            this.setDao = dao;
        }
        
        @Override
        public void run() {
            try {
                LOG.info("Begin collation task "+this.name);
                this.set.setStatus( ComparisonSet.Status.COLLATING );
                this.setDao.update(this.set);
                this.status.begin();
                this.collator.collate( this.set, this.config, this.status);
                LOG.info("collation task "+this.name+" COMPLETE");
                this.endDate = new Date();           
                this.set.setStatus( ComparisonSet.Status.COLLATED );
                this.setDao.update(this.set);   
            } catch (IOException e) {
                LOG.error(this.name+" task failed", e.toString());
                this.status.fail(e.toString());
                this.endDate = new Date();
                this.set.setStatus( ComparisonSet.Status.ERROR );
                this.setDao.update(this.set);
            } catch ( BackgroundTaskCanceledException e) {
                LOG.info( this.name+" task was canceled");
                this.endDate = new Date();
                this.set.setStatus( ComparisonSet.Status.NOT_COLLATED );
                this.setDao.update(this.set);
            } catch (Exception e) {
                LOG.error(this.name+" task failed", e);
                this.status.fail(e.toString());
                this.endDate = new Date();       
                this.set.setStatus( ComparisonSet.Status.ERROR );
                this.setDao.update(this.set);
            }
        }
        
        @Override
        public void cancel() {
            this.status.cancel();
        }

        @Override
        public BackgroundTaskStatus.Status getStatus() {
            return this.status.getStatus();
        }

        @Override
        public String getName() {
            return this.name;
        }
        
        @Override
        public Date getEndTime() {
            return this.endDate;
        }
        
        @Override
        public Date getStartTime() {
            return this.startDate;
        }
        
        @Override
        public String getMessage() {
            return this.status.getNote();
        }
    }
}
