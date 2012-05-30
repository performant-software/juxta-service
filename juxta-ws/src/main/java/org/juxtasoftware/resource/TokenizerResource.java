package org.juxtasoftware.resource;

import java.io.IOException;
import java.util.Date;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.service.Tokenizer;
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

/**
 * Resource to initiate, monitor and control the tokenizer
 *  
 * @author lfoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class TokenizerResource extends BaseResource {
    private enum Action {TOKENIZE, STATUS, CANCEL}
    @Autowired private ComparisonSetDao comparisonSetDao;
    @Autowired private Tokenizer tokenizer;
    @Autowired private TaskManager taskManager;
    private ComparisonSet set;
    private Action action;
       
    @Override
    protected void doInit() throws ResourceException {

        super.doInit();
        
        Long id= Long.parseLong((String) getRequestAttributes().get("id"));
        this.set = this.comparisonSetDao.find(id);
        if ( validateModel(this.set) == false) {
            return;
        }
        
        String act  = getRequest().getResourceRef().getLastSegment().toUpperCase();
        if ( act.equals("TOKENIZE")) {
            this.action = Action.TOKENIZE;
        } else {
            this.action = Action.valueOf(act.toUpperCase());
            if ( action == null ) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid action specified");
            }
        }
    }
    
    @Get("json")
    public Representation toJson() {
        if ( this.action.equals(Action.STATUS) == false ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Invalid GET action");
        }
        
        String json = this.taskManager.getStatus( generateTaskName(this.set.getId()) );
        return toJsonRepresentation(json);
    }
    
    @Post
    public Representation acceptPost() {
        if ( this.action.equals(Action.TOKENIZE)  ) {
            return doTokenization();
        } else if ( this.action.equals(Action.CANCEL)  ) {
            cancelTokenizer();
        }
        return null;
    }
    
    private void cancelTokenizer() {
        LOG.info("Cancel tokenizaton for set "+this.set.getId());
        this.taskManager.cancel( generateTaskName(this.set.getId()) );
        
    }

    private Representation doTokenization() {
        LOG.info("Tokenize set "+this.set.getId() ); 
        int witCnt = this.comparisonSetDao.getWitnesses(this.set).size();
        if ( witCnt < 2 ) {
            this.set.setStatus(ComparisonSet.Status.ERROR);
            this.comparisonSetDao.update(this.set);
            setStatus(Status.CLIENT_ERROR_PRECONDITION_FAILED);
            return toTextRepresentation("Collation requires at least 2 witnesses. This set has "+witCnt+".");
        }
        this.comparisonSetDao.clearCollationData(this.set);
        CollatorConfig cfg = this.comparisonSetDao.getCollatorConfig(set);
        this.taskManager.submit( new TokenizeTask(this.tokenizer, cfg, set) );
        return toTextRepresentation(this.set.getId().toString());
    }
    
    private static String generateTaskName(long setId ) {
        return "tokenize-"+setId;
    }

    /**
     * Task to asynchronously execute the tokenization
     */
    private static class TokenizeTask implements BackgroundTask {
        private final String name;
        private BackgroundTaskStatus status;
        private final Tokenizer tokenizer;
        private final CollatorConfig config;
        private final ComparisonSet set;
        private Date startDate;
        private Date endDate;
        
        public TokenizeTask(Tokenizer tokenizer, CollatorConfig cfg, ComparisonSet set ) {
            this.name = generateTaskName(set.getId());
            this.status = new BackgroundTaskStatus( this.name );
            this.tokenizer = tokenizer;
            this.config = cfg;
            this.set = set;
            this.startDate = new Date();
        }
        
        @Override
        public void run() {
            try {
                LOG.info("Begin task "+this.name);
                this.status.begin();
                this.tokenizer.tokenize(this.set, this.config, this.status);
                LOG.info("task "+this.name+" COMPLETE");
                this.endDate = new Date();
            } catch (IOException e) {
                LOG.error(this.name+" task failed", e.toString());
                this.status.fail(e.toString());
                this.endDate = new Date();
            } catch ( BackgroundTaskCanceledException e) {
                LOG.info( this.name+" task was canceled");
                this.endDate = new Date();
            } catch (Exception e) {
                LOG.error(this.name+" task failed", e.toString());
                this.status.fail(e.toString());
                this.endDate = new Date();
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
