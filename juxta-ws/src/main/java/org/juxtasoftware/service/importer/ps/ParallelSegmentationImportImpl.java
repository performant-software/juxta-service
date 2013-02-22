package org.juxtasoftware.service.importer.ps;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.json.simple.JSONObject;
import org.juxtasoftware.Constants;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.dao.NoteDao;
import org.juxtasoftware.dao.PageMarkDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Note;
import org.juxtasoftware.model.PageMark;
import org.juxtasoftware.model.RevisionInfo;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.model.Workspace;
import org.juxtasoftware.service.ComparisonSetCollator;
import org.juxtasoftware.service.JuxtaTagExtractor;
import org.juxtasoftware.service.Tokenizer;
import org.juxtasoftware.service.importer.ImportService;
import org.juxtasoftware.service.importer.JuxtaXsltFactory;
import org.juxtasoftware.service.importer.XmlTemplateParser;
import org.juxtasoftware.service.importer.XmlTemplateParser.TemplateInfo;
import org.juxtasoftware.service.importer.ps.WitnessParser.PsWitnessInfo;
import org.juxtasoftware.util.BackgroundTaskSegment;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.juxtasoftware.util.NamespaceExtractor.NamespaceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import eu.interedition.text.Text;
import eu.interedition.text.TextRepository;

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

    @Autowired private SourceDao sourceDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private NoteDao noteDao;
    @Autowired private PageMarkDao pageBreakDao;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private WorkspaceDao workspaceDao;
    @Autowired private TextRepository textRepository;
    @Autowired private Tokenizer tokenizer;
    @Autowired private ComparisonSetCollator collator;
    @Autowired private WitnessParser witnessParser;
    @Autowired private XmlTemplateParser templateParser;
    @Autowired private JuxtaXsltFactory xsltFactory;
    @Autowired private JuxtaXsltDao xsltDao;
        
    private ComparisonSet set;
    private BackgroundTaskStatus taskStatus = null;
    private BackgroundTaskSegment taskSegment = null;
    private List<PsWitnessInfo> listWitData = new ArrayList<PsWitnessInfo>();
    private boolean isReImport = false;
    private Long originalXsltId = null;
    
    private static final Logger LOG = LoggerFactory.getLogger( Constants.WS_LOGGER_NAME );
    
    public ParallelSegmentationImportImpl() {  
    }
    
    public void reimportSource(ComparisonSet set, Source importSrc) throws Exception {
        this.isReImport = true;
        doImport(set, importSrc, null);
    }
    
    @Override
    public void doImport(ComparisonSet set, Source importSrc, BackgroundTaskStatus status)
        throws Exception {
        
        // save key data for use later
        this.set = set;
        this.taskStatus = status;
        
        if ( this.taskStatus != null ) {
            // set up the number of segments in the task
            int numSteps = 5;
            if ( this.isReImport ) {
                numSteps = 3;
            }
            this.taskSegment = this.taskStatus.add(1, new BackgroundTaskSegment( numSteps ));
        }
        
        LOG.info("Import parallel segmented document into '"+this.set.getName()+"'");
        
        prepareSet();
        extractWitnessIdentifiers( importSrc );
        parseSource( importSrc );
        
        if ( this.isReImport == false ) {
            set.setStatus(ComparisonSet.Status.COLLATING);
            this.setDao.update(this.set);
            
            CollatorConfig cfg = this.setDao.getCollatorConfig(this.set);
            tokenize(cfg);
            collate( cfg );
            
            set.setStatus(ComparisonSet.Status.COLLATED);
            this.setDao.update(this.set);
        }
        
        setStatusMsg("Import successful");
    }
    
    /**
     * Prepare the set to receive new data - clear out
     * old cache and witnesses
     */
    private void prepareSet() {
        // grab all witnesses associated with this set.
        // If there are none, there is nothing more to do
        List<Witness> witnesses = this.setDao.getWitnesses(this.set);
        if ( witnesses.size() == 0) {
            return;
        }
        
        // clear out all prior witness and supporting data 
        this.setDao.clearCollationData(this.set);
        try {
            for (Witness witness : witnesses) {
                if ( this.originalXsltId == null ) {
                    this.originalXsltId = witness.getXsltId();
                }
                this.noteDao.deleteAll( witness.getId() );
                this.pageBreakDao.deleteAll( witness.getId() );
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to overwrite set; witnesses are in use in another set.");
        }
    }
    
    private void incrementStatus() {
        if ( this.taskSegment != null ) {
            this.taskSegment.incrementValue();
        }
    }
    
    private void setStatusMsg( final String msg ) {
        if (this.taskStatus != null ) {
            this.taskStatus.setNote( JSONObject.escape(msg) );
        }
    }
    
    /**
     * Collate the comparison set
     * @throws IOException
     */
    private void collate( CollatorConfig cfg ) throws IOException {
        setStatusMsg("Collating comparison set");
        this.collator.collate(this.set, cfg, this.taskStatus);
        incrementStatus();
    }
    
    /**
     * Tokenize the comparison set
     * @param cfg  
     * @throws IOException
     */
    private void tokenize( CollatorConfig cfg ) throws IOException {
        setStatusMsg("Tokenizing comparison set");
        this.tokenizer.tokenize(this.set, cfg, this.taskStatus);
        incrementStatus();
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
        setStatusMsg("Extract witness information");
        Reader r = this.sourceDao.getContentReader(teiSource);
        this.witnessParser.parse( r );
        this.listWitData = this.witnessParser.getWitnesses();
        incrementStatus();
    }

    /**
     * Run the TEI PS source thru the xml parser
     * and collect witness/diff info
     * 
     * @param teiSource
     * @throws Exception 
     */
    private void parseSource(Source teiSource ) throws Exception {
        setStatusMsg("Parse "+set.getName());
        Workspace ws = this.workspaceDao.find(this.set.getWorkspaceId());
        
        JuxtaXslt jxXslt = null;
        if ( this.originalXsltId == null ) {
            // parse the TEI template. There is only 1 template in this file
            // so it is safe to get the first one in the resultant list. Use this
            // to create a tei ps parser instance
            this.templateParser.parse( ClassLoader.getSystemResourceAsStream("tei-template.xml") );
            TemplateInfo teiInfo = this.templateParser.getTemplates().get(0);
            
            // this has to be TEI. Make up a default TEI namespace for use in tag extraction
            NamespaceInfo ns = NamespaceInfo.createDefaultNamespace("http://www.tei-c.org/ns/1.0");
            ns.setDefaultPrefix("tei");
            jxXslt = this.xsltFactory.createFromTemplateInfo(teiSource.getWorkspaceId(), teiSource.getName(), teiInfo, ns);
        } else {
            jxXslt = this.xsltDao.find(this.originalXsltId);
        }
        
        // run the src text thru the parser for multiple passes
        // once for each witness listed in the listWit tag.
        Set<Witness> witnesses = new HashSet<Witness>();
        for ( PsWitnessInfo info : this.listWitData ) {
            
            setStatusMsg("Parse WitnessID "+info.getGroupId()+" - '"+info.getDescription()+"' from source");
            JuxtaTagExtractor extractor = new JuxtaTagExtractor();
            extractor.setPsTargetWitness(info);
            extractor.extract(this.sourceDao.getContentReader(teiSource), jxXslt );
            Text witnessTxt = this.textRepository.create( new StringReader(extractor.getPsWitnessContent()));
            Witness witness = createWitness( ws, teiSource, jxXslt, witnessTxt, info );
            witnesses.add(witness);
            for (Note note : extractor.getNotes()  ) {
                note.setWitnessId(witness.getId());
            }
            this.noteDao.create(extractor.getNotes());
            
            for (PageMark pb : extractor.getPageMarks()  ) {
                pb.setWitnessId(witness.getId());
            }
            this.pageBreakDao.create(extractor.getPageMarks());
            
            for (RevisionInfo rev : extractor.getRevisions()  ) {
                rev.setWitnessId(witness.getId());
            }
            this.witnessDao.addRevisions(  extractor.getRevisions() );
        }
        
        // add all witnesses to the set
        if ( this.isReImport == false ) {
            setStatusMsg("Populate comparison set");
            this.setDao.addWitnesses(this.set, witnesses);
            this.setDao.update(this.set);
        }
        incrementStatus();
    }
    
    /**
     * Create a new witness if none was pre-existing. Update the text content
     * of one that already existed.
     * 
     * @param ws
     * @param source
     * @param jxXslt 
     * @param template
     * @param witnessTxt
     * @param info
     * @return
     * @throws Exception
     */
    Witness createWitness(Workspace ws, Source source, JuxtaXslt jxXslt, Text witnessTxt, PsWitnessInfo info ) throws Exception{  
        Witness witness = null;
        for (Witness testWit : this.setDao.getWitnesses(this.set) ) {
            if ( testWit.getName().equals(info.getName())) {
                witness = testWit;
                break;
            }
        }
        
        if ( witness == null ) {
            
            // prevent duplicate witnesses from being created
            if ( this.witnessDao.exists(ws, info.getName()) ) {
                throw new Exception("Witness '"+info.getName()+"' already exists");
            }
            
            witness = new Witness();
            witness.setName( info.getName() );
            witness.setSourceId( source.getId());
            witness.setXsltId(jxXslt.getId());
            witness.setWorkspaceId( this.set.getWorkspaceId() );
            witness.setText(witnessTxt);
            Long id = this.witnessDao.create(witness);
            witness.setId( id );
        } else {
            this.witnessDao.updateContent(witness, witnessTxt);
        }

        return witness;
    }
}
