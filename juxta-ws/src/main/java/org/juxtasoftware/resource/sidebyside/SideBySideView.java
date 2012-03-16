package org.juxtasoftware.resource.sidebyside;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.output.FileWriterWithEncoding;
import org.apache.commons.lang.StringEscapeUtils;
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
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.resource.BaseResource;
import org.juxtasoftware.util.QNameFilters;
import org.juxtasoftware.util.ftl.FileDirective;
import org.juxtasoftware.util.ftl.FileDirectiveListener;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import eu.interedition.text.Range;
import eu.interedition.text.rdbms.RelationalText;

/**
 * Class used to render the side by side view
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SideBySideView implements FileDirectiveListener, ApplicationContextAware {
    
    @Autowired private ComparisonSetDao setDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private QNameFilters filters;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private CacheDao cacheDao;
    
    protected static final Logger LOG = LoggerFactory.getLogger( "SideBySideView" );
    
    private ApplicationContext context;
    private BaseResource parent;
    private List<WitnessInfo> witnessDetails = new ArrayList<SideBySideView.WitnessInfo>(2);
    
    public Representation toHtml( final BaseResource parent, final ComparisonSet set) throws IOException {
        this.parent = parent;
        
        if (parent.getQuery().getValuesMap().containsKey("refresh") ) {
            this.cacheDao.deleteSideBySide(set.getId());
        }
        
        // ensure that the document pair is specified as a param
        if ( parent.getQuery().getValuesMap().containsKey("docs") == false ) {
            parent.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return parent.toTextRepresentation("Missing required docs param");
        } 
        
        // extract the pair of documents for the comparison
        String docs = parent.getQuery().getValues("docs");
        String docsList[] = docs.split(",");
        Long witnessIds[] = new Long[2];
        if ( docsList.length == 2) {
            witnessIds[0] = Long.parseLong(docsList[0]);
            witnessIds[1] = Long.parseLong(docsList[1]);
        } else {
            parent.getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return parent.toTextRepresentation("Malformed docs param");
        }
       
        // Grab it from cache if possible
        if ( this.cacheDao.sideBySideExists(set.getId(), witnessIds[0], witnessIds[1]) == true) {
            Reader sbsReader = this.cacheDao.getSideBySide(set.getId(), witnessIds[0], witnessIds[1]);
            return parent.toHtmlRepresentation(sbsReader);
        }
        
        // get witnesses for each ID and initialize the changes map
        for ( int i=0; i<witnessIds.length; i++ ) {
            Witness w = this.witnessDao.find(witnessIds[i]);
            if ( w == null ) {
                parent.getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return parent.toTextRepresentation("Invalid witness ID "+witnessIds[i]);
            }
            this.witnessDetails.add( new WitnessInfo(w) );
        }
        
        // special case! Only attempt to get and connect
        // differences if the comparands are different.
        if ( witnessIds[0] != witnessIds[1] ) {
            // generate the change lists for each witness and
            // update the changes map with this data
            generateWitnessChangeLists(set);
    
            // find connections between changes in each witness
            connectChanges();
            
            // find any marked transpositions, connect them and
            // add them to the witness info
            connectTranspositions(set);
        }
        
        // render each witness text with changes injected
        for ( WitnessInfo info : this.witnessDetails ) {
            renderDocument( info );
        }
        
        // get all of the witnesses in this set. It will be used
        // by the front end to present the user with a list
        // of witnesses when chaning comparands
        Set<Witness> witnesses = this.setDao.getWitnesses(set);
        
        // stuff this info into a map for freemarker
        FileDirective fileDirective = new FileDirective();
        fileDirective.setListener( this );
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("setId", set.getId());
        map.put("setName", set.getName());
        map.put("page", "set");
        map.put("title", "Juxta Side By Side View: "+set.getName());
        map.put("fileReader", fileDirective );  
        map.put("witnessDetails", this.witnessDetails); 
        map.put("witnesses", witnesses);
        Representation sbsFtl =  this.parent.toHtmlRepresentation("side_by_side.ftl", map);
        
        // Stream data into cache DB (this invalidates the reader), the stream it back out of
        // the db, back to the client
        this.cacheDao.cacheSideBySide(set.getId(), witnessIds[0], witnessIds[1], sbsFtl.getReader());
        return parent.toHtmlRepresentation( this.cacheDao.getSideBySide(set.getId(),  witnessIds[0], witnessIds[1]));
    }

    @Override
    public void fileReadComplete(File file) {
        // once the file has been rendered to the template
        // it is no longer needed and can be deleted
        file.delete();
    }
    
    void connectTranspositions(ComparisonSet set) {
        // grab any transpositions that have been marked
        AlignmentConstraint constraint = new AlignmentConstraint( set );
        constraint.setFilter( this.filters.getTranspositionsFilter() );
        constraint.addWitnessIdFilter( this.witnessDetails.get(0).getId());
        constraint.addWitnessIdFilter( this.witnessDetails.get(1).getId());
        List<Alignment> transpositions = this.alignmentDao.list(constraint);
        
        for ( Alignment align :  transpositions ) {
            Transposition prior = null;
            for ( AlignedAnnotation a : align.getAnnotations() ) {
                WitnessInfo witnessInfo = getWitnessInfo(a.getWitnessId());
                Transposition t = new Transposition(a.getRange());
                witnessInfo.addTransposition(t);   
                
                // once we have 2, connect them
                if ( prior != null ) {
                    prior.connectedToId = t.id;
                    t.connectedToId = prior.id;
                    
                }
                prior = t;
            }
        }
        
        // sort in range order
        Collections.sort(  this.witnessDetails.get(0).getTranspositions() );
        Collections.sort(  this.witnessDetails.get(1).getTranspositions() );
        
    }
    
    void connectChanges() {
        LOG.info("Make connections");
        // walk through each set of changes for the comparands.
        // changes are considered connected if they share an
        // alignment id
        for ( Change change : this.witnessDetails.get(0).changes ) {
            for ( Change otherChange : this.witnessDetails.get(1).changes ) {
                if ( change.isConnected(otherChange)) {
                    change.connectedToId = otherChange.id;
                    otherChange.connectedToId = change.id;
                    break;
                }
            }
        }
        
        // scan thru the list starting with the OPPOSITE witness and 
        // look for un-connected changes. This may happen if a change was
        // merged in one witness but not the other.
        for ( Change change : this.witnessDetails.get(1).changes ) {
            if ( change.connectedToId == null) {
                for ( Change otherChange : this.witnessDetails.get(0).changes ) {
                    if ( change.isConnected(otherChange)) {
                        change.connectedToId = otherChange.id;
                        otherChange.connectedToId = change.id;
                        break;
                    }
                }
            }
        }
    }
    
    private void generateWitnessChangeLists(final ComparisonSet set) {
        // get all of the alignments that involve one of the 
        // witnesses in this comparison. Split the changes into
        // separate lists for each
        LOG.info("Get diffs...");
        QNameFilter changesFilter = this.filters.getDifferencesFilter();
        AlignmentConstraint constraints = new AlignmentConstraint(set);
        constraints.addWitnessIdFilter( this.witnessDetails.get(0).getId() );
        constraints.addWitnessIdFilter( this.witnessDetails.get(1).getId() );
        constraints.setFilter(changesFilter);
        for ( Alignment align :  this.alignmentDao.list(constraints) ) {
            for ( AlignedAnnotation a : align.getAnnotations() ) {
                WitnessInfo witnessInfo = getWitnessInfo(a.getWitnessId());
                Change newChange = new Change(align, a.getRange(), align.getGroup());
                witnessInfo.addChange( newChange );  
            }
        }
        
        // sort each change set in ascending range order and merge adjacent changes
        LOG.info("Sort and merge diffs....");
        for ( WitnessInfo info : this.witnessDetails ) {
            Collections.sort( info.getChanges() );
            Change prior = null;
            for ( Iterator<Change> itr =  info.getChanges().iterator(); itr.hasNext();  ) {
                Change change = itr.next();
                if (prior != null) {
                    if (prior.getGroup() == change.getGroup()) {
                        prior.merge(change);
                        itr.remove();
                        continue;
                    }
                }
                prior = change;
            }
        }
    }
    
    private WitnessInfo getWitnessInfo( Long id ) {
        for ( WitnessInfo wi : this.witnessDetails ) {
            if ( wi.witness.getId().equals( id ) ) {
                return wi;
            }
        }
        return null;
    }
    
    private void renderDocument(WitnessInfo info ) throws IOException {
                
        // create content injectors
        final DiffInjector diffInjector = this.context.getBean(DiffInjector.class);
        diffInjector.initialize(  info.getChanges() );
        final TranspositionInjector moveInjector = this.context.getBean(TranspositionInjector.class);
        moveInjector.initialize(info.getTranspositions());
        
        BufferedWriter writer = new BufferedWriter( new FileWriterWithEncoding(info.file, "UTF-8") );
        Reader reader = this.witnessDao.getContentStream(info.witness);
        StringBuilder line = new StringBuilder();
        boolean done = false;
        int pos = 0;
        
        long lastMoveStart = -1;
        long lastDiffStart = -1;
        while ( done == false ) {
            int data = reader.read();
            if ( data == -1 ) {
                done = true;
            } 
            
            // as long as any injectors are ready, keep going
            while ( diffInjector.hasContent(pos) || moveInjector.hasContent(pos)  ) { 
                
                // dump in start tags and track their positions. 
                // this info will be used to detect overlaps and fix them
                if ( moveInjector.injectContentStart(line, pos) ) {
                    lastMoveStart = pos;
                }
                if ( diffInjector.injectContentStart(line, pos) ) {
                    lastDiffStart = pos;
                }
    
                // now see if any of this injected data needs to be closed
                // and handle any overlapping heirarchies
                if ( diffInjector.injectContentEnd(line, pos) == true ) {
                    if ( lastMoveStart != -1 && lastDiffStart < lastMoveStart) {
                        moveInjector.restartContent(line);
                    }
                    lastDiffStart = -1;
                }
                if ( moveInjector.injectContentEnd(line, pos) == true ) {
                    if ( lastDiffStart != -1 && lastMoveStart < lastDiffStart ) {
                        diffInjector.restartContent(line);
                    }
                    lastMoveStart = -1;
                }
            }

            // once a newline or EOF is reached, write it to the data file
            if ( data == '\n' || data == -1 ) {
                line.append("<br/>");
                writer.write(line.toString());
                writer.newLine();
                line = new StringBuilder();
            } else {
                // escape the text before appending it to the output stream
                line.append( StringEscapeUtils.escapeHtml( Character.toString((char)data) ) );
            }
            pos++;
        }
        
        // close up the file
        writer.close();
        
    }
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }
    
    /**
     * A collection of simplified side-by-side information for a witness
     */
    public static class WitnessInfo {
        final Witness witness;
        File file;
        List<Change> changes;
        List<Transposition> transpositions;
        
        public WitnessInfo( Witness witness ) throws IOException {
            this.witness = witness;
            this.changes = new ArrayList<Change>();
            this.transpositions = new ArrayList<SideBySideView.Transposition>();
            this.file = File.createTempFile("sbs_"+witness.getId(), "dat");
            this.file.deleteOnExit();
        }
        
        public Long getId() {
            return this.witness.getId();
        }
        
        public Long getTextId() {
            return ((RelationalText)this.witness.getText()).getId();
        }
        
        public String getName() {
            return this.witness.getName();
        }
        
        public File getFile() {
            return this.file;
        }
        
        public void addChange( Change c ) {
            this.changes.add(c);
        }
        
        public List<Change> getChanges() {
            return this.changes;
        }
        
        public void addTransposition( Transposition t ) {
            this.transpositions.add(t);
        }
        
        public List<Transposition> getTranspositions() {
            return this.transpositions;
        }
        
        @Override 
        public String toString() {
            return this.witness.getName();
        }
    }
    
    /**
     * Class to track transpositions
     */
    static class Transposition implements Comparable<Transposition> {
        final Long id;
        Long connectedToId;
        Range range;
        static long idGen = 0;
        
        public Transposition(Range witnessRange) {
            this.id = Change.idGen++;
            this.range = witnessRange;
        }
        
        public Range getRange() {
            return this.range;
        }
        @Override
        public int compareTo(Transposition that) {
            if ( this.range.getStart() < that.range.getStart() ) {
                return -1;
            } else if ( this.range.getStart() > that.range.getStart() ) {
                return 1;
            } else {
                if ( this.range.getEnd() < that.range.getEnd() ) {
                    return -1;
                } else if ( this.range.getEnd() > that.range.getEnd() ) {
                    return 1;
                } 
            }
            return 0;
        }
    }

    /**
     * Simplified class to track change by id range and type
     */
    static class Change implements Comparable<Change> {
        List<Long> alignIdList = new ArrayList<Long>();
        final Long id;
        final int group;
        Long connectedToId;
        String type;
        Range range;
        static long idGen = 0;
        
        public Change( Alignment align, Range witnessRange, int group) {
            this.id = Change.idGen++;
            this.group = group;
            this.alignIdList.add( align.getId() );
            this.range = witnessRange;
            this.type = "Change";
            if ( align.getName().equals(Constants.ADD_DEL_NAME) ) {
                if ( range.length() == 0 ) {
                    this.type = "Deleted";
                } else {
                    this.type = "Inserted";
                }
            }
        }
        
        public int getGroup() {
            return this.group;
        }
        
        public Range getRange() {
            return this.range;
        }
        public boolean isConnected( Change other ) {
            for ( Long id : this.alignIdList ) {
                for ( Long otherId : other.alignIdList ) {
                    if ( id.equals(otherId)) {
                        return true;
                    }
                }
            }
            return false;
        }
        
        public void merge(Change other ) {
            this.alignIdList.addAll( other.alignIdList );
            this.range = new Range(this.range.getStart(), other.range.getEnd() );
        }

        
        @Override
        public int compareTo(Change that) {
            if ( this.range.getStart() < that.range.getStart() ) {
                return -1;
            } else if ( this.range.getStart() > that.range.getStart() ) {
                return 1;
            } else {
                if ( this.range.getEnd() < that.range.getEnd() ) {
                    return -1;
                } else if ( this.range.getEnd() > that.range.getEnd() ) {
                    return 1;
                } 
            }
            return 0;
        }
        
        @Override
        public String toString() { 
            return "AlignIDs: "+this.alignIdList+" range: "+this.range.toString();
        }
    }
}
