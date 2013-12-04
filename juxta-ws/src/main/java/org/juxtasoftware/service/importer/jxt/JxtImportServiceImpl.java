package org.juxtasoftware.service.importer.jxt;

import static org.juxtasoftware.service.importer.jxt.Util.isContainedIn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.juxtasoftware.Constants;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.CacheDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaAnnotationDao;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaAnnotation;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Source.Type;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.model.Workspace;
import org.juxtasoftware.service.ComparisonSetCollator;
import org.juxtasoftware.service.SourceTransformer;
import org.juxtasoftware.service.Tokenizer;
import org.juxtasoftware.service.importer.ImportService;
import org.juxtasoftware.service.importer.JuxtaXsltFactory;
import org.juxtasoftware.service.importer.XmlTemplateParser;
import org.juxtasoftware.service.importer.XmlTemplateParser.TemplateInfo;
import org.juxtasoftware.service.importer.jxt.JxtRevisionExtractor.RevisionOccurrence;
import org.juxtasoftware.service.importer.jxt.ManifestParser.SourceInfo;
import org.juxtasoftware.service.importer.jxt.MovesParser.JxtMoveInfo;
import org.juxtasoftware.util.BackgroundTaskSegment;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.juxtasoftware.util.NamespaceExtractor;
import org.juxtasoftware.util.NamespaceExtractor.NamespaceInfo;
import org.juxtasoftware.util.NamespaceExtractor.XmlType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

import eu.interedition.text.Name;
import eu.interedition.text.NameRepository;

/**
 * Service used to import JXT files from the desktop juxta. It supports only the 
 * latest version of juxta desktop (1.6.5). There is no support for target xpath
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
    @Autowired private JuxtaXsltFactory xsltFactory;
    @Autowired private SourceDao sourceDao;
    @Autowired private JuxtaXsltDao xsltDao;
    @Autowired private NameRepository nameRepo;
    @Autowired private CacheDao cacheDao;
    @Autowired private SourceTransformer transformer;
    @Autowired private ComparisonSetDao setDao;
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
    protected static final Logger LOG = LoggerFactory.getLogger( Constants.WS_LOGGER_NAME );
    

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
            LOG.info("Parsing manifest");
            List<SourceInfo> sources = parseManifest(sessionDataDir);
            
            // parse out any transpositions
            LOG.info("Parsing moves");
            List<JxtMoveInfo> moves = parseMoves(sessionDataDir);
            
            // Grab the associated templates and parse them out into a map.
            // this map is held internally in the template parser.
            LOG.info("Parsing templates");
            parseTemplates(sessionDataDir);
                      
            // combine all of this data into the comparison set
            LOG.info("Create set");
            prepareSet( );
            populateSet( sources, moves );
            
            // add in the transpositions!
            addTranspositions( moves );
                        
            // tokenize and collate
            CollatorConfig cfg = this.setDao.getCollatorConfig(this.set);
            this.set.setStatus(ComparisonSet.Status.COLLATING);
            tokenize( cfg );
            collate( cfg );
            
            this.taskStatus.setNote("Import successful");
            
        } finally {
            try {
                FileUtils.deleteDirectory(sessionDataDir);
            } catch (IOException e) {}
        }
    }
    
    private void prepareSet() {

        // grab all witnesses associated with this set.
        // If there are none, there is nothing more to do
        List<Witness> witnesses = this.setDao.getWitnesses(this.set);
        if ( witnesses.size() == 0) {
            return;
        }
        
        // clear out all prior data (NOTE: delete all witnesses wil also clear out all
        // aligment and annotation data )
        this.setDao.deleteAllWitnesses(this.set);
        this.cacheDao.deleteAll(this.set.getId());
        try {
            for (Witness witness : witnesses) {
                Source s = this.sourceDao.find(this.ws.getId(), witness.getSourceId());
                JuxtaXslt xslt = this.xsltDao.find(witness.getXsltId());
                this.witnessDao.delete(witness);
                this.sourceDao.delete(s);
                this.xsltDao.delete(xslt);
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to overwrite set; witnesses are in use in another set.");
        }
    }
    
    private void addTranspositions(List<JxtMoveInfo> moves) {
        this.taskStatus.setNote("Adding Transpositions");
        Name transQname = this.nameRepo.get(Constants.TRANSPOSITION_NAME) ;
        LOG.info("Adding Transpositions");
        List<Alignment> moveLinks = new ArrayList<Alignment>();
        for ( JxtMoveInfo move : moves ) {
            Alignment link = new Alignment();
            link.setComparisonSetId(this.set.getId());
            link.setManual();
            link.setName( transQname );
            
            for ( String title : move.getWitnessTitles() ) {
                Witness witness = this.witnessDao.find(this.set, title);
                if ( witness == null ) {
                    LOG.error("Unable to import moves. Witness "+title+" not found");
                    this.taskSegment.incrementValue();
                    return;
                }
                JuxtaAnnotation anno = new JuxtaAnnotation( this.set.getId(), witness, transQname, move.getWitnessRange(title) );
                anno.setManual();
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

            // determine type of source
            String srcName = JSONObject.escape(srcInfo.getSrcFile().getName());
            this.taskStatus.setNote("Adding raw source document: "+srcName); 
            int extPos = srcName.lastIndexOf('.');
            String ext = ".txt";
            if ( extPos > -1 ) {
                ext = srcName.substring(extPos);
            }
            Source.Type contentType = Source.Type.TXT;
            if ( ext.equalsIgnoreCase(".xml") ) {
                contentType = Source.Type.XML;
            }

            // create the juxta source
            Source source = createSource(srcInfo, contentType);     
                  
            // if the source was associated with a parse template,
            // create it and use it to transform to a witness
            this.taskStatus.setNote("Transform raw "+srcName+" into witness");
            Long witnessId = null;
            JuxtaXslt xslt = null;
            if ( contentType.equals(Source.Type.XML) ) {
                // extract namespace info
                Set<NamespaceInfo> namespaces = NamespaceExtractor.extract( this.sourceDao.getContentReader(source) ); 
                NamespaceInfo namespace = NamespaceInfo.createBlankNamespace();
                if ( namespaces.size() == 1 ) {
                    namespace = (NamespaceInfo)namespaces.toArray()[0];
                    XmlType xmlType = NamespaceExtractor.determineXmlType( this.sourceDao.getContentReader(source) );
                    if ( xmlType.equals(XmlType.TEI)) {
                        namespace.setDefaultPrefix("tei");
                    }
                }
                
                // record any accepted revisions this witness may have had
                TemplateInfo info = this.templateParser.findTemplateInfo(srcInfo.getTemplateGuid());
                xslt = this.xsltFactory.createFromTemplateInfo(source.getWorkspaceId(), srcInfo.getTitle(), info, namespace);
                addRevisonExclusions(source, xslt, namespace, srcInfo.getAcceptedRevsions() );
                witnessId = this.transformer.transform(source, xslt, srcInfo.getTitle());
            } else {
                // Just null transform it to a witness
              witnessId = this.transformer.transform(source, null, source.getName());
            }

            // add all witnesses to the set and update with base witness
            Witness newWitness = this.witnessDao.find(witnessId);
            witnesses.add(  newWitness );
        }

        this.taskStatus.setNote("Create comparison set");
        this.setDao.addWitnesses(this.set, witnesses);
        this.setDao.update(this.set);
        this.taskSegment.incrementValue();
    }

    private void addRevisonExclusions(Source source, JuxtaXslt xslt, NamespaceInfo namespace, List<Integer> acceptedRevsions) throws SAXException, IOException {
        if ( acceptedRevsions.size() == 0 ) {
            // when none are accepted, add an exclusion for all 
            // add tag and addSpan tags. The deletes remain
            xslt.addGlobalExclusion( namespace.addNamespacePrefix("add") );
            xslt.addGlobalExclusion( namespace.addNamespacePrefix("addSpan") );
        } else {
            // extract the exclusion info and add single exclusions to the XSLT
            JxtRevisionExtractor extractor = new JxtRevisionExtractor();
            extractor.extract( this.sourceDao.getContentReader(source), acceptedRevsions);
            for (RevisionOccurrence rev : extractor.getExcludedRevisions() ) {
                xslt.addSingleExclusion( namespace.addNamespacePrefix(rev.getTagName()), rev.getOccurrence() );
            }
        }
        this.xsltDao.update(xslt.getId(), new StringReader(xslt.getXslt()));
    }
    
    private Source createSource(SourceInfo srcInfo, Type contentType) throws FileNotFoundException, IOException, XMLStreamException {
        
        String name = srcInfo.getTitle();
        if ( this.sourceDao.exists(this.ws, name)) {
            name = this.sourceDao.makeUniqueName(this.ws, name);
            srcInfo.setTitle(name);
        }
        FileInputStream fis = new FileInputStream(srcInfo.getSrcFile());
        InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
        Long srcId = this.sourceDao.create(this.ws, name, contentType, isr);
        IOUtils.closeQuietly(isr);
        return this.sourceDao.find(this.ws.getId(), srcId);
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
