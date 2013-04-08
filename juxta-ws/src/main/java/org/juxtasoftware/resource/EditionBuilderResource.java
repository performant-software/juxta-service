package org.juxtasoftware.resource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.CacheDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.PageMarkDao;
import org.juxtasoftware.dao.UserAnnotationDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.AlignmentConstraint;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.PageMark;
import org.juxtasoftware.model.QNameFilter;
import org.juxtasoftware.model.UserAnnotation;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.BackgroundTask;
import org.juxtasoftware.util.BackgroundTaskCanceledException;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.juxtasoftware.util.ConversionUtils;
import org.juxtasoftware.util.QNameFilters;
import org.juxtasoftware.util.RangedTextReader;
import org.juxtasoftware.util.TaskManager;
import org.juxtasoftware.util.ftl.FileDirective;
import org.juxtasoftware.util.ftl.FileDirectiveListener;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.FileRepresentation;
import org.restlet.representation.ReaderRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
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
 * Resource used to create an Edition based on a set.
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class EditionBuilderResource extends BaseResource implements FileDirectiveListener {

    private enum Format { HTML, DOCX };

    @Autowired private PageMarkDao pageMarkDao;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private QNameFilters filters;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private CacheDao cacheDao;
    @Autowired private TaskManager taskManager;
    @Autowired private Integer visualizationBatchSize;
    @Autowired private UserAnnotationDao userNotesDao;
    
    private ComparisonSet set;
    private Long baseWitnessId;
    private String editionTitle;
    private Integer lineFrequency;
    private boolean numberBlankLines;
    private List<TaWitness> witnesses = new ArrayList<TaWitness>();   
    
    private static final Pattern PUNCTUATION = Pattern.compile("[^a-zA-Z0-9\\-]");
    
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
    
    @Get
    public Representation get() {
        if (getQuery().getValuesMap().containsKey("format") == false ) {
            return toTextRepresentation("Missing edition token");
        }
        
        long token = -1;
        try {
            token = Long.parseLong(getQuery().getValuesMap().get("token"));
        } catch (Exception e) {
            return toTextRepresentation("Invalid edition token format");
        }
        
        Format format = Format.HTML;
        if (getQuery().getValuesMap().containsKey("format") ) {
            format = Format.valueOf(getQuery().getValuesMap().get("format").toUpperCase());
            if ( format == null ) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid output format specified. Only HTML and RTF are acceptable.");
            }
        }
        
        if ( this.cacheDao.editionExists(this.set.getId(), token)) {
            Reader rdr = this.cacheDao.getEdition(this.set.getId(), token);
            if ( rdr != null ) {
                if ( format.equals(Format.HTML)) {
                    return new ReaderRepresentation( rdr, MediaType.TEXT_HTML);
                } else {
                    return convertHtmlToRtf(rdr);
                }
            } 
        }
        
        setStatus(Status.CLIENT_ERROR_NOT_FOUND);
        return toTextRepresentation("Invalid edition token");
    }
    
    private Representation convertHtmlToRtf(Reader reader) {
        try {
            File out = ConversionUtils.convertHtmlToDocx(reader);
            FileRepresentation rep =  new FileRepresentation(out, MediaType.APPLICATION_MSOFFICE_DOCX);
            rep.setAutoDeleting(true);
            return rep;
        } catch (Exception e) {
            LOG.error("Convert to DOCX failed", e);
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Unable to create docx version of Edition. Try HTML");
        }
    }

    /**
     * Create an based on settings present in the JSON payload.
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
            return toTextRepresentation("Unable to generate edition - set is not collated");
        }
               
        // parse the config 
        JsonParser p = new JsonParser();
        JsonObject jsonObj = p.parse(jsonData).getAsJsonObject();
        this.editionTitle = jsonObj.get("title").getAsString();
        this.lineFrequency = jsonObj.get("lineFrequency").getAsInt();
        this.numberBlankLines = jsonObj.get("numberBlankLines").getAsBoolean();
        
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
        
        // kick off a task to render the apparatus
        final String taskId =  "edition-"+set.hashCode();
        final long token = System.currentTimeMillis();
        if ( this.taskManager.exists(taskId) == false ) {
            EditionBuilderTask task = new EditionBuilderTask(taskId, token);
            this.taskManager.submit(task);
        } 
        return toJsonRepresentation( 
            "{\"status\": \"RENDERING\", \"taskId\": \""+taskId+"\", \"token\": \""+token+"\"}" );
    }
    

    @Override
    public void fileReadComplete(File file) {
        file.delete();
    }

    private void render( final long token ) throws IOException {
        // setup: create a tmp file to hold output and get a reader for base witness content
        File baseTxt = File.createTempFile("base", "txt");
        baseTxt.deleteOnExit();
        Witness base = this.witnessDao.find(this.baseWitnessId);
        Reader reader = this.witnessDao.getContentStream(base);
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(baseTxt), "UTF-8");
        
        // get the page number marks and select the first one (if available
        List<PageMark> lineNums = this.pageMarkDao.find(this.baseWitnessId, PageMark.Type.LINE_NUMBER);
        Iterator<PageMark> numItr = lineNums.iterator();
        PageMark currNum = null;
        if (numItr.hasNext()) {
            currNum = numItr.next();
        }
        
        // stream witness text from db into file incuding line num markup
        // and the line counts based on the line frequency setting. Track the
        // range associated with each line number so it can be used to record the
        // location of each variant report in the textual apparatus
        int pos = 0;
        int lineStartPos = 0;
        int lineNum = 1;
        StringBuilder line = new StringBuilder("");
        Map<Range, String> lineRanges = new TreeMap<Range, String>();
        String lineLabel = "";
        boolean done = false;
        boolean lineComplete = false;
        while ( !done) {
            int data = reader.read();
            if (data == -1) {
                done = true;
                lineComplete = true;
            } else {
                
                // save off any line number markup found at this psition.
                // it will be handled when the full line has been read
                if (currNum != null && currNum.getOffset() == pos) {
                    lineLabel = currNum.getLabel();
                    currNum = null;
                    if (numItr.hasNext()) {
                        currNum = numItr.next();
                    }
                }

                // now handle the actual content read...
                if (data == '\n') {
                    lineComplete = true;
                } else {
                    line.append(StringEscapeUtils.escapeXml(Character.toString((char) data)));
                }
            }
            
            if ( lineComplete ) {
                lineComplete = false;
                
                final int lineLen = line.toString().trim().length();
                boolean hasNumberMarkup = true;
                if ( lineLabel.length() == 0 ) {
                    hasNumberMarkup = false;
                    lineLabel = ""+lineNum;
                    if ( lineLen == 0 && this.numberBlankLines == false ) {
                        lineLabel = " ";
                    }
                }
                
                if ( lineLabel.length() > 0 ) {
                    lineRanges.put(new Range(lineStartPos, pos), lineLabel);
                }

                if (hasNumberMarkup == false && !(lineNum % this.lineFrequency == 0) ) {
                    // make sure something is here or blank rows collapse
                    lineLabel = " ";   
                }
                osw.write("<tr><td class=\"num-col\">"+lineLabel+"</td><td>"+line.toString()+"</td></tr>\n");
                if ( hasNumberMarkup == false && (lineLen > 0 || (lineLen == 0 && this.numberBlankLines)) ) {
                    lineNum++;
                }
                line = new StringBuilder("");
                lineStartPos = pos+1;
                lineLabel = "";
            }
            
            pos++;

        }

        IOUtils.closeQuietly(osw);
        IOUtils.closeQuietly(reader);
        
        // do all the heavy lifting required to create the textual apparatus
        // results will be an html table rendered to the file
        File appFile = generateApparatus(lineRanges);
        
        // populate template map and generate HTML
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("title", this.editionTitle);
        map.put("witnesses", this.witnesses);
        map.put("baseWitnessText", baseTxt.getAbsoluteFile());
        map.put("apparatusFile", appFile.getAbsoluteFile());
        FileDirective fd = new FileDirective();
        fd.setListener(this);
        map.put("fileReader", fd);  
        
        Representation rep = this.toHtmlRepresentation("edition.ftl", map, false, false);
        this.cacheDao.cacheEdition(this.set.getId(), token, rep.getReader());
    }
    
    private File generateApparatus(Map<Range, String> lineRanges) throws IOException {
        File appFile = File.createTempFile("app", "txt");
        appFile.deleteOnExit();
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(appFile), "UTF-8");

        // collect/merge all of the differences into a variant list
        List<Variant> variants = generateVariantList();
        
        // build the variant table one line at a time and stream it out to a file
        String priorBaseTxt = null;
        Set<String> priorWitsWithChange = new HashSet<String>();
        for (Variant variant : variants) {
            
            // First, grab the base witness fragment and add it to the variant string
            String baseTxt = "";
            boolean additionToBase = false;
            Range baseRange = new Range( variant.getRange() );
            if ( baseRange.length() > 0 ) {
                baseTxt = getWitnessText(this.baseWitnessId, baseRange);
                baseTxt = baseTxt.replaceAll("\\n+", " / ").replaceAll("\\s+", " ").trim();
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
                baseTxt = getBaseAdditionContext( baseRange.getStart());
                String[] parts = baseTxt.replaceAll("\\n+", " ").split(" ");
                if ( parts.length == 2 ) {
                    baseRange = new Range(baseRange.getStart()-parts[0].length(), baseRange.getStart()+parts[1].length()+1);
                } else {
                    baseRange = new Range(baseRange.getStart(), baseRange.getStart()+parts[0].length());
                }
                baseTxt = baseTxt.replaceAll("\\n+", " / ").replaceAll("\\s+", " ").trim();
            }
            
            // make it safe
            baseTxt = StringEscapeUtils.escapeXml( baseTxt ); 
            
            // Start the variant string. It is the base text followed by ]. 
            StringBuilder sb = new StringBuilder(baseTxt).append("] ");
            
            // walk thru each of the witnesses that have differences from
            // the base at this particular range. Extract the variant text.
            boolean nestedChange = false;
            Map<String,Set<String>> txtSiglumMap = new HashMap<String,Set<String>>();
            for (Entry<Long, Range> ent : variant.getWitnessRangeMap().entrySet()) {
                Long witId = ent.getKey();
                Range witRng = ent.getValue();
                String witTxt = "";
                if ( witRng.length() > 0 ) {
                    
                    if ( additionToBase ) {
                        // grab the added witness text plus extend out to include
                        // the prior and next words that book end the change. these
                        // extra 2 words should match the base text, and serve as bookends
                        // to delimit the addition
                        witTxt = getWitnessAdditionWithContext(witId, witRng);
                        
                        if ( priorBaseTxt != null ){
                            // if the first part of the bookend text was already
                            // handled by the prior line of the apparatus. flag
                            // it here so the witnesss wont get double counted.
                            nestedChange = witTxt.split(" ")[0].equals(priorBaseTxt);
                            if ( nestedChange == false && baseTxt.contains(priorBaseTxt)) {
                                nestedChange = true;
                            }
                        }
                    } else {
                        witTxt = getWitnessText(witId, witRng);
                        witTxt = witTxt.replaceAll("\\n+", " / ").replaceAll("\\s+", " ").trim();
                    }
                    
                    // clean up for XML
                    witTxt = StringEscapeUtils.escapeXml( witTxt ); 
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
            // to the witness list following the base fragment. 
            sb.append("<b>");
            addWitnessesMatchingBase(sb, txtSiglumMap, nestedChange, priorWitsWithChange);
            
            // end the base portion of the variant report
            sb.append(";</b> ");
            
            // Lastly, use the merged data from above to create the witness variants
            boolean first = true;
            StringBuilder ids = new StringBuilder();
            for (Entry<String, Set<String>> ent : txtSiglumMap.entrySet() ) {
                if ( first )  {
                    first = false;
                } else {
                    sb.append("; ");
                }
                String witTxt = ent.getKey();
               
                ids = new StringBuilder();
                for ( String siglum: ent.getValue() ) {
                    if ( ids.length() > 0 ) {
                        ids.append(", ");
                    } else {
                        ids.append(" <b>");
                    }
                    ids.append(siglum);
                }
                ids.append("</b>");
                sb.append(witTxt).append(ids);
            }
            
            // find line num for range
            String lineRange = findLineNumber(baseRange, lineRanges);            
  
            // shove the whole thing into a table row and write it out to disk
            final String out = "<tr><td class=\"num-col\">"+lineRange+"</td><td>"+sb.toString()+"</td></tr>\n";
            osw.write( out );
            
            // Add any user annotations on this exact range/base combo
            UserAnnotation anno = this.userNotesDao.find(this.set, this.baseWitnessId, baseRange);
            if (anno != null ) {
                for ( UserAnnotation.Data noteData : anno.getNotes() ) {
                    StringBuilder a = new StringBuilder();
                    a.append("<tr><td class=\"num-col\"> </td><td><i>");
                    if ( anno.hasGroupAnnotation() ) {
                        StringBuilder g = new StringBuilder();
                        for (Long id : this.userNotesDao.getGroupWitnesses( anno.getGroupId() ) ) {
                            if ( g.length() > 0 ) {
                                g.append(", ");
                            }
                            g.append(getSiglum(id));
                        }
                        
                        a.append("<b>").append( g ).append("</b>: ");
                    } else {
                        a.append( "<b>").append( getSiglum(noteData.getWitnessId()) ).append("</b>: ");
                    }
                    a.append(noteData.getText()).append("</i></td></tr>\n");
                    osw.write( a.toString() ); 
                }
            }
            
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
        
        Collections.sort(variants);
        Variant p = null;
        for ( Iterator<Variant> vitr = variants.iterator(); vitr.hasNext(); ) {
            Variant v = vitr.next();
            if ( p != null ) {
                if ( p.getRange().equals(v.getRange())) {
                    p.merge(v);
                    vitr.remove();
                }
            }
            p = v;
        }

        
        return variants;
    }
    
    private String getWitnessAdditionWithContext(final Long witId, final Range witRange ) {
        final int defaultSize = 40;
        Witness w = this.witnessDao.find(witId);
        long maxLen = w.getText().getLength();
        int start = (int)witRange.getStart();
        int end = (int)witRange.getEnd();
        int contextSize = defaultSize;
        
        // special case: added at start of doc
        if ( start < 10 ) {
            Range r = new Range(start, Math.min(maxLen,end+contextSize));
            String witTxt = getWitnessText(witId, r);
            int spacePos = witTxt.replaceAll("\\n+", " ").indexOf(' ', (end-start)+1);
            if ( spacePos > -1 ) {
                String added = witTxt.substring(0, (end-start));
                String out =  added+witTxt.substring((end-start), spacePos);
                out = out.replaceAll("\\n+", " / ");
                return out.trim();
            } else {
                return witTxt.replaceAll("\\n+", " / ");
            }
        }
        
        // special case: added at end of doc
        if ( end >= (maxLen-10) ) {
            Range r = new Range( Math.max(0,start-contextSize), end);
            String witTxt = getWitnessText(witId, r);
            int startAdd = contextSize;
            String added = witTxt.substring( startAdd );
            int p = startAdd;
            int spcCnt = 0;
            String normalized = witTxt.replaceAll("\\n+", " ");
            while (p > 0) {
                if ( normalized.charAt(p) == ' ') {
                    spcCnt++;
                    if (spcCnt == 2 ) {
                        break;
                    }
                }
                p--;
            }
            String out= witTxt.substring(p,startAdd)+added;
            return out.replaceAll("\\n+", " / ").trim();
        }

        // don't extend less < 0
        contextSize = Math.min(defaultSize, start);

        // don't extend past end of doc
        long endPos = end + contextSize;
        if (endPos >= maxLen) {
            long delta = endPos - maxLen;
            contextSize -= delta;
        }
        
        // get the fragment (including the central word in the witness)
        String witTxt = getWitnessText(witId, new Range(start - contextSize, end + contextSize));
        String normalized = witTxt.replaceAll("\\n+", " ");
        int fragStart = contextSize;
        int spcCnt = 0;
        while ( fragStart > 0 ) {
            if ( normalized.charAt(fragStart) == ' ') {
                spcCnt++;
                if (spcCnt == 2 ) {
                    break;
                }
            }
            fragStart--;
        }
        
        int fragEnd = contextSize+(end-start);
        spcCnt = 0;
        while ( fragEnd < normalized.length()) {
            if ( normalized.charAt(fragEnd) == ' ') {
                spcCnt++;
                if (spcCnt == 2 ) {
                    break;
                }
            }
            fragEnd++;
        }

        return witTxt.substring(fragStart, fragEnd).trim().replaceAll("\\n+", " / ");
    }

    private String getBaseAdditionContext(final long pos) {
        Witness w = this.witnessDao.find(this.baseWitnessId);
        long maxLen = w.getText().getLength();
        
        final int defaultSize = 40;
        int contextSize = defaultSize;
        if (pos < 10 ) {
            Range r = new Range(0, Math.min(maxLen,contextSize));
            String witTxt = getWitnessText(this.baseWitnessId, r).trim();   
            int spcPos = witTxt.replaceAll("\\n+", " ").indexOf(" ");
            if ( spcPos > -1 ) {
                return witTxt.substring(0, spcPos);
            } else {
                return witTxt;
            }
        }
        
        if ( pos >= (maxLen-10) ) {
            Range r = new Range( Math.max(0, pos-contextSize), pos);
            String witTxt = getWitnessText(this.baseWitnessId, r);
            int spcPos = witTxt.replaceAll("\\n+", " ").lastIndexOf(' ');
            if ( spcPos > -1 ) {
                return witTxt.substring(spcPos);
            } else {
                return witTxt;
            }
        }

        // don't extend less < 0
        contextSize = Math.min(defaultSize, (int) pos);

        // don't extend past end of doc
        long endPos = pos + contextSize;
        if (endPos >= maxLen) {
            long delta = endPos - maxLen;
            contextSize -= delta;
        }

        String witTxt = getWitnessText(this.baseWitnessId, new Range(pos - contextSize, pos + contextSize));
        String before = witTxt.substring(0, contextSize);
        String wb = before.substring(before.replaceAll("\\n+"," ").lastIndexOf(' '));
        String after = witTxt.substring(contextSize);
        String wa = after;
        int start = 0;
        while (true) {
            int spacePos = after.replaceAll("\\n+"," ").indexOf(' ', start);
            if (spacePos > -1) {
                wa = after.substring(start, spacePos);
                if (wa.length() == 1 && PUNCTUATION.matcher(wa).find()) {
                    if ( wa.equals("&") ) {
                        wb = wb + " "+ wa+" ";
                    } else {
                        wb = wb+wa+" ";
                    }
                    start = spacePos + 1;
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        witTxt = wb + wa;
        return witTxt;
    }
    
    private String findLineNumber(Range tgtRange, Map<Range, String> lineRanges) {
        String startLine = "";
        for ( Entry<Range, String> entry : lineRanges.entrySet() ) {
            Range r = entry.getKey();
            if ( startLine.length() == 0 ) {
                if ( tgtRange.getStart() >= r.getStart() && tgtRange.getStart() <= r.getEnd() ) {
                    startLine = entry.getValue();
                    if ( tgtRange.getEnd() <= r.getEnd() ) {
                        return startLine;
                    }
                }
            } else {
                if ( tgtRange.getEnd() <= r.getEnd() ) {
                    if ( startLine.trim().length() == 0) {
                        return entry.getValue();
                    }
                    return startLine +"-"+entry.getValue();
                }
            }
        }
        return startLine;
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
            return out;
        } catch (Exception e) {
            LOG.error("Unable to get text for witness "+witId +", "+range, e);
            return "";
        }
    }
    
    private static class Variant implements Comparable<Variant> {
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

        @Override
        public int compareTo(Variant that) {
            Range r1 = this.range;
            Range r2 = that.range;
            if (r1.getStart() < r2.getStart()) {
                return -1;
            } else if (r1.getStart() > r2.getStart()) {
                return 1;
            } else {
                if (r1.getEnd() < r2.getEnd()) {
                    return -1;
                } else if (r1.getEnd() > r2.getEnd()) {
                    return 1;
                }
            }
            return 0;
        }
    }
    
    /**
     * Task to asynchronously render the visualization
     */
    private class EditionBuilderTask implements BackgroundTask {
        private final String name;
        private BackgroundTaskStatus status;
        private final long token;
        private Date startDate;
        private Date endDate;
        
        public EditionBuilderTask(final String name, long token) {
            this.name =  name;
            this.token = token;
            this.status = new BackgroundTaskStatus( this.name );
            this.startDate = new Date();
        }
        
        @Override
        public Type getType() {
            return BackgroundTask.Type.EDITION;
        }
        
        @Override
        public void run() {
            try {
                LOG.info("Begin task "+this.name);
                this.status.begin();
                EditionBuilderResource.this.render( this.token  );
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
