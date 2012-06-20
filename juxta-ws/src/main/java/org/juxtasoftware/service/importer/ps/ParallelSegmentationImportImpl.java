package org.juxtasoftware.service.importer.ps;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.xml.parsers.ParserConfigurationException;

import org.juxtasoftware.dao.CacheDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.NoteDao;
import org.juxtasoftware.dao.PageBreakDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Note;
import org.juxtasoftware.model.PageBreak;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.model.Workspace;
import org.juxtasoftware.service.ComparisonSetCollator;
import org.juxtasoftware.service.Tokenizer;
import org.juxtasoftware.service.importer.ImportService;
import org.juxtasoftware.service.importer.JuxtaXsltFactory;
import org.juxtasoftware.service.importer.XmlTemplateParser;
import org.juxtasoftware.service.importer.XmlTemplateParser.TemplateInfo;
import org.juxtasoftware.service.importer.jxt.Util;
import org.juxtasoftware.service.importer.ps.WitnessParser.WitnessInfo;
import org.juxtasoftware.util.BackgroundTaskSegment;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.collect.Maps;

import eu.interedition.text.Range;
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
    @Autowired private CacheDao cacheDao;
    @Autowired private PageBreakDao pageBreakDao;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private WorkspaceDao workspaceDao;
    @Autowired private TextRepository textRepository;
    @Autowired private Tokenizer tokenizer;
    @Autowired private ComparisonSetCollator collator;
    @Autowired private WitnessParser witnessParser;
    @Autowired private XmlTemplateParser templateParser;
    @Autowired private JuxtaXsltFactory xsltFactory;
        
    private ComparisonSet set;
    private BackgroundTaskStatus taskStatus = null;
    private BackgroundTaskSegment taskSegment = null;
    private List<WitnessInfo> listWitData = new ArrayList<WitnessInfo>();
    private boolean deferCollation = false;
    
    private static final Logger LOG = LoggerFactory.getLogger( ImportService.class.getName());
    
    public ParallelSegmentationImportImpl() {  
    }
    
    public void reimportSource(ComparisonSet set, Source importSrc) throws Exception {
        this.deferCollation = true;
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
            if ( this.deferCollation ) {
                numSteps = 3;
            }
            this.taskSegment = this.taskStatus.add(1, new BackgroundTaskSegment( numSteps ));
        }
        
        LOG.info("Import parallel segmented document into '"+this.set.getName()+"'");
        
        prepareSet();
        extractWitnessIdentifiers( importSrc );
        parseSource( importSrc );
        
        if ( this.deferCollation == false ) {
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
        Set<Witness> witnesses = this.setDao.getWitnesses(this.set);
        if ( witnesses.size() == 0) {
            return;
        }
        
        // clear out all prior data (NOTE: delete all witnesses wil also clear out all
        // aligment and annotation data )
        this.setDao.deleteAllWitnesses(this.set);
        this.cacheDao.deleteAll(this.set.getId());
        try {
            for (Witness witness : witnesses) {
                this.witnessDao.delete(witness);
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
            this.taskStatus.setNote(msg);
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
        
        // parse the TEI template. There is only 1 template in this file
        // so it is safe to get the first one in the resultant list. Use this
        // to create a tei ps parser instance
        this.templateParser.parse( ClassLoader.getSystemResourceAsStream("tei-template.xml") );
        TemplateInfo teiInfo = this.templateParser.getTemplates().get(0);
        JuxtaXslt jxXslt = this.xsltFactory.create(teiSource.getWorkspaceId(), teiSource.getName(), teiInfo);
        
        
        // run the src text thru the parser for multiple passes
        // once for each witness listed in the listWit tag.
        Set<Witness> witnesses = new HashSet<Witness>();
        for ( WitnessInfo info : this.listWitData ) {
            
            setStatusMsg("Parse WitnessID "+info.getGroupId()+" - '"+info.getDescription()+"' from source");
            ParallelSegmentationParser parser = new ParallelSegmentationParser( teiInfo.getExcludes(), teiInfo.getLineBreaks());
            parser.parse(this.sourceDao.getContentReader(teiSource), info );
            Text witnessTxt = parser.getWitnessText();
            Witness witness = createWitness( ws, teiSource, jxXslt, witnessTxt, info );
            witnesses.add(witness);
            for (Note note : parser.getNotes()  ) {
                note.setWitnessId(witness.getId());
            }
            this.noteDao.create(parser.getNotes());
            
            for (PageBreak pb : parser.getPageBreaks()  ) {
                pb.setWitnessId(witness.getId());
            }
            this.pageBreakDao.create(parser.getPageBreaks());
        }
        
        // add all witnesses to the set
        setStatusMsg("Create comparison set");
        this.setDao.addWitnesses(this.set, witnesses);
        this.setDao.update(this.set);
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
    Witness createWitness(Workspace ws, Source source, JuxtaXslt jxXslt, Text witnessTxt, WitnessInfo info ) throws Exception{  
        Witness witness = new Witness();
        witness.setName( info.getName() );
        witness.setSourceId( source.getId());
        witness.setXsltId(jxXslt.getId());
        witness.setWorkspaceId( this.set.getWorkspaceId() );
        witness.setText(witnessTxt);
        Long id = this.witnessDao.create(witness);
        witness.setId( id );
        return witness;
    }
    
    /**
     * Aml parser to read TEI parallel segmentaton
     * @author loufoster
     *
     */
    private class ParallelSegmentationParser extends DefaultHandler  {
        private Note currNote = null;
        private StringBuilder currNoteContent;
        private StringBuilder witnessContent;
        private List<Note> notes = new ArrayList<Note>();
        private List<PageBreak> breaks = new ArrayList<PageBreak>();
        private Map<String, Range> identifiedRanges = Maps.newHashMap();
        private Set<String> exclusions;
        private Set<String> lineBeaks;
        private WitnessInfo witnessInfo;
        private long currPos = 0;
        private boolean isExcluding = false;
        private Stack<String> exclusionContext = new Stack<String>();
        private Stack<String> xmlIdStack = new Stack<String>();

        public ParallelSegmentationParser(final Set<String> exclude, final Set<String> breaks ) {
            this.lineBeaks = breaks;
            this.exclusions = exclude;
        }
        
        public void parse(final Reader sourceReader, WitnessInfo witnessInfo ) throws SAXException, IOException {          
            this.witnessInfo = witnessInfo;
            this.witnessContent = new StringBuilder();
            Util.saxParser().parse( new InputSource( sourceReader ), this);
        }
        
        public List<Note> getNotes() {
            return this.notes;
        }
        public List<PageBreak> getPageBreaks() {
            return this.breaks;
        }
        public Text getWitnessText() throws IOException {
            return textRepository.create( new StringReader(this.witnessContent.toString()));
        }
        
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if ( this.isExcluding ) {
                this.exclusionContext.push(qName);
                return;
            }
            
            if ( isSpecialTag(qName) == false && this.exclusions.contains(qName)) {
                this.isExcluding = true;
                this.exclusionContext.push(qName);
            }
                 
            if (qName.equals("rdg") || qName.equals("lem")) { 
                if ( matchesTargetWitness( attributes ) == false ) {
                    // not the witness we care about. Exclude it!
                    this.isExcluding = true;
                    this.exclusionContext.push(qName);
                }
            } else if ( qName.equals("note") ) {
                handleNote(attributes);
            } else if (qName.equals("pb") ) {
                handlePageBreak(attributes);
            } else {
                final String idVal = getAttributeValue("id",attributes);
                if ( idVal != null ) {
                    this.identifiedRanges.put(idVal, new Range(this.currPos, this.currPos));
                    this.xmlIdStack.push(idVal);
                } else  {
                    this.xmlIdStack.push("NA");
                }
            }
        }
        
        private boolean matchesTargetWitness(Attributes attributes) {
            
            // get the value of the wit or lem attribute (only 1 should be present)
            String idAttr = getAttributeValue("wit", attributes);
            if ( idAttr == null ) {
                idAttr = getAttributeValue("lem", attributes);
                if ( idAttr == null ) {
                    return false;
                }
            }
            
            // wit/lem ids are prefixed with # and separated by space.
            // Strip the # and break up into tokens. See if one of the
            // IDs matches the target ID for this parser pass.
            idAttr = idAttr.replaceAll("#", "");
            String[] ids = idAttr.split(" ");
            for ( int i=0; i<ids.length; i++) {
                String id = ids[i].trim();
                if ( id.equals(this.witnessInfo.getId()) || 
                     id.equals(this.witnessInfo.getGroupId()) ) {
                    return true;
                }
            }
            return false;
        }

        private boolean isSpecialTag( final String qName ) { 
            return (qName.equals("note") || qName.equals("pb"));
        }
        
        private void handleNote(Attributes attributes) {
            this.currNote = new Note();
            this.currNote.setAnchorRange(new Range(this.currPos, this.currPos));
            this.currNoteContent = new StringBuilder();

            // search note tag attributes for type and target and add them to the note.
            for (int idx = 0; idx<attributes.getLength(); idx++) {  
                String name = attributes.getQName(idx);
                if ( name.contains(":")) {
                    name = name.split(":")[1];
                }
                if ("type".equals(name)) {
                    this.currNote.setType(attributes.getValue(idx));
                } else if ("target".equals(name)) {
                    this.currNote.setTargetID(attributes.getValue(idx));
                }
            }
            this.notes.add(this.currNote);
        }

        private void handlePageBreak(Attributes attributes) {
            PageBreak pb = new PageBreak();
            pb.setOffset(this.currPos);
            
            for (int idx = 0; idx<attributes.getLength(); idx++) {  
                String name = attributes.getQName(idx);
                if ( name.contains(":")) {
                    name = name.split(":")[1];
                }
                if ("n".equals(name)) {
                    pb.setLabel( attributes.getValue(idx) );
                } 
            }
            this.breaks.add(pb);
        }

        private String getAttributeValue( final String name, final Attributes attributes ){
            for (int idx = 0; idx<attributes.getLength(); idx++) {  
                String val = attributes.getQName(idx);
                if ( val.contains(":")) {
                    val = val.split(":")[1];
                }
                if ( val.equals(name)) {
                    return attributes.getValue(idx);
                }
            }
            return null;
        }
        
        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if ( this.isExcluding ) {
                this.exclusionContext.pop();
                this.isExcluding = !this.exclusionContext.empty();
                return;
            }
            
            if (qName.equals("note")) {
                this.currNote.setContent(this.currNoteContent.toString().replaceAll("\\s+", " ").trim());
                if ( this.currNote.getContent().length() == 0 ) {
                    this.notes.remove(this.currNote);
                }
                this.currNote = null;
                this.currNoteContent = null;
            } else if (qName.equals("pb")) {
                // pagebreaks always include a linebreak. add 1 to
                // current position to account for this
                if ( this.currNote == null ) {
                    this.currPos++;
                    this.witnessContent.append("\n");
                }
            } else {
                // if the tag has an identifier, save it off for crossreference with targeted notes
                if ( this.xmlIdStack.empty() == false ) {
                    final String xmlId = this.xmlIdStack.pop();
                    if (xmlId.equals("NA") == false ) {
                        this.identifiedRanges.put(xmlId, new Range(this.identifiedRanges.get(xmlId).getStart(), this.currPos));
                    }
                }
                
                // if this tag is in the midst of a note, check it for 
                // linebreaks and add a hard break now. Also, do NOT
                // increment position count if we are collecting a note.
                if ( this.currNote != null ) {
                    if ( this.lineBeaks.contains(qName) ){ 
                        this.currNoteContent.append("<br/>");
                    }
                } else  if ( this.lineBeaks.contains(qName) ){
                    this.currPos++;
                    this.witnessContent.append("\n");
                }  
            }            
        }
        
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if ( this.isExcluding == false ) {
                String txt = new String(ch, start, length);
               
                // remove last newline and trailing space (right trim)
                txt = txt.replaceAll("[\\n]\\s*$", "");
                
                // remove first newline and traiing whitespace.
                // this will leave any leading whitespace before the 1st newline
                txt = txt.replaceAll("[\\n]\\s*", "");
                
                if ( this.currNote != null ) {
                    this.currNoteContent.append(txt);
                } else  {
                    this.witnessContent.append(txt);
                    this.currPos += txt.length();
                }
            }
        }
        
        @Override
        public void endDocument() throws SAXException {
            // at the end of parsing, find all notes that have a target
            // specified. Look up that id and set the associated range
            // as the note anchor point
            for ( Note note : this.notes ) {
                String noteTargetId = note.getTargetID();
                if ( noteTargetId != null && noteTargetId.length() > 0){
                    Range tgtRange = this.identifiedRanges.get(noteTargetId);
                    if ( tgtRange != null ) {
                        note.setAnchorRange( tgtRange );
                    }
                }
            }
            //System.err.println(this.witnessContent);
        }
    }
}
