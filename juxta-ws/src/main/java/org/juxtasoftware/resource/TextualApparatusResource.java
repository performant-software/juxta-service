package org.juxtasoftware.resource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.IOUtils;
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
import org.juxtasoftware.util.RangedTextReader;
import org.juxtasoftware.util.TaskManager;
import org.juxtasoftware.util.ftl.FileDirective;
import org.juxtasoftware.util.ftl.FileDirectiveListener;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.interedition.text.Range;

/**
 * Resource used to create a Textual Apparatus based on a set.
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class TextualApparatusResource extends BaseResource implements FileDirectiveListener {

    private enum Format { HTML, RTF };

    
    @Autowired private ComparisonSetDao setDao;
    @Autowired private QNameFilters filters;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private CacheDao cacheDao;
    @Autowired private TaskManager taskManager;
    @Autowired private Integer visualizationBatchSize;
    
    private ComparisonSet set;
    private Long baseWitnessId;
    private Format format;
    private Integer lineFrequency;
    private List<TaWitness> witnesses = new ArrayList<TaWitness>();   
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        Long id = getIdFromAttributes("id");
        if ( id == null ) {
            return;
        }
        this.set = this.setDao.find(id);
        if ( validateModel(this.set) == false ) {
            return;
        }
    }
    
    /**
     * Create the textual apparatus based on settings present in the JSON payload.
     * Expected format:
     * { 
     *     format: html|rtf,
     *     lineNumFrequency: 5,
     *     witnesses: [
     *       { id: id, include: true|false, base: true|false, siglum: name }, ...
     *     ]
     * }
     * @throws IOException 
     */
    @Post("json")
    public Representation create( final String jsonData) throws IOException {
        if ( this.set.getStatus().equals(ComparisonSet.Status.COLLATED) == false ) {
            setStatus(Status.CLIENT_ERROR_CONFLICT);
            return toTextRepresentation("Unable to generate textual apparatus - set is not collated");
        }
               
        // parse the config 
        JsonParser p = new JsonParser();
        JsonObject jsonObj = p.parse(jsonData).getAsJsonObject();
        this.format = Format.valueOf(jsonObj.get("format").getAsString().toUpperCase());
        if ( this.format == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Invalid output format specified. Only HTML and RTF are acceptable.");
        }
        this.lineFrequency = jsonObj.get("lineFrequency").getAsInt();
        
        JsonArray jsonWits = jsonObj.get("witnesses").getAsJsonArray();
        for ( Iterator<JsonElement>  itr = jsonWits.iterator(); itr.hasNext(); ) {
            JsonObject witInfo = itr.next().getAsJsonObject();
            boolean included = witInfo.get("include").getAsBoolean();
            boolean isBase = witInfo.get("base").getAsBoolean();
            Long witId = witInfo.get("id").getAsLong();
            String siglum = witInfo.get("siglum").getAsString();
            
            if ( isBase && this.baseWitnessId != null ) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Only one witness can be set as base.");
            }
            
            if ( isBase ) {
                this.baseWitnessId = witId;
            }
            
            Witness w = this.witnessDao.find(witId);
            if ( w == null ) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Invalid witness specified.");
            }
            
            if ( this.setDao.isWitness(this.set, w) == false ) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Invalid witness specified.");
            }
            
            if ( included ) {
                this.witnesses.add( new TaWitness(witId, siglum, w.getName(), isBase));
            } else {
                this.witnesses.add( new TaWitness(witId));
            }
        }
        
        if ( this.baseWitnessId == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("No base witness has been specified.");
        }
        if ( this.witnesses.size() < 2 ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("At least 2 witnesses must be included.");
        }
        
        // generate a hash for the config and see if the data is cached
        final int configHash =  generateConfigHash();
//        if ( this.cacheDao.textualApparatusExists(this.set.getId(), configHash)) {
//            // TODO
//        }
        
        // kick off a task to render the apparatus
        final String taskId =  "textualapp"+configHash;
//        if ( this.taskManager.exists(taskId) == false ) {
//            CaTask task = new CaTask(taskId);
//            this.taskManager.submit(task);
//        } 
//        return toJsonRepresentation( "{\"status\": \"RENDERING\", \"taskId\": \""+taskId+"\"}" );
        return render();
    }
    

    @Override
    public void fileReadComplete(File file) {
        file.delete();
    }
    
    private int generateConfigHash() {
        final int prime = 31;
        int result = 1;
        result = prime * result + this.set.getId().hashCode();
        result = prime * result + this.baseWitnessId.hashCode();
        result = prime * result + this.format.hashCode();
        result = prime * result + this.lineFrequency.hashCode();
        result = prime * result + this.witnesses.hashCode();
        return result;
    }

    private Representation render() throws IOException {
        // stream contents to text file. this will be read into the template below
        // during this process, convert the lines into a table and append
        // line numbering as needed. Further, track the range of each line.
        // This will be used to get correct line numbering info on the apparatus
        File baseTxt = File.createTempFile("base", "txt");
        baseTxt.deleteOnExit();
        Witness base = this.witnessDao.find(this.baseWitnessId);
        BufferedReader rdr = new BufferedReader( this.witnessDao.getContentStream(base));
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(baseTxt), "UTF-8");
        int lineNum = 1;
        int currPos = 0;
        List<Range> lineRanges = new ArrayList<Range>();
        while ( true ) {
            String line = rdr.readLine();
            if ( line == null ) {
                break;
            } else {
                lineRanges.add( new Range(currPos, currPos+line.length()) );
                currPos += line.length()+1;
                StringBuilder lb = new StringBuilder("<tr><td class=\"num-col\">");
                    
                if ( lineNum % this.lineFrequency == 0 ) {
                    lb.append(lineNum);
                } 
                lb.append("</td><td>").append(line).append("</td></tr>\n");

                lineNum++;
                osw.write(lb.toString());
            }
        }
        osw.close();
        rdr.close();
        
        File appFile = generateApparatus(lineRanges);
        
        // populate template map and generate HTML
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("title", "Textual Apparatus for Comparison Set \""+this.set.getName()+"\"");
        map.put("witnesses", this.witnesses);
        map.put("baseWitnessText", baseTxt.getAbsoluteFile());
        map.put("apparatusFile", appFile.getAbsoluteFile());
        FileDirective fd = new FileDirective();
        fd.setListener(this);
        map.put("fileReader", fd);  
        
        Representation rep = this.toHtmlRepresentation("textual_apparatus.ftl", map, false);
        
        return rep;
    }
    
    private File generateApparatus(List<Range> lineRanges) throws IOException {
        File appFile = File.createTempFile("app", "txt");
        appFile.deleteOnExit();
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(appFile), "UTF-8");

        // collect/merge all of the differences into a variant list
        List<Variant> variants = generateVariantList();
        
        // build the variant table one line at a time and stream it out to a file
        final String baseSiglum = getSiglum(this.baseWitnessId );
        String priorBaseTxt = null;
        Set<String> priorWitsWithChange = new HashSet<String>();
        for (Variant v : variants) {
            
            // First, grab the base witness fragment and add it to the variant string
            String baseTxt = "";
            boolean additionToBase = false;
            if ( v.getRange().length() > 0 ) {
                baseTxt = getWitnessText(this.baseWitnessId, v.getRange());
            } else {
                // if base len is 0, this means that a witness added text relative to
                // the base. grab the two words from the base text to the left and right of
                // this addition to bookend the change. These two words will also
                // be appeneded on the ends of the witness text to make it clear that
                // the witness added content between them. IMPORTANT: this reach ahead/behind
                // opens the possibity of NESTED changes (one witness has a difference from base 
                // in one of these words). To balance this out, always keep track of the prior
                // set of base text and witnesses with diffs. When building the list of
                // sigla that are the same as base, this prior info will be consulted.
                additionToBase = true;
                baseTxt = getAdditionContext(this.baseWitnessId, v.getRange().getStart());
            }
            
            // Start the variant string. It is the base text followed by ]. 
            StringBuilder sb = new StringBuilder(baseTxt).append("] ");
            
            // walk thru each of the witnesses that have differences from
            // the base at this particular range. Extract the variant text.
            boolean nestedChange = false;
            Map<String,Set<String>> txtSiglumMap = new HashMap<String,Set<String>>();
            for (Entry<Long, Range> ent : v.getWitnessRangeMap().entrySet()) {
                Long witId = ent.getKey();
                Range witRng = ent.getValue();
                String witTxt = "";
                if ( witRng.length() > 0 ) {
                    witTxt = getWitnessText(witId, witRng);
                    if ( additionToBase ) {
                        // Since this is an addition relative to the base, bookend the
                        // witness text with the two words from the base. this makes
                        // it clear that base and witness share those 2 words, but witness
                        // added the content in the middle.
                        String[] bits = baseTxt.split(" ");
                        witTxt = bits[0] + " " + witTxt + " "+bits[1];
                        if ( bits[0].equals(priorBaseTxt)) {
                            // the first part of the bookend text was already
                            // handled by the prior line of the apparatus. flag
                            // it here so the witnesss wont get double counted. maybe
                            nestedChange = true;
                        }
                    }
                } else {
                    witTxt = "<i>not in </i>";
                }
                
                // accumulate the witness text fragments in a map
                // that can associate them with multiple witnesses
                final String witSiglum = getSiglum(witId);
                if ( txtSiglumMap.containsKey(witTxt)) {
                    txtSiglumMap.get(witTxt).add(witSiglum);
                } else {
                    Set<String> sigla = new HashSet<String>();
                    sigla.add(witSiglum);
                    txtSiglumMap.put(witTxt, sigla);
                }
            }
            
            // add any witnesses that are NOT accounted for in the txtSiglumMap
            // these are witnesses that are the same as the base. add their siglum
            // to the witness list following the base fragment
            addWitnessesMatchingBase(sb, txtSiglumMap, nestedChange, priorWitsWithChange);
            
            // end the base portion of the variant report
            sb.append("; ");
            
            // Lastly, use the merged data from above to create the witness variants
            for (Entry<String, Set<String>> ent : txtSiglumMap.entrySet() ) {
                String witTxt = ent.getKey();
                StringBuilder ids = new StringBuilder();
                for ( String siglum: ent.getValue() ) {
                    if ( ids.length() > 0 ) {
                        ids.append(", ");
                    }
                    ids.append(siglum);
                }
                sb.append(witTxt);
                if  ( witTxt.equals("<i>not in </i>") == false ) {
                    sb.append(": ");
                }
                sb.append(ids).append("; ");
            }
            
            // find line num for range
            String lineRange = findLineNumber(v.getRange(), lineRanges);
  
            // shove the whole thing into a table row and wroite it out to disk
            final String out = "<tr><td class=\"num-col\">"+lineRange+"</td><td>"+sb.toString()+"</td></tr>\n";
            osw.write(out);
            
            // save priors to detect special cases
            priorBaseTxt = baseTxt;
            priorWitsWithChange.clear();
            for (  Set<String> sigla : txtSiglumMap.values()) {
                priorWitsWithChange.addAll(sigla);
            }
        }
        
        IOUtils.closeQuietly(osw);
        return appFile;
    }
    
    private void addWitnessesMatchingBase(StringBuilder variantSb, Map<String, Set<String>> txtSiglumMap, 
                                          boolean nestedChange, Set<String> priorWitsWithChange) {
        
        // merge all of the sigla entries into one list
        Set<String> witsWithDiffs = new HashSet<String>();
        for ( Set<String> s : txtSiglumMap.values()) {
            witsWithDiffs.addAll(s);
        }
        
        // if a witness is NOT in the diff list, it has the same
        // text as the base. Add it to the sigla list associated
        // associated with the base text
        StringBuilder sameAsBase = new StringBuilder();
        for ( TaWitness w : this.witnesses ) {
            boolean alreadyHandled = ( nestedChange && priorWitsWithChange.contains(w.siglum));
            if ( witsWithDiffs.contains(w.siglum) == false && alreadyHandled == false ) {
                if ( sameAsBase.length() > 0) {
                    sameAsBase.append(", ");
                }
                sameAsBase.append(w.siglum);
            }
        }
        variantSb.append(sameAsBase);
    }

    private List<Variant> generateVariantList() {
        boolean done = false;
        int startIdx = 0;
        List<Variant> variants = new ArrayList<Variant>();
        while ( !done ) {
            List<Alignment> aligns = getAlignments(startIdx, this.visualizationBatchSize);
            if ( aligns.size() < this.visualizationBatchSize ) {
                done = true;
            } else {
                startIdx += this.visualizationBatchSize;
            }
            
            // create report based on alignments
            for (Alignment align : aligns) {
                
                // get base and witness annotations
                AlignedAnnotation baseAnno = align.getWitnessAnnotation(this.baseWitnessId);
                AlignedAnnotation witAnno = null;
                for ( AlignedAnnotation a  : align.getAnnotations()) {
                    if ( a.getWitnessId().equals(this.baseWitnessId) == false ) {
                        witAnno = a;
                        break;
                    }
                }
                
                // look up or create a variant for the current base range
                Variant variant = null;
                for ( Variant v : variants ) {
                    if ( v.getRange().equals(baseAnno.getRange())) {
                        variant = v;
                        break;
                    }
                    
                    // once this range start occurs past the current base range end,
                    // all of the remaining ranges will be further in the doc. stop now
                    if ( v.getRange().getStart() > baseAnno.getRange().getEnd() ) {
                        break;
                    }
                }
                if ( variant == null ) {
                    variant = new Variant(baseAnno.getRange(), align.getGroup());
                    variants.add(variant);
                }
                
                // add the witness info
                variant.addWitnessDetail(witAnno.getWitnessId(), witAnno.getRange(), align.getGroup());
            }
            
            // merge related variants
            Variant prior = null;
            for (Iterator<Variant> itr = variants.iterator(); itr.hasNext();) {
                Variant variant = itr.next();
                if (prior != null) {
                    // See if these are a candidate to merge
                    if (variant.hasMatchingGroup(prior) && variant.hasMatchingWitnesses(prior)) {
                        prior.merge(variant);
                        itr.remove();
                        continue;
                    }
                }

                prior = variant;
            }
        }
        return variants;
    }

    private String getAdditionContext(final Long witId, final long addPos) {
        final int defaultSize = 20;
        int contextSize = defaultSize;
        if ( addPos == 0 ) {
            String witTxt = getWitnessText(witId, new Range(0, contextSize)).trim();
            return witTxt.substring(0, witTxt.indexOf(' '));
        }
        contextSize = Math.min( defaultSize, (int)addPos);
        String witTxt = getWitnessText(witId, new Range(addPos-contextSize, addPos+contextSize)).trim();
        String before = witTxt.substring(0,contextSize).trim();
        String wb = before.substring(before.lastIndexOf(' ')).trim();
        String after = witTxt.substring(contextSize).trim();
        String wa = after;
        if ( after.indexOf(' ') > -1 ) {
            wa = after.substring(0, after.indexOf(' ')).trim();
        }
        witTxt = wb+" "+wa;
        return witTxt;
    }
    
    private String findLineNumber(Range tgtRange, List<Range> lineRanges) {
        int lineStart = -1;
        int lineEnd = -1;
        int lineNum = 1;
        for ( Range r : lineRanges ) {
            if ( r.getStart() > tgtRange.getEnd() ) {
                return "";
            }
            
            if ( lineStart == -1 ) {
                if ( tgtRange.getStart() >= r.getStart() && tgtRange.getStart() < r.getEnd() ) {
                    lineStart = lineNum;
                    if ( tgtRange.getEnd() <= r.getEnd() ) {
                        lineEnd = lineNum;
                        break;
                    }
                }
            } else {
                if ( tgtRange.getEnd() <= r.getEnd() ) {
                    lineEnd = lineNum;
                    break;
                }
            }
            lineNum++;
        }
        
        if ( lineStart == lineEnd ) {
            return ""+lineStart;
        }
        else {
            return lineStart+" - "+lineEnd;
        }
    }

    private String getSiglum( final Long witId ) {
        for ( TaWitness w : this.witnesses ) {
            if ( w.getId().equals(witId)) {
                return w.getSiglum();
            }
         }
        return "UNK";
    }
    
    private List<Alignment> getAlignments(int startIdx, int batchSize) {
        QNameFilter changesFilter = this.filters.getDifferencesFilter();
        AlignmentConstraint constraints = new AlignmentConstraint(this.set, this.baseWitnessId);
        
        // when constraints use a base witness, the filter is EXCLUSIVE.
        // add any excluded witnesses to the filter now
        for ( TaWitness w : this.witnesses ) {
            if ( w.included == false ) {
                constraints.addWitnessIdFilter(w.id);
            }
        }
        constraints.setFilter(changesFilter);
        constraints.setResultsRange(startIdx, batchSize);
        return this.alignmentDao.list(constraints);
    }
    
    private String getWitnessText( final Long witId, final Range range ) {
        try {
            Witness w = this.witnessDao.find(witId);
            final RangedTextReader reader = new RangedTextReader();
            reader.read( this.witnessDao.getContentStream(w), range );
            String out = reader.toString();
            out = out.replaceAll("\\n+", " ").replaceAll("\\s+", " ");
            return out;
        } catch (Exception e) {
            LOG.error("Unable to get text for witness "+witId +", "+range, e);
            return "";
        }
    }
    
    private static class Variant {
        private Set<Integer> groups = new HashSet<Integer>();
        private Range range;
        private Map<Long, Range> witnessRangeMap = new HashMap<Long, Range>();
        
        public Variant( final Range r, final int groupId) {
            this.range = new Range(r);
            this.groups.add(groupId);
        }
        
        public boolean hasMatchingWitnesses(Variant other) {
            for ( Long witId : this.witnessRangeMap.keySet() ) {
                if ( other.getWitnessRangeMap().containsKey(witId) == false)  {
                    return false;
                }
            }
            return true;
        }

        public final Range getRange() {
            return this.range;
        }
        
        public final Map<Long, Range> getWitnessRangeMap() {
            return this.witnessRangeMap;
        }

        public void addWitnessDetail(Long witnessId, Range range, int group) {
            Range witRange = this.witnessRangeMap.get(witnessId);
            this.groups.add(group);
            if (witRange == null) {
                this.witnessRangeMap.put(witnessId, range);
            } else {
                Range expanded = new Range(
                    Math.min(witRange.getStart(), range.getStart()),
                    Math.max(witRange.getEnd(), range.getEnd()));
                this.witnessRangeMap.put(witnessId, expanded);
            }
        }
        
        public boolean hasMatchingGroup(Variant other) {
            if ( this.groups.size() != other.groups.size()) {
                return false;
            }
            for ( Integer g1 : this.groups ) {
                if ( other.groups.contains(g1) == false) {
                    return false;
                }
            }
            return true;
        } 
        
        public void merge( Variant mergeFrom ) {
            // new range of this change is the  min/max of the two ranges
            this.range = new Range(
                    Math.min( this.range.getStart(), mergeFrom.getRange().getStart() ),
                    Math.max( this.range.getEnd(), mergeFrom.getRange().getEnd() )
            );
            
            this.groups.addAll(mergeFrom.groups);
            
            // for each of the witness details in the merge source, grab the
            // details and add them to the details on this change. note that
            // all witnesses must match up between mergeFrom and this or the
            // merge will not happen. this is enforced in the heatmap render code
            for ( Entry<Long, Range> mergeEntry : mergeFrom.getWitnessRangeMap().entrySet()) {
                Long witId = mergeEntry.getKey();
                Range witRange = mergeEntry.getValue();
                Range thisRange = this.witnessRangeMap.get(witId);
                if ( thisRange == null ) {
                    this.witnessRangeMap.put(witId, witRange);
                } else {
                    Range expanded = new Range(
                        Math.min(witRange.getStart(), thisRange.getStart()),
                        Math.max(witRange.getEnd(), thisRange.getEnd()));
                    this.witnessRangeMap.put(witId, expanded);
                }
            }
        }
    }
    
    /**
     * Task to asynchronously render the visualization
     */
    private class CaTask implements BackgroundTask {
        private final String name;
        private BackgroundTaskStatus status;
        private Date startDate;
        private Date endDate;
        
        public CaTask(final String name) {
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
                TextualApparatusResource.this.render();
                LOG.info("Task "+this.name+" COMPLETE");
                this.endDate = new Date();   
                this.status.finish();
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
    
    /**
     * Information about a witness in the textual apparatus
     */
    public static final class TaWitness {
        private final Long id;
        private final String siglum;
        private final String title;
        private final boolean isBase;
        private final boolean included;
        
        public TaWitness( Long id) {
            this.id = id;
            this.siglum = "";
            this.title = "";
            this.isBase = false;
            this.included = false;
        }
        
        public TaWitness( Long id, String siglum, String title, boolean base) {
            this.id = id;
            this.siglum = siglum;
            this.title = title;
            this.isBase = base;
            this.included = true;
        }
        
        public Long getId() {
            return this.id;
        }
        
        public String getSiglum() {
            return this.siglum;
        }
        
        public String getTitle() {
            return this.title;
        }
        
        public boolean getIsBase() {
            return this.isBase;
        }
        
        public boolean getIsIncluded() {
            return this.included;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result + (included ? 1231 : 1237);
            result = prime * result + (isBase ? 1231 : 1237);
            result = prime * result + ((siglum == null) ? 0 : siglum.hashCode());
            result = prime * result + ((title == null) ? 0 : title.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            TaWitness other = (TaWitness) obj;
            if (id == null) {
                if (other.id != null)
                    return false;
            } else if (!id.equals(other.id))
                return false;
            if (included != other.included)
                return false;
            if (isBase != other.isBase)
                return false;
            if (siglum == null) {
                if (other.siglum != null)
                    return false;
            } else if (!siglum.equals(other.siglum))
                return false;
            if (title == null) {
                if (other.title != null)
                    return false;
            } else if (!title.equals(other.title))
                return false;
            return true;
        }
        
    }

}
