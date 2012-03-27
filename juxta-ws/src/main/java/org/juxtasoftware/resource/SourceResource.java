package org.juxtasoftware.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.lang.StringEscapeUtils;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.CacheDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.RevisionDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.service.SourceTransformer;
import org.juxtasoftware.service.importer.ps.ParallelSegmentationImportImpl;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.juxtasoftware.util.RangedTextReader;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.fileupload.RestletFileUpload;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import eu.interedition.text.Range;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SourceResource extends BaseResource implements ApplicationContextAware {

    @Autowired private SourceDao sourceDao;
    @Autowired private RevisionDao revisionDao;
    @Autowired private CacheDao cacheDao;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private SourceTransformer transformer;
    
    private ApplicationContext context;
    private Range range = null;
    private Source source;

    /**
     * Extract the doc ID from the request attributes. This is the doc that
     * will be acted upon by the get/delete verbs.
     */
    @Override
    protected void doInit() throws ResourceException {
        
        super.doInit();
        
        Long id = Long.parseLong((String) getRequestAttributes().get("id"));
        this.source = this.sourceDao.find(this.workspace.getId(), id);
        
        // was a range set requested?
        if (getQuery().getValuesMap().containsKey("range") ) {
            String rangeInfo = getQuery().getValues("range");
            String ranges[] = rangeInfo.split(",");
            if ( ranges.length == 2) {
                this.range = new Range( 
                    Integer.parseInt(ranges[0]),
                    Integer.parseInt(ranges[1]) );
            } else {
                getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid Range specified");
            }
        }
        
        validateModel( this.source );
    }
    
    /**
     * Get the source refreneced by <code>sourceID</code> and return its
     * XML representation as the response
     * @return
     */
    @Get("html")
    public Representation toHtml() throws IOException {
        final RangedTextReader reader = new RangedTextReader();
        reader.read( this.sourceDao.getContentReader(this.source), this.range );

        Map<String,Object> map = new HashMap<String,Object>();
        map.put("name", this.source.getFileName());
        map.put("sourceId", this.source.getId());
        map.put("hasRevisionSets", this.revisionDao.hasRevisionSets(this.source.getId()));
        map.put("page", "source");
        map.put("title", "Juxta Source: "+this.source.getFileName());
        map.put("text",  toTextRepresentation( StringEscapeUtils.escapeHtml(reader.toString())));
        return toHtmlRepresentation("source.ftl", map);
    }

    /**
     * Get the source refreneced by <code>sourceID</code> and return its
     * XML representation as the response
     * @return
     */
    @Get("txt")
    public Representation toTxt() throws IOException {
        final RangedTextReader reader = new RangedTextReader();
        reader.read( this.sourceDao.getContentReader(this.source), this.range );
        return toTextRepresentation(reader.toString());
    }
    
    /**
     * Get the source refreneced by <code>sourceID</code> and return a 
     * json object containing source meta data and complete content
     * @return
     */
    @Get("json")
    public Representation toJson() throws IOException {
        final RangedTextReader reader = new RangedTextReader();
        reader.read( this.sourceDao.getContentReader(this.source), this.range );
        JsonObject obj = new JsonObject();
        obj.addProperty("id", this.source.getId());
        obj.addProperty("fileName", this.source.getFileName());
        obj.addProperty("content", reader.toString());
        Gson gson = new Gson();
        return toTextRepresentation(gson.toJson(obj));
    }
    
    /**
     * Update the content of source <code>sourcceID</code> with the data
     * contined in the post
     * @param entity
     * @return
     * @throws ResourceException
     */
    @Put
    public Representation update( Representation entity  ) throws ResourceException {
        if ( entity == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Missing source payload");
        }
        
        // extract the input stream from the multipart request
        if (MediaType.MULTIPART_FORM_DATA.equals(entity.getMediaType(),true)) {
            InputStream srcInputStream = null;
            String sourceName= null;
            boolean isParallelSegmented = false;
            try {
                // pull the list of items in this multipart request
                DiskFileItemFactory factory = new DiskFileItemFactory();
                factory.setSizeThreshold(1000240);
                RestletFileUpload upload = new RestletFileUpload(factory);
                List<FileItem> items = upload.parseRequest(getRequest());
                for ( FileItem item : items ) {
                    if ( item.getFieldName().equals("sourceFile")) {
                        srcInputStream = item.getInputStream();
                    } else  if ( item.getFieldName().equals("sourceName")) {
                        sourceName = item.getString();
                    } else  if ( item.getFieldName().equals("parallelSegmented")) {
                        isParallelSegmented = true;
                    } 
                }
                
                // Fail requests with none of the required payload
                if ( srcInputStream == null && sourceName == null ) {
                    setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                    return toTextRepresentation("Request is missing all content");
                }
                
                // Rename source or rename/update content 
                if ( sourceName != null && sourceName.length() > 0 && srcInputStream == null ) {
                    return renameSource( sourceName );
                } else {
                    // if no new name was specifed, just pass along the
                    // old name as if it were new.
                    if ( sourceName == null || sourceName.length() == 0) {
                        sourceName = this.source.getFileName();
                    }
                    if ( isParallelSegmented ) {
                        return updateParallelSegmentedSource( srcInputStream, sourceName  );
                    } else {
                        return updateSource( srcInputStream, sourceName );
                    }
                }
                
            } catch (Exception e) {
                LOG.error("Unable to update source", e);
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST );
                return toTextRepresentation("File upload failed");
            } 
                
        } 
        
        setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
        return toTextRepresentation("Unsupported content type in put");
    }
    
    /**
     * Update a source that is encoded in TEI Parallel Segmentation. Requires
     * a re-import of the newly editied source.
     * 
     * @param srcInputStream
     * @param sourceName
     * @return
     * @throws Exception 
     */
    private Representation updateParallelSegmentedSource(InputStream srcInputStream, String newName) throws Exception {
        // First, update the source with the new content and (possibly) name
        this.sourceDao.update(this.source, newName, new InputStreamReader(srcInputStream));
        Source source = this.sourceDao.find(this.workspace.getId(), this.source.getId());
        ComparisonSet set = null;
        for ( Usage u : this.sourceDao.getUsage(source)) {
            if ( u.getType().equals(Usage.Type.COMPARISON_SET)) {
                set = this.setDao.find(u.getId());
                if ( set.getName().equals(this.source.getFileName())) {
                    break;
                } else {
                    set = null;
                }
            }
        }
        
        ParallelSegmentationImportImpl importService = this.context.getBean(ParallelSegmentationImportImpl.class);
        importService.deferCollation();
        BackgroundTaskStatus task =  new BackgroundTaskStatus( "update-tei-ps-src-"+newName );
        importService.doImport(set, source, task);
        return toJsonRepresentation("{\"result\": \"success\"}");
    }

    /**
     * Update source content and name. This will also find all related witnesses and comparison sets.
     * Witnesses will be re-parsed and comparison sets will be invalidated (cache cleared,
     * alignments reset and collated flag set to false)
     * 
     * @param srcInputStream
     * @param newName
     * @return
     * @throws XMLStreamException 
     * @throws IOException 
     */
    private Representation updateSource( InputStream srcInputStream, final String newName ) throws IOException, XMLStreamException {
        this.sourceDao.update(this.source, newName, new InputStreamReader(srcInputStream));
        
        List<Usage> usage = this.sourceDao.getUsage(this.source);
        for ( Usage use : usage ) {
            if ( use.getType().equals(Usage.Type.COMPARISON_SET ) ) {
                // clear cached data, alignmsnts and flag set as uncollated
                Long setId = use.getId();
                this.cacheDao.deleteAll(setId);
                ComparisonSet set = this.setDao.find(setId);
                set.setCollated(false);
                this.setDao.update(set);
                this.alignmentDao.clear(set);
            } else if  ( use.getType().equals(Usage.Type.WITNESS) ) {
                Witness oldWit = this.witnessDao.find( use.getId() );
                this.transformer.redoTransform(this.source, oldWit);                     
            }
        }
        
        return toJsonRepresentation("{\"result\": \"success\"}");
    }

    /**
     * Change the name of the source
     * @param newName
     * @return
     */
    private Representation renameSource( final String newName ) {
        this.sourceDao.update(this.source, newName);
        return toJsonRepresentation("{\"result\": \"success\"}");
    }

    /**
     * Delete the raw document resource with the ID specified in the request
     */
    @Delete
    public Representation remove() {
        LOG.info("Delete source "+source.getId());
        
        // get a list of all uses of this source. Comparison
        // set uses need special treatment; mark them as NOT collated,
        // clear their collation cache and remove all alignments
        List<Usage> usage = this.sourceDao.getUsage(this.source);
        for (Usage u : usage) {
            // Manually delete the witness. This is necessary
            // cuz witness content is stored in the text_content area
            // and will not cascade delete itself.
            if ( u.getType().equals(Usage.Type.WITNESS)) {
                Witness w = this.witnessDao.find(u.getId());
                this.witnessDao.delete(w);
            } else if ( u.getType().equals(Usage.Type.COMPARISON_SET)) {
           
                // clear cached data
                Long setId = u.getId();
                this.cacheDao.deleteAll(setId);
                
                // set status to NOT collated
                ComparisonSet set = this.setDao.find(setId);
                set.setCollated(false);
                this.setDao.update(set);
            }
        }

        this.sourceDao.delete(this.source);
        
        
        Gson gson = new Gson();
        return toJsonRepresentation( gson.toJson(usage));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }
}
