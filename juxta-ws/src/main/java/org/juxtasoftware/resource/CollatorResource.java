package org.juxtasoftware.resource;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.service.ComparisonSetCollator;
import org.juxtasoftware.service.Tokenizer;
import org.juxtasoftware.util.BackgroundTask;
import org.juxtasoftware.util.BackgroundTaskCanceledException;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.juxtasoftware.util.MetricsHelper;
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

    private enum Action {
        COLLATE, CONFIG
    }

    @Autowired private ComparisonSetDao setDao;
    @Autowired private Tokenizer tokenizer;
    @Autowired private ComparisonSetCollator collator;
    @Autowired private TaskManager taskManager;
    @Autowired private MetricsHelper metrics;

    private Action action;
    private ComparisonSet set;

    @Override
    protected void doInit() throws ResourceException {

        super.doInit();

        // make sure set exists and is in workspace
        Long id = getIdFromAttributes("id");
        if ( id == null ) {
            return;
        }
        this.set = this.setDao.find(id);
        if (validateModel(this.set) == false) {
            return;
        }

        // validate the action requested
        String act = getRequest().getResourceRef().getLastSegment().toUpperCase();
        if (act.equals("COLLATE")) {
            this.action = Action.COLLATE;
        } else if (act.equals("COLLATOR")) {
            this.action = Action.CONFIG;
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid collation action specified");
        }
    }

    @Post("json")
    public Representation acceptPost(final String json) {

        if (this.action.equals(Action.CONFIG)) {
            return configureCollator(json);
        } else if (this.action.equals(Action.COLLATE)) {
            return doCollation();
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Invalid collation action specified");
        }
    }

    private Representation doCollation() {

        if (this.set.getStatus().equals(ComparisonSet.Status.COLLATING) || 
            this.set.getStatus().equals(ComparisonSet.Status.TOKENIZING)  ) {
            LOG.error("Attempt to collate "+this.set+" when it is aalready collating");
            setStatus(Status.CLIENT_ERROR_CONFLICT);
            return toTextRepresentation("Set " + this.set.getId() + " is currently collating");
        }
        List<Witness> witnesses = this.setDao.getWitnesses(this.set);
        if (witnesses.size() < 2) {
            LOG.error("Attempt to collate "+this.set+" that has less than 2 witnesses");
            setStatus(Status.CLIENT_ERROR_FAILED_DEPENDENCY);
            return toTextRepresentation("Set " + this.set.getId()
                + " has fewer than 2 witnesses; cannot collate");
        }

        final String taskId = generateTaskName(this.set.getId());
        this.taskManager.submit(new CollateTask(taskId));
        return toTextRepresentation(taskId);
    }

    private Representation configureCollator(String json) {
        Gson gson = new Gson();
        CollatorConfig cfg = gson.fromJson(json, CollatorConfig.class);
        this.setDao.updateCollatorConfig(this.set, cfg);
        return toTextRepresentation(this.set.getId().toString());
    }

    @Get("json")
    public Representation handeGet() {
        if (this.action.equals(Action.CONFIG)) {
            return getCollationConfig();
        } 
        setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
        return toTextRepresentation("Invalid collation action specified");
    }

    private String generateTaskName(final Long setId) {
        final int prime = 31;
        int result = 1;
        result = prime * result + setId.hashCode();
        return "collate-"+result;
    }

    private Representation getCollationConfig() {
        CollatorConfig cfg = this.setDao.getCollatorConfig(this.set);
        Gson gson = new Gson();
        String json = gson.toJson(cfg);
        return toJsonRepresentation(json);
    }

    /**
     * Task to asynchronously execute the collation
     */
    private class CollateTask implements BackgroundTask {
        private final String name;
        private BackgroundTaskStatus status;
        private final CollatorConfig config;
        private Date startDate;
        private Date endDate;

        public CollateTask(final String name) {
            this.name = name;
            this.status = new BackgroundTaskStatus(this.name);
            this.config =  CollatorResource.this.setDao.getCollatorConfig(set);;
            this.startDate = new Date();
        }

        @Override
        public Type getType() {
            return BackgroundTask.Type.COLLATE;
        }

        @Override
        public void run() {
            try {
                LOG.info("Begin collation task " + this.name);
                this.status.begin();
                if ( set.getStatus().equals(ComparisonSet.Status.TOKENIZED)) {
                    CollatorResource.this.collator.collate(set, this.config, this.status);
                } else {
                    CollatorResource.this.metrics.collationStarted(CollatorResource.this.workspace, CollatorResource.this.set);
                    LOG.info(this.name+" tokenizing....");
                    CollatorResource.this.tokenizer.tokenize( CollatorResource.this.set, this.config, this.status);
                    LOG.info(this.name+" collating....");
                    CollatorResource.this.collator.collate(set, this.config, this.status);
                }
                LOG.info("collation task " + this.name + " COMPLETE");
                metrics.collationFinished(workspace,set);
                this.endDate = new Date();
            } catch (IOException e) {
                LOG.error(this.name + " task failed", e.toString());
                this.status.fail(e.toString());
                this.endDate = new Date();
                set.setStatus(ComparisonSet.Status.ERROR);
                setDao.update(set);
            } catch (BackgroundTaskCanceledException e) {
                LOG.info(this.name + " task was canceled");
                this.endDate = new Date();
                set.setStatus(ComparisonSet.Status.NOT_COLLATED);
                setDao.update(set);
            } catch (Exception e) {
                LOG.error(this.name + " task failed", e);
                this.status.fail(e.toString());
                this.endDate = new Date();
                set.setStatus(ComparisonSet.Status.ERROR);
                setDao.update(set);
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
