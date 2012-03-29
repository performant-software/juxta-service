package org.juxtasoftware.service.importer.jxt;

import static org.juxtasoftware.service.importer.jxt.Util.isContainedIn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLStreamException;

import org.juxtasoftware.Constants;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.CacheDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaAnnotationDao;
import org.juxtasoftware.dao.RevisionDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.TemplateDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaAnnotation;
import org.juxtasoftware.model.RevisionSet;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Template;
import org.juxtasoftware.model.Template.TagAction;
import org.juxtasoftware.model.Template.WildcardQName;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.model.Workspace;
import org.juxtasoftware.service.ComparisonSetCollator;
import org.juxtasoftware.service.SourceTransformer;
import org.juxtasoftware.service.Tokenizer;
import org.juxtasoftware.service.XmlTemplateParser;
import org.juxtasoftware.service.importer.ImportService;
import org.juxtasoftware.service.importer.jxt.ManifestParser.SourceInfo;
import org.juxtasoftware.service.importer.jxt.MovesParser.JxtMoveInfo;
import org.juxtasoftware.util.BackgroundTaskSegment;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

import eu.interedition.text.NameRepository;

/**
 * Service used to import JXT files from the desktop juxta. It supports only the 
 * latest version of juxta desktop (1.6.2). There is no support for target xpath
 * in the parse templates. For imports of documents using the juxta-document template,
 * the biblio data is stripped out to help compensate for this.
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class JxtImportServiceImpl implements ImportService<InputStream> {
    @Autowired private ManifestParser manifestParser;
    @Autowired private MovesParser movesParser;
    @Autowired private XmlTemplateParser templateParser;
    @Autowired private SourceDao sourceDao;
    @Autowired private TemplateDao templateDao;
    @Autowired private NameRepository nameRepo;
    @Autowired private CacheDao cacheDao;
    @Autowired private SourceTransformer transformer;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private RevisionDao revisionsDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private Tokenizer tokenizer;
    @Autowired private ComparisonSetCollator collator;
    @Autowired private JuxtaAnnotationDao annotationDao;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private WorkspaceDao workspaceDao;
    
    private Workspace ws;
    private ComparisonSet set;
    private BackgroundTaskStatus taskStatus;
    private BackgroundTaskSegment taskSegment;
    

    /**
     * Import a JXT file from juxta version 1.6 or greater. Older versions will cause this method
     * to throw 
     * @param set
     * @return
     */
    @Override
    public void doImport(final ComparisonSet set,final InputStream jxtIs, BackgroundTaskStatus status) throws Exception {
        final File sessionDataDir = Files.createTempDir();
        
        // stuff key data into class members
        this.set = set;
        this.taskStatus = status;
        this.ws = this.workspaceDao.find(this.set.getWorkspaceId());
        
        // segment the status tracker to match the steps in the import process:
        //  unzip, parse manifest, parse moves, parse templates, populate set, 
        //  add transpositions, tokenize, collate
        final int importSteps = 8;
        this.taskSegment = this.taskStatus.add(1, new BackgroundTaskSegment( importSteps ));

        try {
            // unzip the jxt file into a temp directory
            this.taskStatus.setNote("Inflating JXT data");
            unzip(sessionDataDir, jxtIs);
            this.taskSegment.incrementValue();
            
            // parse the manifest found in the unzipped
            // directory into a list of source data
            List<SourceInfo> sources = parseManifest(sessionDataDir);
            
            // parse out any transpositions
            List<JxtMoveInfo> moves = parseMoves(sessionDataDir);
            
            // Grab the associated templates and parse them out into a map.
            // this map is held internally in the template parser.
            parseTemplates(sessionDataDir);
                      
            // combine all of this data into the comparison set
            prepareSet( );
            populateSet( sources, moves );
            
            // add in the transpositions!
            addTranspositions( moves );
                        
            // tokenize and collate
            CollatorConfig cfg = this.setDao.getCollatorConfig(this.set);
            tokenize( cfg );
            collate( cfg );
            
            this.taskStatus.setNote("Import successful");
            
        } finally {
            try {
                Files.deleteRecursively(sessionDataDir.getCanonicalFile());
            } catch (IOException e) {}
        }
    }
    
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
        this.cacheDao.deleteHeatmap(this.set.getId());
        try {
            for (Witness witness : witnesses) {
                Source s = this.sourceDao.find(this.ws.getId(), witness.getSourceId());
                this.witnessDao.delete(witness);
                this.revisionsDao.deleteSourceRevisionSets(s.getId());
                this.sourceDao.delete(s);
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to overwrite set; witnesses are in use in another set.");
        }
    }
    
    private void addTranspositions(List<JxtMoveInfo> moves) {
        this.taskStatus.setNote("Adding Transpositions");
        
        List<Alignment> moveLinks = new ArrayList<Alignment>();
        for ( JxtMoveInfo move : moves ) {
            Alignment link = new Alignment();
            link.setComparisonSetId(this.set.getId());
            link.setManual();
            link.setName( this.nameRepo.get(Constants.TRANSPOSITION_NAME) );
            
            for ( String title : move.getWitnessTitles() ) {
                Witness witness = this.witnessDao.find(this.set, title);
                JuxtaAnnotation anno = new JuxtaAnnotation(witness, Constants.TRANSPOSITION_NAME, move.getWitnessRange(title) );
                Long annoId = this.annotationDao.create(anno);
                AlignedAnnotation aa =  new AlignedAnnotation(anno.getName(), witness.getId(), annoId, anno.getRange());
                link.addAnnotation( aa );
            }
            moveLinks.add(link);
        }
        this.alignmentDao.create(moveLinks);
        
        this.taskSegment.incrementValue();
        
    }

    /**
     * Parse a list of <code>SourceInfo</code> objects from the manifest file
     * @param sessionDataDir
     * @return
     * @throws Exception
     */
    private List<SourceInfo> parseManifest( File sessionDataDir) throws Exception {
        this.taskStatus.setNote("Parsing manifest");
        final File manifest = new File(sessionDataDir, "manifest.xml");
        if (!manifest.isFile()) {
            throw new IOException("No manifest.xml");
        }
        this.manifestParser.parse(this.set, sessionDataDir, manifest);
        List<SourceInfo> sources = this.manifestParser.getSources();
        this.taskSegment.incrementValue();
        return sources;    
    }
    
    /**
     * Parse a list of <code>MoveInfo</code> objects from the moves file
     * @param sessionDataDir
     * @return
     * @throws Exception
     */
    private List<JxtMoveInfo> parseMoves( File sessionDataDir) throws Exception {
        this.taskStatus.setNote("Parsing moves");
        final File movesFile = new File(sessionDataDir, "moves.xml");
        if (!movesFile.isFile()) {
            throw new IOException("No moves.xml");
        }
        List<JxtMoveInfo> moves = this.movesParser.parse(this.set, movesFile);
        this.taskSegment.incrementValue();
        return moves;    
    }
    
    /**
     * Parse out all parse templates from the templates.xml file.
     * @param sessionDataDir
     * @throws Exception
     */
    private void parseTemplates( File sessionDataDir ) throws Exception {
        this.taskStatus.setNote("Parsing templates");
        final File templates = new File(sessionDataDir, "templates.xml");
        if (!templates.isFile()) {
            throw new IOException("No templates.xml");
        }
        this.templateParser.parse( new FileInputStream(templates ) );
        this.taskSegment.incrementValue();
    }
    
      
    /**
     * Create all sources, templates and witnesses and use them to populate the 
     * comparison set with the new sources. Update the base document too.\
     * 
     * @param sources
     * @throws Exception
     */
    private void populateSet( List<SourceInfo> sources, List<JxtMoveInfo> moves ) throws Exception {
        
        // Use collected data to create soures, templates, witness and
        // add them all to the comparison set
        Set<Witness> witnesses = new HashSet<Witness>();
        this.taskStatus.setNote("Adding witnesses to comparison set");
        for ( SourceInfo srcInfo : sources ) {

            // the only way a source can have no template is if it 
            // is a plain text file instead of xml. 
            Template parseTemplate = null;
            String srcName = srcInfo.getSrcFile().getName();
            this.taskStatus.setNote("Adding raw source document: "+srcName); 
            int extPos = srcName.lastIndexOf('.');
            String ext = ".txt";
            if ( extPos > -1 ) {
                ext = srcName.substring(extPos);
            }
            boolean isXml = !ext.equalsIgnoreCase(".txt");
            if ( isXml ) {
                parseTemplate = this.templateParser.findTemplate( srcInfo.getTemplateGuid() );
            }
            
            // create the juxta source (must always create a new one)
            // the reson: juxta desktop allows user to alter raw xml
            // also, others may have grabbed this source for other purposes
            Source source = createSource(srcInfo, isXml);
            
            // record any accepted revisions this witness may have had
            List<Integer> acceptedRevs = srcInfo.getAcceptedRevsions();
            RevisionSet revSet = null;
            if ( acceptedRevs.size() > 0) {
                revSet = new RevisionSet();
                revSet.setName(this.set.getName()+"-src-"+source.getId());
                revSet.setSourceId( source.getId() );
                revSet.setRevisionIndexes(acceptedRevs);
                Long id = this.revisionsDao.createRevisionSet(revSet);
                revSet.setId(id);
            }
            
            // if the source was associated with a parse template,
            // create it and use it to transform to a witness
            this.taskStatus.setNote("Transform raw "+srcName+" into witness");
            Long witnessId = null;
            if ( parseTemplate != null ) {
                
                // imports can never bet set as default and names
                // are prefixed with the set name. Only create 1x
                if ( parseTemplate.getId() == null ) {
                    // juxta docs have a bunch of biblio noise up front. this
                    // as handled with a root xpath, but this is no longer supported
                    // (forces the full doc to be sucked into memory). to compensate,
                    // make sure the biblio data is excluded here
                    if ( parseTemplate.getRootElement().getLocalName().equals("juxta-document")) {
                        excludeBiblioData( parseTemplate );
                    }
                    parseTemplate.setDefault(false);
                    parseTemplate.setName( parseTemplate.getName()+"."+set.getName().hashCode());
                    parseTemplate.setWorkspaceId( this.set.getWorkspaceId() );
                    
                    // if  template with this name already exists, update it
                    Workspace ws = this.workspaceDao.find( this.set.getWorkspaceId() );
                    Template orig = this.templateDao.find( ws, parseTemplate.getName() );
                    if ( orig != null ) {
                        parseTemplate.setId( orig.getId());
                        this.templateDao.update(parseTemplate);
                    } else {
                        // create a new template
                        Long id = this.templateDao.create(parseTemplate);
                        parseTemplate.setId(id);
                    }
                }
                // note: always create a new witness. others may have grabbed
                // a prior version and started using it. can't wipe it out
                // from under them - or change it!
                witnessId = this.transformer.transform(source, parseTemplate, revSet, srcInfo.getTitle());
                
            } else {
                // Just null transform it to a witness
                witnessId = this.transformer.transform(source, null, null, source.getName());
            }
            
            Witness newWitness = this.witnessDao.find(witnessId);
            witnesses.add(  newWitness );
        }
        
        // add all witnesses to the set and update with base witness
        this.taskStatus.setNote("Create comparison set");
        this.setDao.addWitnesses(this.set, witnesses);
        this.setDao.update(this.set);
        this.taskSegment.incrementValue();
    }

    private Source createSource(SourceInfo srcInfo, boolean isXml) throws FileNotFoundException, IOException, XMLStreamException {
        
        String name = srcInfo.getTitle();
        if ( this.sourceDao.exists(this.ws, name)) {
            name = this.sourceDao.makeUniqueName(this.ws, name);
            srcInfo.setTitle(name);
        }
        Long srcId = this.sourceDao.create(this.ws, name, isXml, new FileReader(srcInfo.getSrcFile()));
        return this.sourceDao.find(this.ws.getId(), srcId);
    }

    private void excludeBiblioData(Template parseTemplate) {
        for ( TagAction tagAct : parseTemplate.getTagActions()) {
            if ( tagAct.getTag().getLocalName().equals("bibliographic")) {
                tagAct.setAction( "EXCLUDE" );
                return;
            }
        }
        
        TagAction tagAction = new TagAction();
        tagAction.setAction("EXCLUDE");
        tagAction.setTemplate(parseTemplate);
        tagAction.setTag( new WildcardQName("*", "*", "bibliographic"));
        parseTemplate.getTagActions().add( tagAction );
        
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
     * Collate the comparison set
     * @throws IOException
     */
    private void collate( CollatorConfig cfg ) throws IOException {
        this.taskStatus.setNote("Collating comparison set");
        this.collator.collate(this.set, cfg, this.taskStatus);
        this.taskSegment.incrementValue();
    }
    
    private void unzip(File to, InputStream jxtIs) throws IOException {

        final ZipInputStream zip = new ZipInputStream(jxtIs);
        while (true) {
            final ZipEntry entry = zip.getNextEntry();
            if (entry == null) {
                break;
            }

            final File entryFile = new File(to, entry.getName());
            Preconditions.checkArgument(isContainedIn(to, entryFile));

            if (!entry.isDirectory()) {
                final File parentFile = entryFile.getParentFile();
                if (!parentFile.isDirectory()) {
                    parentFile.mkdirs();
                }
                Preconditions.checkState(parentFile.isDirectory());
                FileOutputStream entryStream = null;
                try {
                    ByteStreams.copy(zip, entryStream = new FileOutputStream(entryFile));
                } finally {
                    Closeables.close(entryStream, false);
                }
            }
        }
    }
}
