package org.juxtasoftware.resource;

import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.service.SourceTransformer;
import org.juxtasoftware.service.WitnessRemover;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Resourcce to GET a json list of all avaiable witnesses
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class WitnessesResource extends BaseResource {
    private boolean isCopyRequest = false;
    private boolean batchDelete = false;
    
    @Autowired private WitnessDao witnessDao;
    @Autowired private JuxtaXsltDao xsltDao;
    @Autowired private SourceTransformer transformer;
    @Autowired private SourceDao sourceDao;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private WitnessRemover remover;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        String lastSeg  = getRequest().getResourceRef().getLastSegment();
        this.isCopyRequest = lastSeg.equalsIgnoreCase("copy_settings");
        this.batchDelete =  lastSeg.equals("delete");
    }
       
    /**
     * Get a HTML representation of all available witnesses
     * @return
     */
    @Get("html")
    public Representation toHtml() {
        List<Witness> docs = this.witnessDao.list( this.workspace );
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("items", docs);
        map.put("page", "witness");
        map.put("title", "Juxta Witnesses");
        return toHtmlRepresentation("witness_list.ftl", map);
    }
    
    @Get("json")
    public Representation toJson() {       
        List<Witness> docs = this.witnessDao.list( this.workspace );
        Gson gson = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .setDateFormat("MM/dd/yyyy HH:mm:ss")
            .create();
        String out = gson.toJson(docs);
        return toJsonRepresentation(out);
    }
    
    @Post("json")
    public Representation handlePost( final String jsonData ) {
        if ( this.batchDelete ) {
            return batchDelete(jsonData);  
        }
        if ( this.isCopyRequest == false ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return null;
        }
        
        JsonParser parser = new JsonParser();
        JsonObject jsonObj =  parser.parse(jsonData).getAsJsonObject();
        Long fromId = jsonObj.get("from").getAsLong();
        Long toId = jsonObj.get("to").getAsLong();
        Witness from = this.witnessDao.find(fromId);
        Witness to = this.witnessDao.find(toId);
        if ( validateModel(from) == false ) {
            return null;
        }
        if ( validateModel(to) == false ) {
            return null;
        } 

        // grab the xslt for the source and copy it into the
        // destination. Strip all witness-specific single exclusions
        JuxtaXslt srcXslt = this.xsltDao.find(from.getXsltId());
        JuxtaXslt destXslt = this.xsltDao.find(to.getXsltId());
        destXslt.setXslt( srcXslt.getXslt() );
        destXslt.stripSingleExclusions();

        try {
            // save changes and redo transform
            this.xsltDao.update(destXslt.getId(), new StringReader(destXslt.getXslt()));
            Source src = this.sourceDao.find(to.getWorkspaceId(), to.getSourceId());
            for (Usage u: this.sourceDao.getUsage(src) ) {
                if ( u.getType().equals(Usage.Type.COMPARISON_SET)) {
                    ComparisonSet set = this.setDao.find(u.getId());
                    this.setDao.clearCollationData(set);
                }
            }
            this.transformer.redoTransform(src, to);
        } catch (Exception e) {
            setStatus(Status.SERVER_ERROR_INTERNAL, e.getMessage());
            LOG.error("Copy preparation settings failed", e);
        } 
        return toTextRepresentation("ok");
    }
    
    @Delete("json")
    public Representation batchDelete( final String jsonContent) {
        LOG.info("Batch delete witnesses "+jsonContent);
        JsonParser parser = new JsonParser();
        JsonArray jsonArray = parser.parse(jsonContent).getAsJsonArray();
        Set<Usage> usage = new HashSet<Usage>();
        for ( Iterator<JsonElement>  itr = jsonArray.iterator(); itr.hasNext(); ) {
            JsonElement ele = itr.next();
            Long id = ele.getAsLong();
            Witness w = this.witnessDao.find(id);
            if ( w != null ) {
                try {
                    usage.addAll( this.remover.remove(w) );
                } catch ( ResourceException e ) {
                    LOG.warn(e.toString());
                }
            } else {
                LOG.warn("Witness ID "+id+" is not a valid witness for this workspace");
            }
        }
        Gson gson = new Gson();
        return toJsonRepresentation( gson.toJson(usage) );
    }
}
