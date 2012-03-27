package org.juxtasoftware.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.lang.StringEscapeUtils;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.CacheDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.RevisionDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.service.SourceTransformer;
import org.juxtasoftware.service.importer.ps.ParallelSegmentationImportImpl;
import org.juxtasoftware.util.BackgroundTask;
import org.juxtasoftware.util.BackgroundTaskCanceledException;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.juxtasoftware.util.RangedTextReader;
import org.juxtasoftware.util.TaskManager;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.fileupload.RestletFileUpload;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import eu.interedition.text.Range;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SourceResource extends BaseResource implements ApplicationContextAware {

    @Autowired private SourceDao sourceDao;
    @Autowired private RevisionDao revisionDao;
    @Autowired private CacheDao cacheDao;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private SourceTransformer transformer;
    @Autowired private TaskManager taskManager;
    
    private ApplicationContext context;
    private Range range = null;
    private Source source;
    private boolean isStatusRequest;

    /**
     * Extract the doc ID from the request attributes. This is the doc that
     * will be acted upon by the get/delete verbs.
     */
    @Override
    protected void doInit() throws ResourceException {
        
        super.doInit();
        
        Long id = Long.parseLong((String) getRequestAttributes().get("id"));
        this.source = this.sourceDao.find(this.workspace.getId(), id);
        
        // was a range set requested?
        if (getQuery().getValuesMap().containsKey("range") ) {
            String rangeInfo = getQuery().getValues("range");
            String ranges[] = rangeInfo.split(",");
            if ( ranges.length == 2) {
                this.range = new Range( 
                    Integer.parseInt(ranges[0]),
                    Integer.parseInt(ranges[1]) );
            } else {
                getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid Range specified");
            }
        }
        
        validateModel( this.source );
        
        // grab the last segment of the request and see if this
        // is a status related request
        String act = getRequest().getResourceRef().getLastSegment().toUpperCase();
        this.isStatusRequest = act.equals("STATUS");
    }
    
    /**
     * Get the source refreneced by <code>sourceID</code> and return its
     * XML representation as the response
     * @return
     */
    @Get("html")
    public Representation toHtml() throws IOException {
        
        // don't support HTML status requests
        if (isStatusRequest ) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return null;
        }
        
        final RangedTextReader reader = new RangedTextReader();
        reader.read( this.sourceDao.getContentReader(this.source), this.range );

        Map<String,Object> map = new HashMap<String,Object>();
        map.put("name", this.source.getFileName());
        map.put("sourceId", this.source.getId());
        map.put("hasRevisionSets", this.revisionDao.hasRevisionSets(this.source.getId()));
        map.put("page", "source");
        map.put("title", "Juxta Source: "+this.source.getFileName());
        map.put("text",  toTextRepresentation( StringEscapeUtils.escapeHtml(reader.toString())));
        return toHtmlRepresentation("source.ftl", map);
    }

    /**
     * Get the source refreneced by <code>sourceID</code> and return its
     * XML representation as the response
     * @return
     */
    @Get("txt")
    public Representation toTxt() throws IOException {
        // don't support TXT status requests
        if (isStatusRequest ) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return null;
        }
        
        final RangedTextReader reader = new RangedTextReader();
        reader.read( this.sourceDao.getContentReader(this.source), this.range );
        return toTextRepresentation(reader.toString());
    }
    
    /**
     * Get the source refreneced by <code>sourceID</code> and return a 
     * json object containing source meta data and complete content
     * @return
     */
    @Get("json")
    public Representation toJson() throws IOException {
        if ( this.isStatusRequest ) {
            return getUpdateStatus();
        } else {
            final RangedTextReader reader = new RangedTextReader();
            reader.read( this.sourceDao.getContentReader(this.source), this.range );
            JsonObject obj = new JsonObject();
            obj.addProperty("id", this.source.getId());
            obj.addProperty("fileName", this.source.getFileName());
            obj.addProperty("content", reader.toString());
            Gson gson = new Gson();
            return toTextRepresentation(gson.toJson(obj));
        }
    }
    
    /**
     * Handle POST only to cancel updates
     * @param entity
     */
    @Post
    public void handlePost( Representation entity ) {
        if ( this.isStatusRequest == false ) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND);
        } else {
            cancelUpdate();
        } 
    }
    
    /**
     * Update the content of source <code>sourcceID</code> with the data
     * contined in the post
     * @param entity
     * @return
     * @throws ResourceException
     */
    @Put
    public Representation update( Representation entity  ) throws ResourceException {
        if ( entity == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Missing source payload");
        }
        
        // extract the input stream from the multipart request
        if (MediaType.MULTIPART_FORM_DATA.equals(entity.getMediaType(),true)) {
            InputStream srcInputStream = null;
            String sourceName= null;
            boolean isParallelSegmented = false;
            try {
                // pull the list of items in this multipart request
                DiskFileItemFactory factory = new DiskFileItemFactory();
                factory.setSizeThreshold(1000240);
                RestletFileUpload upload = new RestletFileUpload(factory);
                List<FileItem> items = upload.parseRequest(getRequest());
                for ( FileItem item : items ) {
                    if ( item.getFieldName().equals("sourceFile")) {
                        srcInputStream = item.getInputStream();
                    } else  if ( item.getFieldName().equals("sourceName")) {
                        sourceName = item.getString();
                    } else  if ( item.getFieldName().equals("parallelSegmented")) {
                        isParallelSegmented = true;
                    } 
                }
                
                // Fail requests with none of the required payload
                if ( srcInputStream == null && sourceName == null ) {
                    setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                    return toTextRepresentation("Request is missing all content");
                }
                
                // Rename source or rename/update content 
                if ( sourceName != null && sourceName.length() > 0 && srcInputStream == null ) {
                    return renameSource( sourceName );
                } else {
                    // if no new name was specifed, just pass along the
                    // old name as if it were new.
                    if ( sourceName == null || sourceName.length() == 0) {
                        sourceName = this.source.getFileName();
                    }
                    if ( isParallelSegmented ) {
                        return updateParallelSegmentedSource( srcInputStream, sourceName  );
                    } else {
                        return updateSource( srcInputStream, sourceName );
                    }
                }
                
            } catch (Exception e) {
                LOG.error("Unable to update source", e);
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST );
                return toTextRepresentation("File upload failed");
            }    
        } 
        
        setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
        return toTextRepresentation("Unsupported content type in put");
    }
    
    private Representation getUpdateStatus() {
        String json = this.taskManager.getStatus( "update-src-"+this.source.getId() );
        return toJsonRepresentation(json);
    }

    private void cancelUpdate() {
        LOG.info("Cancel source update " + this.source.getId());
        this.taskManager.cancel( "update-src-"+this.source.getId() );
    }
    
    /**
     * Update a source that is encoded in TEI Parallel Segmentation. Requires
     * a re-import of the newly editied source.
     * 
     * @param srcInputStream
     * @param sourceName
     * @return
     * @throws Exception 
     */
    private Representation updateParallelSegmentedSource(InputStream srcInputStream, String newName) throws Exception {
        TeiPsSourceUpdater updater = new TeiPsSourceUpdater(this.source, srcInputStream, newName);
        this.taskManager.submit( new UpdateTask(updater));
        return toJsonRepresentation(this.source.getId().toString());
    }

    /**
     * Create a task to update source content and name. This will also find all related witnesses and comparison sets.
     * Witnesses will be re-parsed and comparison sets will be invalidated (cache cleared,
     * alignments reset and collated flag set to false)
     * 
     * @param srcInputStream
     * @param newName
     * @return
     */
    private Representation updateSource( InputStream srcInputStream, final String newName )  {
        SourceUpdater updater = new SourceUpdater(this.source, srcInputStream, newName);
        this.taskManager.submit( new UpdateTask(updater));
        return toJsonRepresentation(this.source.getId().toString());
    }

    /**
     * Change the name of the source
     * @param newName
     * @return
     */
    private Representation renameSource( final String newName ) {
        this.sourceDao.update(this.source, newName);
        return toJsonRepresentation("{\"result\": \"success\"}");
    }

    /**
     * Delete the raw document resource with the ID specified in the request
     */
    @Delete
    public Representation remove() {
        LOG.info("Delete source "+source.getId());
        
        // get a list of all uses of this source. Comparison
        // set uses need special treatment; mark them as NOT collated,
        // clear their collation cache and remove all alignments
        List<Usage> usage = this.sourceDao.getUsage(this.source);
        for (Usage u : usage) {
            // Manually delete the witness. This is necessary
            // cuz witness content is stored in the text_content area
            // and will not cascade delete itself.
            if ( u.getType().equals(Usage.Type.WITNESS)) {
                Witness w = this.witnessDao.find(u.getId());
                this.witnessDao.delete(w);
            } else if ( u.getType().equals(Usage.Type.COMPARISON_SET)) {
           
                // clear cached data
                Long setId = u.getId();
                this.cacheDao.deleteAll(setId);
                
                // set status to NOT collated
                ComparisonSet set = this.setDao.find(setId);
                set.setCollated(false);
                this.setDao.update(set);
            }
        }

        this.sourceDao.delete(this.source);
        
        
        Gson gson = new Gson();
        return toJsonRepresentation( gson.toJson(usage));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }
    
    /**
     * Interface for an update task
     *
     */
    private interface UpdateExecutor {
        public void doUpdate( BackgroundTaskStatus status ) throws Exception;
        public String getName();
    }
    
    /**
     * Class to perform all of the steps necessary to update a souce
     */
    private class SourceUpdater implements UpdateExecutor {
        private InputStream srcInputStream;
        private String newName;
        private Source origSource;
        
        public SourceUpdater( Source origSource, InputStream srcInputStream, final String newName ) {
            this.srcInputStream = srcInputStream;
            this.newName = newName;
            this.origSource = origSource;
        }
        
        @Override
        public void doUpdate(BackgroundTaskStatus status) throws Exception {
            SourceResource.this.sourceDao.update(this.origSource, this.newName, 
                new InputStreamReader(this.srcInputStream));
            
            List<Usage> usage = sourceDao.getUsage(this.origSource);
            for ( Usage use : usage ) {
                if ( use.getType().equals(Usage.Type.COMPARISON_SET ) ) {
                    // clear cached data, alignmsnts and flag set as uncollated
                    Long setId = use.getId();
                    SourceResource.this.cacheDao.deleteAll(setId);
                    ComparisonSet set = SourceResource.this.setDao.find(setId);
                    set.setCollated(false);
                    SourceResource.this.setDao.update(set);
                    SourceResource.this.alignmentDao.clear(set);
                } else if  ( use.getType().equals(Usage.Type.WITNESS) ) {
                    Witness oldWit = SourceResource.this.witnessDao.find( use.getId() );
                    SourceResource.this.transformer.redoTransform(this.origSource, oldWit);                     
                }
            }
        }

        @Override
        public String getName() {
            return "update-src-"+this.origSource.getId();
        }
    }
    
    /**
     * Class to perform all of the steps necessary to update and re-import a TEI PS source
     */
    private class TeiPsSourceUpdater implements UpdateExecutor {
        private InputStream srcInputStream;
        private String newName;
        private Source origSource;
        
        public TeiPsSourceUpdater( Source origSource, InputStream srcInputStream, String newName ) {
            this.srcInputStream = srcInputStream;
            this.newName = newName;
            this.origSource = origSource;
        }
        
        @Override
        public void doUpdate(BackgroundTaskStatus status) throws Exception {
            // First, update the source with the new content and (possibly) name
            SourceResource.this.sourceDao.update(this.origSource, newName, new InputStreamReader(srcInputStream));
            Source source = SourceResource.this.sourceDao.find(this.origSource.getWorkspaceId(), this.origSource.getId());
            ComparisonSet set = null;
            for ( Usage u : SourceResource.this.sourceDao.getUsage(source)) {
                if ( u.getType().equals(Usage.Type.COMPARISON_SET)) {
                    set = SourceResource.this.setDao.find(u.getId());
                    if ( set.getName().equals(this.origSource.getFileName())) {
                        break;
                    } else {
                        set = null;
                    }
                }
            }
            
            ParallelSegmentationImportImpl importService = SourceResource.this.context.getBean(ParallelSegmentationImportImpl.class);
            importService.reimportSource(set, source);
        }
        
        @Override
        public String getName() {
            return "update-src-"+this.origSource.getId();
        }
    }
    
    /**
     * Task to asynchronously execute the source update and push the
     * changes out to witnesses and comparison sets
     */
    private class UpdateTask implements BackgroundTask {
        private BackgroundTaskStatus task;
        private Date startDate;
        private Date endDate;
        private UpdateExecutor updateExecutor;
        private BackgroundTaskStatus.Status status = BackgroundTaskStatus.Status.PENDING;
        
        public UpdateTask(UpdateExecutor update) {
            this.task = new BackgroundTaskStatus( update.getName() );
            this.startDate = new Date();
            this.updateExecutor = update;
        }
        
        @Override
        public void run() {
            try {
                LOG.info("Begin update source task "+this.updateExecutor.getName());
                this.status = BackgroundTaskStatus.Status.PROCESSING;
                this.task.begin();
                this.updateExecutor.doUpdate( this.task);
                LOG.info("task "+this.updateExecutor.getName()+" COMPLETE");
                this.endDate = new Date();
                this.status = BackgroundTaskStatus.Status.COMPLETE;
            } catch ( BackgroundTaskCanceledException e) {
                LOG.info( this.updateExecutor.getName()+" update source task was canceled");
                this.endDate = new Date();
                this.status = BackgroundTaskStatus.Status.CANCELED;
            } catch (Exception e) {
                LOG.error(this.updateExecutor.getName()+" update source task failed", e);
                this.task.fail(e.getMessage());
                this.endDate = new Date();
                this.status = BackgroundTaskStatus.Status.FAILED;
            }
        }
        
        @Override
        public void cancel() {
            this.task.cancel();
        }

        @Override
        public BackgroundTaskStatus.Status getStatus() {
            return this.status;
        }

        @Override
        public String getName() {
            return this.updateExecutor.getName();
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
            return this.task.getNote();
        }
    }
}
