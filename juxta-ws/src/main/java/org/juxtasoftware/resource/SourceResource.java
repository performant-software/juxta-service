package org.juxtasoftware.resource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringEscapeUtils;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.CacheDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.RevisionDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.util.RangedTextReader;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import eu.interedition.text.Range;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SourceResource extends BaseResource {

    @Autowired private SourceDao sourceDao;
    @Autowired private RevisionDao revisionDao;
    @Autowired private CacheDao cacheDao;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private AlignmentDao alignmentDao;
    
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
            if ( u.getType().equals(Usage.Type.COMPARISON_SET)) {
           
                // clear cached data
                Long setId = u.getId();
                this.cacheDao.deleteAll(setId);
                
                // set status to NOT collated
                ComparisonSet set = this.setDao.find(setId);
                set.setCollated(false);
                this.setDao.update(set);
                
                // clear alignments
                this.alignmentDao.clear(set);
            }
        }

        this.sourceDao.delete(this.source);
        
        
        Gson gson = new Gson();
        return toJsonRepresentation( gson.toJson(usage));
    }
}
