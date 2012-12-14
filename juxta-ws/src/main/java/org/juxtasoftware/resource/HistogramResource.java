package org.juxtasoftware.resource;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.FileWriterWithEncoding;
import org.juxtasoftware.Constants;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.CacheDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.AlignmentConstraint;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.QNameFilter;
import org.juxtasoftware.model.VisualizationInfo;
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

import eu.interedition.text.Range;

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
    @Autowired private Integer visualizationBatchSize;

    private ComparisonSet set;
    private Witness baseWitness;
    private Set<Long> witnessFilterList;

    protected static final Logger LOG = LoggerFactory.getLogger(Constants.WS_LOGGER_NAME);

    @Override
    protected void doInit() throws ResourceException {

        super.doInit();

        Long id = getIdFromAttributes("id");
        if (id == null) {
            return;
        }
        this.set = this.setDao.find(id);
        if (validateModel(this.set) == false) {
            return;
        }

        // Get the required base witness ID
        if (getQuery().getValuesMap().containsKey("base") == false) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing base parameter");
        } else {
            String baseIdStr = getQuery().getValues("base");

            Long baseId = null;
            try {
                baseId = Long.parseLong(baseIdStr);
            } catch (NumberFormatException e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid base witness id");
                return;
            }
            this.baseWitness = this.witnessDao.find(baseId);
            if (validateModel(this.baseWitness) == false) {
                return;
            }
        }

        // grab the witness id filter. If provided, only these witnesses
        // will be included in the histogram.
        Set<Long> includeIdList = new HashSet<Long>();
        if (getQuery().getValuesMap().containsKey("docs")) {
            String[] docStrIds = getQuery().getValues("docs").split(",");
            for (int i = 0; i < docStrIds.length; i++) {
                try {
                    Long witId = Long.parseLong(docStrIds[i]);
                    if (witId.equals(this.baseWitness.getId()) == false) {
                        includeIdList.add(witId);
                    }
                } catch (Exception e) {
                    setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid document id specified");
                    return;
                }
            }
        }

        // invert the include list into an exclude filter
        this.witnessFilterList = new HashSet<Long>();
        if (includeIdList.size() > 0) {
            for (Witness w : this.setDao.getWitnesses(this.set)) {
                if (includeIdList.contains(w.getId()) == false && w.equals(this.baseWitness) == false) {
                    this.witnessFilterList.add(w.getId());
                }
            }
        }
    }

    @Get("json")
    public Representation toJson() throws IOException {
        // Create the info block that identifies this vsualization
        VisualizationInfo info = new VisualizationInfo(this.set, this.baseWitness, this.witnessFilterList);

        // FIRST, see if the cached version is available:
        LOG.info("Is histogram cached: " + info);
        if (this.cacheDao.histogramExists(this.set.getId(), info.getKey())) {
            LOG.info("Retrieving cached histogram");
            Representation rep = new ReaderRepresentation(this.cacheDao.getHistogram(this.set.getId(), info.getKey()),
                MediaType.APPLICATION_JSON);
            if (isZipSupported()) {
                return new EncodeRepresentation(Encoding.GZIP, rep);
            } else {
                return rep;
            }
        }

        final String taskId = generateTaskId(set.getId(), baseWitness.getId());
        if (this.taskManager.exists(taskId) == false) {
            HistogramTask task = new HistogramTask(taskId, info);
            this.taskManager.submit(task);
        }
        return toJsonRepresentation("{\"status\": \"RENDERING\", \"taskId\": \"" + taskId + "\"}");
    }

    private void render(VisualizationInfo histogramInfo) throws IOException {

        // init
        int[] histogram = createHistogram();
        boolean[] addTracker = createAddTracker();
        double baseLen = this.baseWitness.getText().getLength();
        List<Alignment> diffs = null;

        // grab diff info in pairs to keep memory needs low
        for (Witness w : this.setDao.getWitnesses(this.set)) {
            if (this.witnessFilterList.contains(w.getId())) {
                LOG.info("Skipping filtered witness " + w.getName() + "[" + w.getId() + "]");
                continue;
            }
            if (w.getId().equals(this.baseWitness.getId())) {
                // base will be included in each constraint pair below,
                // so it can be skipped here
                continue;
            }
            
            boolean done = false;
            int startIdx = 0;
            while ( !done ) {
                diffs = getPairAlignments(this.set, this.baseWitness.getId(), w.getId(), startIdx, this.visualizationBatchSize);
                if ( diffs.size() < this.visualizationBatchSize ) {
                    done = true;
                } else {
                    startIdx += this.visualizationBatchSize;
                }
                
                for ( Alignment diff : diffs ) {
                    AlignedAnnotation baseAnno = diff.getWitnessAnnotation(this.baseWitness.getId());
                    Range baseRange = baseAnno.getRange();
                    
                    // for each pos in the range, determine percent doc position
                    // add to the histogram at this percentage
                    for ( long pos = baseRange.getStart(); pos <=baseRange.getEnd(); pos++) {
                        float rawPercent = (float)pos / (float)baseLen;
                        rawPercent = rawPercent * 100.0f;
                        int percent = Math.round( rawPercent );
                        percent = Math.min(99, percent);
                        //System.err.println("Pos "+pos+" of total len "+baseLen+ " is percent "+rawPercent+":"+percent);
                        if (baseRange.length() == 0 && diff.getEditDistance() == -1) {
                            //System.err.println("This is an ADD");
                            addTracker[percent] = true;
                        } else {
                            histogram[percent]++;
                        }
                    }
                }
            }
        }

        // find the percentage with the most change, and save that change amount
        int maxVal = -1;
        for (int i = 0; i < 100; i++) {
            if (histogram[i] > maxVal) {
                maxVal = histogram[i];
            }
        }

        // scale to max value and dump to temp file
        File hist = File.createTempFile("histo", "data");
        hist.deleteOnExit();
        BufferedWriter bw = new BufferedWriter(new FileWriterWithEncoding(hist, "UTF-8"));
        try {
            boolean firstWrite = true;
            bw.write("{\"baseName\": \"" + this.baseWitness.getJsonName() + "\", \"histogram\": [");
            for (int i = 0; i < histogram.length; i++) {
                if (firstWrite == false) {
                    bw.write(",");
                }
                firstWrite = false;
                if (maxVal > 0) {
                    double val = (double) histogram[i];
                    double scaled = val / (double) maxVal;
                    if (addTracker[i]) {
                        scaled += 0.025;
                    }
                    if (scaled > 1.0) {
                        scaled = 1.0;
                    }
                    bw.write(String.format("%1.2f", scaled));
                } else {
                    if (addTracker[i]) {
                        bw.write("0.025");
                    } else {
                        bw.write("0.00");
                    }
                }
            }
            bw.write("] }");
        } catch (IOException e) {
            LOG.error("Unable to generate histogram", e);
            throw e;
        } finally {
            IOUtils.closeQuietly(bw);
        }

        // cache the results and kill temp file
        LOG.info("Cache histogram " + histogramInfo);
        FileReader r = new FileReader(hist);
        this.cacheDao.cacheHistogram(this.set.getId(), histogramInfo.getKey(), r);
        IOUtils.closeQuietly(r);
        hist.delete();
    }

    private List<Alignment> getPairAlignments(final ComparisonSet set, final Long baseId, final Long witnessId, int startIdx, int batchSize) {
        QNameFilter changesFilter = this.filters.getDifferencesFilter();
        AlignmentConstraint constraints = new AlignmentConstraint(set);
        constraints.addWitnessIdFilter(baseId);
        constraints.addWitnessIdFilter(witnessId);
        constraints.setFilter(changesFilter);
        constraints.setResultsRange(startIdx, batchSize);
        return this.alignmentDao.list(constraints);
    }

    private String generateTaskId(final Long setId, final Long baseId) {
        final int prime = 31;
        int result = 1;
        result = prime * result + setId.hashCode();
        result = prime * result + baseId.hashCode();
        return "histogram-" + result;
    }

    /**
     * create and init histogram array with all zeros
     * @param size
     * @return
     */
    private int[] createHistogram() {
        int[] out = new int[100];
        for (int i = 0; i < 100; i++) {
            out[i] = 0;
        }
        return out;
    }

    private boolean[] createAddTracker() {
        boolean[] out = new boolean[100];
        for (int i = 0; i < 100; i++) {
            out[i] = false;
        }
        return out;
    }

    /**
     * Task to asynchronously render the visualization
     */
    private class HistogramTask implements BackgroundTask {
        private final String name;
        private final VisualizationInfo histogramInfo;
        private BackgroundTaskStatus status;
        private Date startDate;
        private Date endDate;

        public HistogramTask(final String name, VisualizationInfo info) {
            this.name = name;
            this.status = new BackgroundTaskStatus(this.name);
            this.startDate = new Date();
            histogramInfo = info;
        }

        @Override
        public Type getType() {
            return BackgroundTask.Type.VISUALIZE;
        }

        @Override
        public void run() {
            try {
                LOG.info("Begin task " + this.name);
                this.status.begin();
                HistogramResource.this.render(this.histogramInfo);
                LOG.info("Task " + this.name + " COMPLETE");
                this.endDate = new Date();
                this.status.finish();
            } catch (IOException e) {
                LOG.error(this.name + " task failed", e.toString());
                this.status.fail(e.toString());
                this.endDate = new Date();
            } catch (BackgroundTaskCanceledException e) {
                LOG.info(this.name + " task was canceled");
                this.endDate = new Date();
            } catch (Exception e) {
                LOG.error(this.name + " task failed", e);
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
