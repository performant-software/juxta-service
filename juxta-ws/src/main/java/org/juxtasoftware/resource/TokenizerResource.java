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
    @Autowired private ComparisonSetDao comparisonSetDao;
    @Autowired private Tokenizer tokenizer;
    @Autowired private TaskManager taskManager;
    private ComparisonSet set;
       
    @Override
    protected void doInit() throws ResourceException {

        super.doInit();
        
        String idStr = (String) getRequestAttributes().get("id");
        Long id = null;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException e ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid source id");
            return;
        }
        
        this.set = this.comparisonSetDao.find(id);
        if ( validateModel(this.set) == false) {
            return;
        }
    }
    
    @Post
    public Representation acceptPost() {
        LOG.info("Tokenize set "+this.set.getId() ); 
        int witCnt = this.comparisonSetDao.getWitnesses(this.set).size();
        if ( witCnt < 2 ) {
            this.set.setStatus(ComparisonSet.Status.ERROR);
            this.comparisonSetDao.update(this.set);
            setStatus(Status.CLIENT_ERROR_PRECONDITION_FAILED);
            return toTextRepresentation("Collation requires at least 2 witnesses. This set has "+witCnt+".");
        }
        
        final String taskId = generateTaskName(this.set.getId());
        this.taskManager.submit( new TokenizeTask(taskId) );
        return toTextRepresentation( taskId );   
    }
    
    private String generateTaskName(final Long setId ) {
        final int prime = 31;
        int result = 1;
        result = prime * result + setId.hashCode();
        return "tokenize-"+result;
    }

    /**
     * Task to asynchronously execute the tokenization
     */
    private class TokenizeTask implements BackgroundTask {
        private final String name;
        private BackgroundTaskStatus status;
        private final CollatorConfig config;
        private Date startDate;
        private Date endDate;
        
        public TokenizeTask(final String name ) {
            this.name = name;
            this.status = new BackgroundTaskStatus( this.name );
            this.config = comparisonSetDao.getCollatorConfig( set); 
            this.startDate = new Date();
        }
        
        @Override
        public Type getType() {
            return BackgroundTask.Type.TOKENIZE;
        }
        
        @Override
        public void run() {
            try {
                LOG.info("Begin task "+this.name);
                this.status.begin();
                tokenizer.tokenize( TokenizerResource.this.set, this.config, this.status);
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
                LOG.error(this.name+" task failed", e);
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
