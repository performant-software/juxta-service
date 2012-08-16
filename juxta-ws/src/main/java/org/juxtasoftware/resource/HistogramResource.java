package org.juxtasoftware.resource;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.FileWriterWithEncoding;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.CacheDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.AlignmentConstraint;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.QNameFilter;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.BackgroundTask;
import org.juxtasoftware.util.BackgroundTaskCanceledException;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.juxtasoftware.util.QNameFilters;
import org.juxtasoftware.util.TaskManager;
import org.restlet.data.Encoding;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.engine.application.EncodeRepresentation;
import org.restlet.representation.ReaderRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Resource used to GET a json object containing histogram
 * data for a comparison set
 * 
 * @author loufoster
 *
 */
public class HistogramResource extends BaseResource {
    @Autowired private ComparisonSetDao setDao;
    @Autowired private QNameFilters filters;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private CacheDao cacheDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private TaskManager taskManager;
    
    private ComparisonSet set;
    private Witness baseWitness;
    
    protected static final Logger LOG = LoggerFactory.getLogger( "JuxtWsResource" );
    
    @Override
    protected void doInit() throws ResourceException {

        super.doInit();
        
        Long setId = Long.parseLong( (String)getRequest().getAttributes().get("id"));
        this.set = this.setDao.find(setId);
        if ( validateModel(this.set) == false) {
            return;
        }
        
        if (getQuery().getValuesMap().containsKey("base") == false ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing base parameter");
        } else {
            String baseIdStr = getQuery().getValues("base");
            Long baseId = Long.parseLong(baseIdStr);
            this.baseWitness = this.witnessDao.find( baseId);
            if ( validateModel( this.baseWitness) == false ) {
                return;
            }
        }
    }
    
    @Get("json")
    public Representation toJson() throws IOException {
        // FIRST, see if the cached version is available:
        if ( this.cacheDao.histogramExists(this.set.getId(), this.baseWitness.getId())) {
            Representation rep = new ReaderRepresentation( 
                this.cacheDao.getHistogram(this.set.getId(), this.baseWitness.getId()), 
                MediaType.APPLICATION_JSON);
            if ( isZipSupported() ) {
                return new EncodeRepresentation(Encoding.GZIP, rep);
            } else {
                return rep;
            }
        }
                
        // set up a filter to get the annotations necessary for this histogram
        QNameFilter changesFilter = this.filters.getDifferencesFilter();
        AlignmentConstraint constraints = new AlignmentConstraint(this.set, this.baseWitness.getId());
        constraints.setFilter(changesFilter);
        
        // Get the number of annotations that will be returned and do a rough calcuation
        // to see if generating this histogram will exhaust available memory - with a 5M pad
        final Long count = this.alignmentDao.count(constraints);
        final long estimatedByteUsage = count*Alignment.AVG_SIZE_BYTES + this.baseWitness.getText().getLength();
        Runtime.getRuntime().gc();
        Runtime.getRuntime().runFinalization();
        LOG.info("HISTOGRAM ["+ estimatedByteUsage+"] ESTIMATED USAGE");
        LOG.info("HISTOGRAM ["+ Runtime.getRuntime().freeMemory()+"] ESTIMATED FREE");
        if (estimatedByteUsage > Runtime.getRuntime().freeMemory()) {
            setStatus(Status.SERVER_ERROR_INSUFFICIENT_STORAGE);
            return toTextRepresentation(
                "The server has insufficent resources to generate a histogram for this collation.");
        }
        
        final String taskId =  generateTaskId(set.getId(), baseWitness.getId() );
        if ( this.taskManager.exists(taskId) == false ) {
            HistogramTask task = new HistogramTask(taskId);
            this.taskManager.submit(task);
        } 
        return toJsonRepresentation( "{\"status\": \"RENDERING\", \"taskId\": \""+taskId+"\"}" );
    }
    
    private String generateTaskId( final Long setId, final Long baseId) {
        final int prime = 31;
        int result = 1;
        result = prime * result + setId.hashCode();
        result = prime * result + baseId.hashCode();
        return "histogram-"+result;
    }

    private void render() throws IOException, FileNotFoundException {
        // Algorithm: 
        // The histogram is an array of integers sized to match 
        // the length of the base document.
        // Get all diffs for the selected set.
        // Walk thru each, and get the base diff start and end position.
        // For each position in range from start to end, add 1 to 
        // the corresponding position in the histogram array.
        // start of by getting the base witness and the cout of all 
        // witnesses in this comparison set
        Set<Witness> witnesses = this.setDao.getWitnesses(this.set);
        int maxValue = witnesses.size();

        // Get all of the differences and apply the above algorithm
        QNameFilter changesFilter = this.filters.getDifferencesFilter();
        AlignmentConstraint constraints = new AlignmentConstraint(this.set, this.baseWitness.getId());
        constraints.setFilter(changesFilter);
        LOG.info("Create histogram buffer size " + (int)this.baseWitness.getText().getLength());
        byte[] histogram = createHistogram( (int)this.baseWitness.getText().getLength() );
        LOG.info( Runtime.getRuntime().freeMemory()+" bytes free after histogram buffer");
        List<Alignment> diffs =  this.alignmentDao.list(constraints);
        LOG.info( Runtime.getRuntime().freeMemory()+" bytes free after alignment retrieval");
        for (Alignment diff :  diffs ) {
            
            // get the base witness annotation
            AlignedAnnotation anno = diff.getWitnessAnnotation(this.baseWitness.getId());
            
            // mark of its range in the histogram
            int start = (int)anno.getRange().getStart();
            int end = (int)anno.getRange().getEnd();
            for (int i=start; i<end; i++) {
                histogram[i]++;
            }
        }
        diffs.clear();
        diffs = null;

        // scale to max value and dump to temp file
        File hist = File.createTempFile("histo", "data");
        hist.deleteOnExit();
        BufferedWriter bw = new BufferedWriter( new FileWriterWithEncoding(hist, "UTF-8") );
        try {
            boolean firstWrite = true;
            bw.write( "{\"baseName\": \""+this.baseWitness.getName()+"\", \"histogram\": [" );
            for ( int i=0;i<histogram.length;i++) {
                if ( firstWrite == false ) {
                    bw.write(",");
                }
                firstWrite = false;
                double scaled = (double)histogram[i]/(double)maxValue;
                if ( scaled > 1.0 ) {
                    scaled = 1.0;
                }
                bw.write(String.format("%1.2f",  scaled));
            }
            bw.write("] }");
        } catch (IOException e) {
            LOG.error("Unable to generate histogram", e);
            throw e;
        } finally {
            IOUtils.closeQuietly(bw);
        }
        
        // cache the results and kill temp file
        FileReader r = new FileReader(hist);
        this.cacheDao.cacheHistogram(this.set.getId(), this.baseWitness.getId(), r);
        IOUtils.closeQuietly(r);
        hist.delete();
    }
    
    /**
     * create and init histogram array with all zeros
     * @param size
     * @return
     */
    private byte[] createHistogram( long size ) {
        byte[] out = new byte[(int)size];
        for ( int i=0; i<size; i++) {
            out[i] = 0;
        }
        return out;
    }
    
    /**
     * Task to asynchronously render the visualization
     */
    private class HistogramTask implements BackgroundTask {
        private final String name;
        private BackgroundTaskStatus status;
        private Date startDate;
        private Date endDate;
        
        public HistogramTask(final String name) {
            this.name =  name;
            this.status = new BackgroundTaskStatus( this.name );
            this.startDate = new Date();
        }
        
        @Override
        public Type getType() {
            return BackgroundTask.Type.VISUALIZE;
        }
        
        @Override
        public void run() {
            try {
                LOG.info("Begin task "+this.name);
                this.status.begin();
                HistogramResource.this.render();
                LOG.info("Task "+this.name+" COMPLETE");
                this.endDate = new Date();   
                this.status.finish();
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
