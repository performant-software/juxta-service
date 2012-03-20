package org.juxtasoftware.service.importer.ps;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.juxtasoftware.Constants;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.CacheDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.NoteDao;
import org.juxtasoftware.dao.PageBreakDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.TemplateDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Note;
import org.juxtasoftware.model.PageBreak;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Template;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.model.Workspace;
import org.juxtasoftware.service.ComparisonSetCollator;
import org.juxtasoftware.service.Tokenizer;
import org.juxtasoftware.service.importer.ImportService;
import org.juxtasoftware.service.importer.ps.WitnessParser.WitnessInfo;
import org.juxtasoftware.util.BackgroundTaskSegment;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import eu.interedition.text.Text;
import eu.interedition.text.xml.XMLParser;

/**
 * Import service implementation for reading in a TEI parallel segmented
 * xml file, recreating a comparison set with all witnesse, and populating
 * it with all annotations and diff alignments.
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ParallelSegmentationImportImpl implements ImportService<Source> {

    @Autowired private TemplateDao templateDao;
    @Autowired private SourceDao sourceDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private NoteDao noteDao;
    @Autowired private CacheDao cacheDao;
    @Autowired private PageBreakDao pageBreakDao;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private WorkspaceDao workspaceDao;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private Tokenizer tokenizer;
    @Autowired private ComparisonSetCollator collator;
    @Autowired private WitnessParser witnessParser;
    @Autowired private XMLParser xmlParser;
        
    private Workspace ws;
    private ComparisonSet set;
    private BackgroundTaskStatus taskStatus;
    private BackgroundTaskSegment taskSegment;
    private List<WitnessInfo> listWitData = new ArrayList<WitnessInfo>();
    
    private static final Logger LOG = LoggerFactory.getLogger( ImportService.class.getName());
    
    public ParallelSegmentationImportImpl() {  
    }
    
    @Override
    public void doImport(ComparisonSet set, Source importSrc, BackgroundTaskStatus status)
        throws Exception {
        
        // save key data for use later
        this.set = set;
        this.taskStatus = status;
        this.ws = this.workspaceDao.find(this.set.getWorkspaceId());
        
        // set up the number of segments in the task
        final int numSteps = 5;
        this.taskSegment = this.taskStatus.add(1, new BackgroundTaskSegment( numSteps ));
        
        LOG.info("Import parallel segmented document into '"+this.set.getName()+"'");
        
        prepareSet();
        extractWitnessIdentifiers( importSrc );
        parseSource( importSrc );
        
        // tokenize 
        CollatorConfig cfg = this.setDao.getCollatorConfig(this.set);
        tokenize(cfg);
        collate( cfg );
        
        this.taskSegment.incrementValue();
        
        this.taskStatus.setNote("Import successful");
    }
    
    /**
     * Prepare the set to receive new data - clear out
     * old cache and witnesses
     */
    private void prepareSet() {
        // grab all witnesses associated with this set.
        // If there are none, there is nothing more to do
        Set<Witness> witnesses = this.setDao.getWitnesses(this.set);
        if ( witnesses.size() == 0) {
            return;
        }
        
        // clear out all prior data 
        this.setDao.update(this.set);
        this.setDao.deleteAllWitnesses(this.set);
        this.cacheDao.deleteHeatmap(set.getId());
        this.alignmentDao.clear(this.set, true); // true to force clear ALL for this set
        try {
            Source teiSource = null;
            for (Witness witness : witnesses) {
                if ( teiSource == null ) {
                   teiSource = this.sourceDao.find(this.ws.getId(), witness.getSourceId());
                }
                this.witnessDao.delete(witness);
            }

            this.sourceDao.delete( teiSource );
        } catch (Exception e) {
            throw new RuntimeException("Unable to overwrite set; witnesses are in use in another set.");
        }
    }
    
    /**
     * Collate the comparison set
     * @throws IOException
     */
    private void collate( CollatorConfig cfg ) throws IOException {
        this.taskStatus.setNote("Collating comparison set");
        this.collator.collate(this.set, cfg, this.taskStatus);
        this.taskSegment.incrementValue();
    }
    
    /**
     * Tokenize the comparison set
     * @param cfg  
     * @throws IOException
     */
    private void tokenize( CollatorConfig cfg ) throws IOException {
        this.taskStatus.setNote("Tokenizing comparison set");
        this.tokenizer.tokenize(this.set, cfg, this.taskStatus);
        this.taskSegment.incrementValue();
    }
    
    /**
     * Scan the source data from witList data and generate a 
     * list of witnesses included in the file
     * 
     * @param importStream
     * @throws IOException 
     * @throws SAXException 
     * @throws ParserConfigurationException 
     */
    private void extractWitnessIdentifiers(Source teiSource) throws ParserConfigurationException, SAXException, IOException {
        this.taskStatus.setNote("Extract witness information");
        Reader r = this.sourceDao.getContentReader(teiSource);
        this.witnessParser.parse( r );
        this.listWitData = this.witnessParser.getWitnesses();
        this.taskSegment.incrementValue();
    }

    /**
     * Run the TEI PS source thru the xml parser
     * and collect witness/diff info
     * 
     * @param teiSource
     * @throws Exception 
     */
    private void parseSource(Source teiSource ) throws Exception {
        this.taskStatus.setNote("Parse "+set.getName());
        Workspace ws = this.workspaceDao.getPublic();
        Template template = this.templateDao.find(ws, Constants.PARALLEL_SEGMENTATION_TEMPLATE);
        
        // run the src text thru the parser for multiple passes
        // once for each witness listed in the listWit tag.
        Set<Witness> witnesses = new HashSet<Witness>();
        for ( WitnessInfo info : this.listWitData ) {
            
            this.taskStatus.setNote("Parse WitnessID "+info.getGroupId()+" - '"+info.getDescription()+"' from source");
            PsXmlParserConfig cfg = new PsXmlParserConfig( template, info );
            Text witnessTxt = this.xmlParser.parse(teiSource.getText(), cfg);
            Witness witness = createWitness( ws, teiSource, template, witnessTxt, info );
            witnesses.add(witness);
            if ( cfg.notesIncluded()  ) {
                writeNotes(witness, cfg.getNotes() );
            }
            if ( cfg.pageBreaksIncluded()) {
                writePageBreaks(witness, cfg.getPageBreaks() );
            }
        }
        
        // add all witnesses to the set
        this.taskStatus.setNote("Create comparison set");
        this.setDao.addWitnesses(this.set, witnesses);
        this.setDao.update(this.set);
        this.taskSegment.incrementValue();
        
        this.taskSegment.incrementValue();
        
    }
    
    Witness createWitness(Workspace ws, Source source, Template template, Text witnessTxt, WitnessInfo info ) throws Exception{
        if ( this.witnessDao.exists(ws, info.getName())) {
            throw new Exception("Witness '"+info.getName()+"' already exists in workspace '"+ws.getName()+"'");
        }
        Witness witness = new Witness();
        witness.setName( info.getName() );
        witness.setSourceId( source.getId());
        witness.setTemplateId(template.getId());
        witness.setText(witnessTxt);
        witness.setWorkspaceId( this.set.getWorkspaceId() );
        Long id = this.witnessDao.create(witness);
        witness.setId( id );
        return witness;
    }
    
    public void writeNotes( final Witness w, List<Note> notes) {
        if (!notes.isEmpty()) {
            for (Note note : notes) {
                note.setWitnessId(w.getId());
            }
            this.noteDao.create(notes);
        }
    }
    
    public void writePageBreaks( final Witness w, List<PageBreak> breaks) {
        if ( breaks.isEmpty() == false ) {
            for ( PageBreak pb : breaks ) {
                pb.setWitnessId(w.getId());
            }
            this.pageBreakDao.create(breaks);
        }
    }
}
