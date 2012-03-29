package org.juxtasoftware.resource.heatmap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.output.FileWriterWithEncoding;
import org.apache.commons.lang.StringEscapeUtils;
import org.juxtasoftware.Constants;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.CacheDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaAnnotationDao;
import org.juxtasoftware.dao.NoteDao;
import org.juxtasoftware.dao.PageBreakDao;
import org.juxtasoftware.dao.RevisionDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.AlignmentConstraint;
import org.juxtasoftware.model.AnnotationConstraint;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaAnnotation;
import org.juxtasoftware.model.QNameFilter;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.resource.BaseResource;
import org.juxtasoftware.util.QNameFilters;
import org.juxtasoftware.util.ftl.FileDirective;
import org.juxtasoftware.util.ftl.HeatmapStreamDirective;
import org.restlet.representation.Representation;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import eu.interedition.text.Range;
import eu.interedition.text.rdbms.RelationalText;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class HeatmapView implements ApplicationContextAware {
    @Autowired private CacheDao cacheDao;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private JuxtaAnnotationDao annotationDao;
    @Autowired private ComparisonSetDao setDao;    
    @Autowired private NoteDao noteDao;
    @Autowired private PageBreakDao pbDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private RevisionDao revisionDao;
    @Autowired private QNameFilters filters;
    @Autowired private HeatmapStreamDirective heatmapDirective;
    
    private ApplicationContext context;
    private BaseResource parent;
    private int minimumEditDistance = 0;

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
        
        // Get all witness (and changeIndex) info. Pick out the base
        List<SetWitness> witnesses = getWitnessInfo( set, baseWitnessId );
        if ( witnesses.size() < 2) {
            return this.parent.toTextRepresentation("This set contains less than two witnesess. Unable to view heatmap.");
        }
        Witness base = null;
        for ( SetWitness w : witnesses ) {
            if ( w.isBase() ) {
                base = w;
                break;
            }
        }
               
        // init FTL data map
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("hasNotes", false);
        map.put("hasBreaks", false);
      
        // Next, the get heatmap main body (map, notes and margin boxes)
        // Grab it from cache if possible
        if ( this.cacheDao.heatmapExists(set.getId(), base.getId())==false) {
            renderHeatMap( set, base, witnesses );
        }
        
        // Last, wrap the body with ui (title, comparison set details)
        map.put("hasNotes", this.noteDao.hasNotes( base.getId() ) );
        map.put("hasBreaks", this.pbDao.hasBreaks( base.getId() ) );
        map.put("hasRevisions", this.revisionDao.hasRevisions(base));
        map.put("setId", set.getId());
        map.put("baseId", base.getId());
        map.put("baseName", base.getName() );
        map.put("witnesses", witnesses );
        map.put("heatmapStreamer", this.heatmapDirective);
        map.put("page", "set");
        map.put("title", "Juxta Heatmap View: "+set.getName());
        
        // fill in the hidden spans with sements of urls so the
        // javascript can piece together fill URLs for all of
        // its ajax requests. This also becomes an extnsion point
        // for systems that embed heatmap views in the main ui. They
        // just provide alternate values for these 3 elemements
        map.put("ajaxBaseUrl", parent.getRequest().getHostRef().toString()+"/juxta/"+parent.getWorkspace()+"/set/");
        map.put("viewHeatmapSegment", "/view?mode=heatmap");
        map.put("fragmentSegment", "/diff/fragment");
        
        return this.parent.toHtmlRepresentation("heatmap.ftl", map);
    }
    
    private List<SetWitness> getWitnessInfo( ComparisonSet set, Long baseWitnessId ) {
        List<SetWitness> out = new ArrayList<SetWitness>();
        List<Witness> witnesses = new ArrayList<Witness>(this.setDao.getWitnesses(set));
        if ( witnesses.size() < 2 ) {
            return out;
        }
        Map<Long, Long> witnessDiffLen = new HashMap<Long, Long>();
        
        // setup base witnessID; if unspecified just pick
        // first witness as the base
        if ( baseWitnessId == null ) {
            baseWitnessId = witnesses.get(0).getId();
        }

        // get all alignments for this set and add up diff length
        // for each annotaion
        QNameFilter changesFilter = this.filters.getDifferencesFilter();
        AlignmentConstraint constraints = new AlignmentConstraint(set, baseWitnessId);
        constraints.setFilter(changesFilter);
        List<Alignment> diffList = this.alignmentDao.list(constraints);
        for ( Alignment align : diffList ) {
            long longestDiff = -1;
            Long witnessId = null;
            for (AlignedAnnotation aa : align.getAnnotations() ) {
                if ( aa.getWitnessId().equals( baseWitnessId ) == false ) {
                    witnessId = aa.getWitnessId();
                }
                long len = aa.getRange().length();
                if ( len > longestDiff ) {
                    longestDiff = len;
                }
            }
            
            Long priorLen = witnessDiffLen.get(witnessId);
            if ( priorLen == null) {
                priorLen = new Long(0);
            }
            witnessDiffLen.put(witnessId, priorLen+longestDiff);
        }

        
        // find length of base witness: get all tokens
        // and sum up their lengths. Done this way because
        // all alignments are tied to tokens. If the basic
        // Text length was used, the data would be wrong - tokens 
        // do not include whitespace/punctuation and text len does
        long baseLen = 0;
        for (Witness w : witnesses ) {
            if ( w.getId().equals( baseWitnessId )) {
                AnnotationConstraint constraint = new AnnotationConstraint( w);
                constraint.setIncludeText( false );
                constraint.setFilter( this.filters.getTokensFilter() );
                for (JuxtaAnnotation token : this.annotationDao.list( constraint ) ) {
                    baseLen += token.getRange().length();
                }
                break;
            }
        }
        
        // now run thru all witnesses and append a change index to them
        // change idx is [total witness diff chars] / [totals chars in base]
        for (Witness w : witnesses ) {
            if ( w.getId().equals( baseWitnessId )) {
                out.add( new SetWitness(w, true, 0.0f));
            } else {
                Long changeLen = witnessDiffLen.get(w.getId());
                float changeIdx = 0.0f;
                if ( changeLen != null ) {
                    changeIdx = (float)changeLen / (float)baseLen;
                    out.add( new SetWitness(w, false, changeIdx));
                }
            }
        }
        
        return out;
    }
    
    private void renderHeatMap(ComparisonSet set, Witness base, List<SetWitness> witnesses) throws IOException {
               
        // get a list of revisons, differeces, notes and breaks in ascending oder.
        // add this information to injectors that will be used to inject
        // supporting markup into the witness heatmap stream
        final ChangeInjector changeInjector = this.context.getBean(ChangeInjector.class);
        changeInjector.setWitnessCount( witnesses.size() );
        changeInjector.initialize( generateHeatmapChangelist( set, base, witnesses ) );
        final NoteInjector noteInjector = this.context.getBean(NoteInjector.class);
        noteInjector.initialize( this.noteDao.find(base.getId()) );
        final BreakInjector pbInjector = this.context.getBean(BreakInjector.class);
        pbInjector.initialize( this.pbDao.find(base.getId()) );
        final RevisionInjector revisionInjector = this.context.getBean(RevisionInjector.class);
        revisionInjector.initialize( this.revisionDao.getRevisions(base) );
        
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
        
        // close up the file
        br.close();
        
        // create the template map and stuff it with some basic data
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("baseName", base.getName());
        map.put("srcFile", heatmapFile.getAbsoluteFile());
        map.put("fileReader", new FileDirective());   
        map.put("numWitnesses", witnesses.size()-1);
        map.put("notes", noteInjector.getData() );
        
        // create the main body of the heatmap. NOTE the false param
        // this tells the base NOT to use the layout template when generating
        // the representation
        Representation heatmapFtl = this.parent.toHtmlRepresentation("heatmap_text.ftl", map, false);
                
        // Stuff it in a cache for future fast response
        this.cacheDao.cacheHeatmap(set.getId(), base.getId(), heatmapFtl.getReader());
        
        // done with this file. kill it explicitly
        heatmapFile.delete();
    }    

    private List<Change> generateHeatmapChangelist( final ComparisonSet set, final Witness base, final List<SetWitness> witnesses ) {

        // get all of the alignments and sort them in ascending
        // range relative to the base witness
        QNameFilter changesFilter = this.filters.getDifferencesFilter();
        AlignmentConstraint constraints = new AlignmentConstraint(set, base.getId());
        constraints.setFilter(changesFilter);
        List<Alignment> diffList = this.alignmentDao.list(constraints);
        Collections.sort(diffList, new Comparator<Alignment>() {
            @Override
            public int compare(Alignment a, Alignment b) {
                // NOTE: There is a bug in interedition Range. It will
                // order range [0,1] before [0,0] when sorting ascending.
                // So.. do NOT use its compareTo. Roll own.
                Range r1 = a.getWitnessAnnotation(base.getId()).getRange();
                Range r2 = b.getWitnessAnnotation(base.getId()).getRange();
                if ( r1.getStart() < r2.getStart() ) {
                    return -1;
                } else if ( r1.getStart() > r2.getStart() ) {
                    return 1;
                } else {
                    if ( r1.getEnd() < r2.getEnd() ) {
                        return -1;
                    } else if ( r1.getEnd() > r2.getEnd() ) {
                        return 1;
                    } 
                }
                return 0;
            }
        });
        
        // generate a change list based on the sorted differences
        long changeIdx = 0;
        Map<Range, Change> changeMap = new HashMap<Range, Change>();
        List<Change> changes = new ArrayList<Change>();
        for ( Alignment align : diffList ) {
            
            // the heatmap is from the perspective of the BASE
            // text, so only care about annotations that refer to it
            AlignedAnnotation baseAnno = align.getWitnessAnnotation(base.getId());
                    
            // IGNORE all changes (not add/dels) that are 
            // below the specified edit distance threshold
            if (  align.getEditDistance() < this.minimumEditDistance &&
                  align.getName().equals( Constants.ADD_DEL_NAME ) == false) {
                continue;
            }
                    
            // Track all changed ranges in the base document
            Change change = changeMap.get(baseAnno.getRange());
            if ( change == null ) {
                change= new Change( changeIdx++, baseAnno.getRange(), align.getGroup() );
                changeMap.put(baseAnno.getRange(), change);
                changes.add(change);
            }
            
            // Find the annotation details for the witness half
            // of this alignment
            AlignedAnnotation witnessAnnotation = null;
            Witness witness = null;
            for ( AlignedAnnotation a : align.getAnnotations()) {
                if ( a.getWitnessId().equals( base.getId()) == false ) {
                    witnessAnnotation = a;
                    witness = findWitness(witnesses, witnessAnnotation.getWitnessId());
                    break;
                }
            }
                    
            // Add all witness change details to the base change. There can be many.
            // This will occur when the witness has added/deleted several words to the text.
            // Each word will show up here as an additional annotation linked to the
            // base doc change. When this happens, add the new ranges to the existing
            // witness change detail. these ranges will be used to highlight corresponding
            // text in the margin box.
            change.addWitnessDetail( witness, align.getName(), witnessAnnotation.getRange() );
        }
        
        // Walk the ranges and extend them to cover gaps
        // and merge adjacent, same intensity changes into one
        List<Change> deleteList = new ArrayList<Change>();
        Change prior = null;
        Long baseTextId = ((RelationalText) base.getText()).getId();
        for (Iterator<Change> itr = changes.iterator(); itr.hasNext();) {
            Change change = itr.next();
            if (prior != null) {
                // increase len of add/del so they are visible
                if (prior.getRange().length() == 0) {
                    long start = prior.getRange().getStart();
                    if (prior.getRange().getStart() == 0) {
                        prior.adjustRange(start, start + 1);
                    } else {
                        long newStart = this.annotationDao.findNextTokenStart(baseTextId, start);
                        prior.adjustRange(newStart, newStart + 1);
                    }
                    
                    // if this overlaps the current change, toss the prior
                    // and move on to next change
                    if (change.getRange().getStart() <= prior.getRange().getStart()) {
                        deleteList.add(prior);
                        prior = change;
                        continue;
                    }
                }

                // See if these are a candidate to merge. Criteria:
                //  * diff must be between same witnesses
                //  * diffs must have same frequence
                //  * must be from the same alignment group   
                if ( prior.getGroup() == change.getGroup() && change.hasMatchingWitnesses(prior) && 
                     change.getDifferenceFrequency() == prior.getDifferenceFrequency()) {
                    prior.mergeChange(change);
                    itr.remove();
                    continue;
                }
            }

            prior = change;
        }
        
        // see if the LAST change has 0 length and make it visible if this is the case
        if ( prior.getRange().length() == 0 ) {
            long start = prior.getRange().getStart();
            if ( prior.getRange().getStart() < base.getText().getLength()-1 ) {
                long newStart = this.annotationDao.findNextTokenStart(baseTextId, start);
                prior.adjustRange(newStart, newStart + 1);
            } else {
                if ( start > 0) {
                    prior.adjustRange(start-1, start);
                }
            }
        }

        // scrub any that were found to overlap
        for ( Change c : deleteList ) {
            changes.remove(c);
        }
        
        return changes;
    }
    
    private Witness findWitness(List<SetWitness> witnesses, Long id) {
        for (Witness w: witnesses) {
            if (w.getId().equals( id )) {
                return w;
            }
        }
        return null;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }
    
    /**
     * Extension of a witness to include change index data
     * @author loufoster
     *
     */
    public static class SetWitness extends Witness  {
        private final float changeIndex;
        private final boolean isBase;
        
        public SetWitness( Witness w, boolean isBase, float changeIdx) {
            super(w);
            this.isBase = isBase;
            this.changeIndex = changeIdx;
        }
        public float getChangeIndex() {
            return this.changeIndex;
        }
        public boolean isBase() {
            return this.isBase;
        }
    }
}