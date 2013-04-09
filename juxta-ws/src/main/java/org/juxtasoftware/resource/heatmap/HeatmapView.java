package org.juxtasoftware.resource.heatmap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.output.FileWriterWithEncoding;
import org.apache.commons.lang.StringEscapeUtils;
import org.juxtasoftware.Constants;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.CacheDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaAnnotationDao;
import org.juxtasoftware.dao.NoteDao;
import org.juxtasoftware.dao.PageMarkDao;
import org.juxtasoftware.dao.UserAnnotationDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.AlignmentConstraint;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.QNameFilter;
import org.juxtasoftware.model.VisualizationInfo;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.resource.BaseResource;
import org.juxtasoftware.util.BackgroundTask;
import org.juxtasoftware.util.BackgroundTaskCanceledException;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.juxtasoftware.util.QNameFilters;
import org.juxtasoftware.util.TaskManager;
import org.juxtasoftware.util.ftl.FileDirective;
import org.juxtasoftware.util.ftl.HeatmapStreamDirective;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import eu.interedition.text.Range;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class HeatmapView  {
    @Autowired private CacheDao cacheDao;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private ComparisonSetDao setDao;    
    @Autowired private NoteDao noteDao;
    @Autowired private PageMarkDao pbDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private QNameFilters filters;
    @Autowired private HeatmapStreamDirective heatmapDirective;
    @Autowired private ApplicationContext context;
    @Autowired private TaskManager taskManager;
    @Autowired private Integer visualizationBatchSize;
    @Autowired private JuxtaAnnotationDao annotationDao;
    @Autowired private UserAnnotationDao userNoteDao;
    
    private BaseResource parent;
    private VisualizationInfo visualizationInfo;
    private List<SetWitness> witnesses;
    
    protected static final Logger LOG = LoggerFactory.getLogger( Constants.WS_LOGGER_NAME );

    /**
     * Delete all cached heatmap data for the specified set
     * @param set
     */
    public void delete( ComparisonSet set ) {
        this.cacheDao.deleteHeatmap(set.getId());
    }
    
    /**
     * Get the UTF-8, HTML representaton of a heatmap. This data will
     * be generated once and cached in the database. If the refresh
     * flag is truem the db cache will be wiped and a new representation
     * generated
     * 
     * @param set Comparison set used to generate the data
     * @refresh when true, the data will be cleared from cache and regenerated.
     * 
     * @return UTF-8 encoded, text/html representation of the set
     * @throws IOException 
     */
    public Representation toHtml( final BaseResource parent, final ComparisonSet set) throws IOException {
        
        // save a reference to the parent resource
        this.parent = parent;
        
        if (parent.getQuery().getValuesMap().containsKey("refresh") ) {
            this.cacheDao.deleteHeatmap(set.getId());
        }
               
        // Determine base witnessID from query params
        Long baseWitnessId = null;
        if (this.parent.getQuery().getValuesMap().containsKey("base")  ) {
            String baseId = this.parent.getQuery().getValues("base");
            baseWitnessId = Long.parseLong(baseId);
        }
        
        // pick out sorting settings
        String sortBy = "date";
        if (this.parent.getQuery().getValuesMap().containsKey("by")  ) {
            sortBy = this.parent.getQuery().getValues("by");
        }
        String sortOrder = "desc";
        if (this.parent.getQuery().getValuesMap().containsKey("order")  ) {
            sortOrder = this.parent.getQuery().getValues("order");
        }
        
        // Check if this visualization should be the condensed version
        boolean condensed = false;
        if (this.parent.getQuery().getValuesMap().containsKey("condensed")  ) {
            condensed = true;
        }
        
        // Get all witness 
        List<Witness> setWitnesses = new ArrayList<Witness>( this.setDao.getWitnesses(set) );
        if ( setWitnesses.size() < 2) {
            return this.parent.toTextRepresentation("This set contains less than two witnesess. Unable to view heatmap.");
        }
        
        // Set the base witness id to the first in the set of it was not specified
        if ( baseWitnessId == null ) {
            baseWitnessId = setWitnesses.get(0).getId();
        }
        
        // get the base witness AND its length
        Witness base = null;
        long baseLength = 0L;
        for ( Witness w : setWitnesses ) {
            if ( w.getId().equals(baseWitnessId) ) {
                base = w;
                
                // lookup the cached base length
                baseLength = this.setDao.getTokenzedLength(set, w);
                if ( baseLength == 0 ) {
                    LOG.error("Missing tokenized length of witness "+w.getId());
                    parent.setStatus(Status.SERVER_ERROR_INTERNAL);
                    return parent.toTextRepresentation("Missing length of base witness. Please re-collate.");
                }
                break;
            }
        }
        
        if ( base == null) {
            parent.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return parent.toTextRepresentation("The specified base witness was not found");
        }
        
        // get the witness filter list. these witnesses will not be rendered in the visualization.
        // if no filter witnesses are  present, all witnesses will be included. 
        // Use this to create a visualiztionInfo object that will be used to generate a key to uniquely identify
        // this visualization
        Set<Long> witFilterList = getWitnessFilterList( base.getId() );
        this.visualizationInfo = new VisualizationInfo(set, base, witFilterList);
        
        // Calculate the change index for the witnesses ( not necessary in condensed view: no witness list)
        try {
            // init witness list so all witnesses have change index of 0
            // this will be filled in from cache or during heatmap generation
            this.witnesses = new ArrayList<HeatmapView.SetWitness>();
            for (Witness w: setWitnesses ) {
                SetWitness sw = new SetWitness(w, baseLength, w.equals(base));
                sw.hasAnnotations = this.userNoteDao.hasUserAnnotations(set, sw.getId());
                this.witnesses.add(sw);
            }

            // Asynchronously render heatmap main body (map, notes and margin boxes)
            // Grab it from cache if possible. 
            if ( this.cacheDao.heatmapExists(set.getId(), this.visualizationInfo.getKey(), condensed) == false) {
                final String taskId =  generateTaskId(set.getId(), this.visualizationInfo.getKey(), condensed);
                if ( this.taskManager.exists(taskId) == false ) {
                    HeatmapTask task = new HeatmapTask(taskId, set, base, condensed);
                    this.taskManager.submit(task);
                } 
                return this.parent.toHtmlRepresentation( new StringReader("RENDERING "+taskId));
            }
                          
            // init FTL data map
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("hasNotes", false);
            map.put("hasBreaks", false);
            map.put("hasLineNumbers", false);
            
            // Last, wrap the body with ui (title, comparison set details)
            map.put("condensed", condensed );
            map.put("hasUserAnnotations", this.userNoteDao.hasUserAnnotations(set, base.getId()) );
            map.put("hasNotes", this.noteDao.hasNotes( base.getId() ) );
            map.put("hasBreaks", this.pbDao.hasBreaks( base.getId() ) );
            map.put("hasLineNumbers", this.pbDao.hasLineNumbers( base.getId() ) );
            map.put("hasRevisions", this.witnessDao.hasRevisions(base) );
            map.put("setId", set.getId());
            map.put("setTitle", set.getName());
            map.put("baseId", base.getId());
            map.put("visualizationKey", this.visualizationInfo.getKey());
            map.put("baseName", base.getName() );
            map.put("witnessCount", this.witnesses.size() );
            map.put("witnesses", this.witnesses );
            map.put("heatmapStreamer", this.heatmapDirective);  // stream the cached content into template
            map.put("witnessFilter", witFilterList.toString());
            map.put("page", "set");
            map.put("title", "Juxta Heatmap View: "+set.getName());
            map.put("sortBy", sortBy);
            map.put("sortOrder", sortOrder);
            
            // fill in the hidden spans with segments of urls so the
            // javascript can piece together URLs for all of
            // its ajax requests. This also becomes an extnsion point
            // for systems that embed heatmap views in the main ui. They
            // just provide alternate values for these 3 elemements
            map.put("ajaxBaseUrl", parent.getRequest().getHostRef().toString()+"/juxta/"+parent.getWorkspace()+"/set/");
            map.put("viewHeatmapSegment", "/view?mode=heatmap&");
            map.put("fragmentSegment", "/diff/fragment");
            map.put("annotateSegment", "/annotation");

            return this.parent.toHtmlRepresentation("heatmap.ftl", map);
        } catch ( OutOfMemoryError e ) {
            return this.parent.toTextRepresentation(
                "The server has insufficent resources to generate this visualization." +
                "\nTry again later. If this fails, try breaking large witnesses up into smaller segments.");
        }
    }

    private Set<Long> getWitnessFilterList( Long baseId ) {
        Set<Long> list = new HashSet<Long>();
        if ( this.parent.getQuery().getValuesMap().containsKey("filter")  ) {
            String[] docStrIds = this.parent.getQuery().getValues("filter").split(",");
            for ( int i=0; i<docStrIds.length; i++ ) {
                Long witId = Long.parseLong(docStrIds[i]);
                if ( witId.equals(baseId) == false ) {
                    list.add( witId );
                }
            }
        }
        return list;
    }

    private String generateTaskId( final Long setId, final Long baseId, final Boolean condensed) {
        final int prime = 31;
        int result = 1;
        result = prime * result + setId.hashCode();
        result = prime * result + baseId.hashCode();
        result = prime * result + condensed.hashCode();
        return "heatmap-"+result;
    }
    
    private void renderHeatMap(BackgroundTaskStatus status, ComparisonSet set, Witness base, boolean condensed) throws IOException {
               
        LOG.info("Rendering heatmap for "+set);
        
        // get a list of revisons, differeces, notes and breaks in ascending oder.
        // add this information to injectors that will be used to inject
        // supporting markup into the witness heatmap stream
        final ChangeInjector changeInjector = this.context.getBean(ChangeInjector.class);
        changeInjector.setWitnessCount( this.witnesses.size() );
        changeInjector.initialize( generateHeatmapChangelist(status, set, base ) );

        final NoteInjector noteInjector = this.context.getBean(NoteInjector.class);
        noteInjector.initialize( this.noteDao.find(base.getId()) );
        
        final PageMarkInjector pbInjector = this.context.getBean(PageMarkInjector.class);
        pbInjector.initialize( this.pbDao.find(base.getId()) );
        
        final RevisionInjector revisionInjector = this.context.getBean(RevisionInjector.class);
        revisionInjector.initialize( this.witnessDao.getRevisions(base) );
        
        // create a temp file in which to assemble the heatmap data
        File heatmapFile = File.createTempFile("heatmap", ".dat");
        heatmapFile.deleteOnExit();
        BufferedWriter br = new BufferedWriter( new FileWriterWithEncoding(heatmapFile, "UTF-8") );
        
        // get the content stream and generate the data....
        Reader reader = this.witnessDao.getContentStream(base);
        
        // now the fun bit; stream the base content and mark it
        // up with heat map goodness using the injectors defined above.
        // each injector compares the current doc position to its internal
        // data. when appropriate, html markup an/or content is added.
        int pos = 0;
        StringBuilder line = new StringBuilder();
        boolean done = false;
        while ( done == false ) {
            status.setNote("Writing results: "+Math.round(pos/base.getText().getLength()*100)+"% Complete");
            int data = reader.read();
            if ( data == -1 ) {
                done = true;
            }             
            
            // as long as any injectors hav content to stuff
            // into the document at this position, kepp spinning
            while ( revisionInjector.hasContent(pos) ||
                    pbInjector.hasContent(pos) ||
                    noteInjector.hasContent(pos) ||
                    changeInjector.hasContent(pos) ) { 
                
                // inject heatmap markup into the basic 
                // witness data stream. put revsions first so their markup
                // wraps all others.
                revisionInjector.injectContentStart(line, pos);
                pbInjector.injectContentStart(line, pos);
                noteInjector.injectContentStart(line, pos);
                changeInjector.injectContentStart(line, pos);  
    
                // now see if any of this injected data needs to be closed
                // off. This must be done in reverse order of the above calls
                // to avoid interleaving of tags
                changeInjector.injectContentEnd(line, pos); 
                noteInjector.injectContentEnd(line, pos);
                pbInjector.injectContentEnd(line, pos); 
                revisionInjector.injectContentEnd(line, pos);
            }
  
            
            // once a newline is reached write it to the data file
            if ( data == '\n' || data == -1 ) {
                line.append("<br/>");
                br.write(line.toString());
                br.newLine();
                line = new StringBuilder();
            } else {
                // escape the text before appending it to the output stream
                line.append( StringEscapeUtils.escapeHtml( Character.toString((char)data) ) );
            }
            pos++;
        }
        
        // append any unanchored notes that trail the end of doc
        if ( noteInjector.addTrailingNotes(line) ) {
            br.write(line.toString());
        }
        
        // close up the file
        br.close();
        
        // create the template map and stuff it with some basic data
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("condensed", condensed );
        map.put("baseName", base.getName());
        map.put("srcFile", heatmapFile.getAbsoluteFile());
        map.put("fileReader", new FileDirective());   
        map.put("numWitnesses", this.witnesses.size()-1);
        map.put("notes", noteInjector.getData() );
        map.put("changeIndexes", generateChangeIndexJson() );
        
        // create the main body of the heatmap. NOTE the false params.
        // The first one tells the base NOT to use the layout template when generating
        // the representation. The second tells it NOT to GZIP the results
        Representation heatmapFtl = this.parent.toHtmlRepresentation("heatmap_text.ftl", map, false, false);
                
        // Stuff it in a cache for future fast response
        this.cacheDao.cacheHeatmap(set.getId(), this.visualizationInfo.getKey(), heatmapFtl.getReader(), condensed);
        
        // done with this file. kill it explicitly
        heatmapFile.delete();
        LOG.info("Hetmap rendered for "+set);
    } 
    
    private String generateChangeIndexJson() {
        StringBuilder sb = new StringBuilder("[");
        for ( SetWitness sw : this.witnesses) {
            if ( sb.length() > 1 ) {
                sb.append(",");
            }
            sb.append( "{\"id\":").append(sw.getId()).append(",");
            sb.append("\"ci\":").append("\"").append(String.format("%.2f", sw.getChangeIndex()) ).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private List<Change> generateHeatmapChangelist(BackgroundTaskStatus status, final ComparisonSet set, final Witness base) throws IOException {
        
        // init 
        List<Integer> zeroLen = new ArrayList<Integer>();
        int len = (int)base.getText().getLength();
        byte[] heat = new byte[len];
        for (int i = 0; i < len; i++) {
            heat[i] = 0;
        }
        
        // generate heat map data 1 pair at a time
        for (SetWitness wit : this.witnesses) {
            if (wit.getId().equals(base.getId())) {
                continue;
            }
            if (this.visualizationInfo.getWitnessFilter().contains(wit.getId()) ) {
                LOG.info("Skipping filtered witness "+wit.getName()+"["+wit.getId()+"]");
                continue;
            }
            
            LOG.info("Generate heatmap data for " + base + " vs " + wit);
            status.setNote("Rendering  "+ base + " vs " + wit);
            boolean done = false;
            int startIdx = 0;
            while ( !done ) {
                List<Alignment> aligns = getPairAlignments(set, base.getId(), wit.getId(), startIdx, this.visualizationBatchSize);
                if ( aligns.size() < this.visualizationBatchSize ) {
                    done = true;
                } else {
                    startIdx += this.visualizationBatchSize;
                }
    
                // generate a change list based on the sorted differences
                int lastGroupId = -1;
                int lastEnd = -1;
                for (Alignment align : aligns) {
                    // the heatmap is from the perspective of the BASE
                    // text, so only care about annotations that refer to it
                    AlignedAnnotation baseAnno = align.getWitnessAnnotation(base.getId());  
                    AlignedAnnotation witAnnotation = align.getWitnessAnnotation(wit.getId());  
                    
                    // accumulate total diff length for this witness. it will be used to render the
                    // change index. always add on the longest diff.
                    long longestDiff = Math.max(baseAnno.getRange().length(), witAnnotation.getRange().length());
                    wit.addDiffLen(longestDiff);
   
                    // Save off the start and end positions of the annotaion on the base
                    int start = (int)baseAnno.getRange().getStart();
                    int end = (int)baseAnno.getRange().getEnd();

                    // see if this has matching group to prior
                    // change. if so, extend the end of that to match the
                    // start of this. keeps heatmap contiguous
                    if ( lastGroupId > -1 ) {
                        if ( lastGroupId == align.getGroup() ) {
                            for ( int i=lastEnd;i<start;i++) {
                                heat[i]++;
                            }
                        }
                    }
                    
                    if ( baseAnno.getRange().length() == 0 ) {
                        Integer pos = (int)baseAnno.getRange().getStart();
                        if ( (long)pos > lastEnd ) {
                            zeroLen.add(pos);
                        }
                    } else {
                        // ... now add the data to the byte array
                        for ( int i=start; i<end;i++) {
                            heat[i]++;
                        }
                    }
                    
                    lastEnd = end;
                    lastGroupId = align.getGroup();
                }
            }
        }
            
        // Convert the byte heat array to a list of changes
        List<Change> changes = new ArrayList<Change>();
        byte lastVal = 0;
        int changeStart = 0;
        int changeId= 1;
        for (int i = 0; i < len; i++) {
            if ( heat[i] == 0 ) {
                if ( lastVal != 0 ) {
                    changes.add( new Change(changeId++, new Range(changeStart,i),lastVal));
                    changeStart = i;
                    lastVal = heat[i];
                }
            } else {
                if ( heat[i] != lastVal ) {
                    if ( lastVal != 0 ) {
                        changes.add( new Change(changeId++, new Range(changeStart,i),lastVal));
                        changeStart = i;
                        lastVal = heat[i];
                    } else {
                        changeStart = i;
                        lastVal = heat[i];
                    }
                }
            }
        }
        
        // overlay adds
        Collections.sort(zeroLen);
        for (Integer pos : zeroLen ) {
            boolean handled = false;
            for ( Change c : changes ) {
                if ( (long)pos >= c.getRange().getStart() && (long)pos <= c.getRange().getEnd() ) {
                    handled  = true;
                    c.increaseDiffFrequency();
                    break;
                }
            }
            
            if ( handled == false ) {
                Change c =  new Change(changeId++, new Range(pos, pos), 1);
                int adjPos = (int)this.annotationDao.findNextTokenStart(base.getId(), pos);
                if ( adjPos == -1 || adjPos+1 >= base.getText().getLength() ) {
                    c.adjustRange(pos-1, pos);
                } else {
                    // first, see if there are any more tokens after this point
                    int nextPos = (int)this.annotationDao.findNextTokenStart(base.getId(), adjPos);
                    if ( nextPos == -1 ) {
                        // this was the last token. Don't highlight past it, just up to it.
                        // this fixes a special case: punct is not ignored and last non-whitespace char
                        // in witness is punct and there is an add relative to this position. This code
                        // makes sure the last punctuation gets highlighted as a change
                        c.adjustRange(pos, adjPos);
                    } else {
                        c.adjustRange(adjPos, adjPos+1);
                    }
                }
                changes.add(c );
                Collections.sort(changes);
            }
        }
        
        return changes;
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
    
    /**
     * Extension of a witness to include change index data
     * @author loufoster
     *
     */
    public static final class SetWitness extends Witness  {
        private long totalDiffLen;
        private final long baseLen;
        private final boolean isBase;
        private final long createTimestamp;
        private boolean hasAnnotations = false;
        
        public SetWitness( Witness w, long baseLen, boolean isBase) {
            super(w);
            this.isBase = isBase;
            this.baseLen = baseLen;
            this.totalDiffLen = 0;
            this.createTimestamp = w.getCreated().getTime();
        }
        void addDiffLen( long longestDiff ) {
            this.totalDiffLen += longestDiff;
        }
        public float getChangeIndex() {
            return (float)this.totalDiffLen / (float)this.baseLen;
        }
        public boolean isBase() {
            return this.isBase;
        }
        public long getDate() {
            return this.createTimestamp;
        }
        public boolean getHasAnnotations() {
            return this.hasAnnotations;
        }
    }
    
    /**
     * Task to asynchronously render the visualization
     */
    private class HeatmapTask implements BackgroundTask {
        private final String name;
        private BackgroundTaskStatus status;
        private final ComparisonSet set;
        private final Witness base;
        private final boolean condensed;
        private Date startDate;
        private Date endDate;
        
        public HeatmapTask(final String name, final ComparisonSet set, final Witness base, boolean condensed) {
            this.name =  name;
            this.status = new BackgroundTaskStatus( this.name );
            this.set = set;
            this.base = base;
            this.condensed = condensed;
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
                HeatmapView.this.renderHeatMap(this.status, set, base, condensed);
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
