package org.juxtasoftware.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaAnnotationDao;
import org.juxtasoftware.dao.QNameFilterDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.AnnotationConstraint;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaAnnotation;
import org.juxtasoftware.model.QNameFilter;
import org.juxtasoftware.model.Witness;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.interedition.text.Name;
import eu.interedition.text.NameRepository;
import eu.interedition.text.Range;
import eu.interedition.text.Text;
import eu.interedition.text.mem.SimpleName;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class AnnotationsResource extends BaseResource {
    
    @Autowired private ComparisonSetDao setDao;
    @Autowired private QNameFilterDao filterDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private JuxtaAnnotationDao annotationDao;
    @Autowired private NameRepository qnameRepo;
    
    private ComparisonSet set =  null;
    private Witness witness = null;
    private boolean includeText = false;
    private QNameFilter filter = null;
    private Range range;
    private Gson gson;
        
    @Override
    protected void doInit() throws ResourceException {
        
        super.doInit();

        // get the set and validate that it exists and is in the wprkspace
        Long setId = getIdFromAttributes("setId");
        if ( setId == null ) {
            return;
        }
        this.set = this.setDao.find( setId);
        if ( validateModel(this.set) == false ) {
            return;
        }
        
        // once the set is workspace validated, just make sure the witness
        // exists and is part of the set
        Long witnessId = getIdFromAttributes("witnessId");
        if ( witnessId == null ) {
            return;
        }
        this.witness = this.witnessDao.find( witnessId);
        if ( witness == null ) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Invalid witness identifier specified");
            return;
        }
        if ( this.setDao.isWitness(this.set, this.witness) == false ) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Witness "+witnessId+" does not exist in set "+setId);
            return;
        }
            
        // content requested?
        if (getQuery().getValuesMap().containsKey("content") ) {
            this.includeText = true;
        }
        
        // filter results?
        if (getQuery().getValuesMap().containsKey("filter") ) {
            String filterName = getQuery().getValuesMap().get("filter");
            this.filter = this.filterDao.find(filterName);
            if ( this.filter == null ) {
                setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Invalid filter specified");
            }
        }
        
        // was a range set requested?
        if (getQuery().getValuesMap().containsKey("range") ) {
            String rangeInfo = getQuery().getValues("range");
            String ranges[] = rangeInfo.split(",");
            if ( ranges.length == 2) {
                this.range = new Range(
                    Integer.parseInt(ranges[0]),
                    Integer.parseInt(ranges[1]) );
            } else {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid Range specified");
            }
        }
        
        this.gson = new GsonBuilder()
            .setExclusionStrategies( new TextExclusion() )
            .create();
    }
    
    @Get("html")
    public Representation toHtml() {
        List<JuxtaAnnotation> list = getAnnotations();
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("setId", this.set.getId());
        map.put("witness", this.witness );
        map.put("annotations", list );
        map.put("includeText", this.includeText);
        map.put("page", "set");
        map.put("title", "Juxta \""+this.witness.getName()+"\" Annotations");
        return toHtmlRepresentation("annotations.ftl",map);
    }
    
    @Get("json")
    public Representation toJson() {
        List<JuxtaAnnotation> list = getAnnotations(); 
        
        String json = this.gson.toJson( list );
        return toJsonRepresentation(json);
    }

    private List<JuxtaAnnotation> getAnnotations() {
        // build the constraints for the request; range, text and qname filter
        AnnotationConstraint constraint = new AnnotationConstraint( this.set.getId(), this.witness );
        constraint.setIncludeText( this.includeText );
        constraint.addRange( this.range );
        constraint.setFilter( this.filter );
        
        // get the constrained list of anotation data; convert to json 
        List<JuxtaAnnotation> list = this.annotationDao.list( constraint );
        return list;
    }
    
    @Post("json")
    public Representation fromJson( final String jsonStr ) {
        LOG.info("Create annotation from "+jsonStr);
        JsonParser parser = new JsonParser();
        JsonArray array = null;
        try {
            array = parser.parse(jsonStr).getAsJsonArray();
        } catch ( Exception e ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Invalid json payload");
        }
        
        List<JuxtaAnnotation> annotations = new ArrayList<JuxtaAnnotation>();
        for ( Iterator<JsonElement> itr = array.iterator(); itr.hasNext(); ) {
            JsonObject annoObj = itr.next().getAsJsonObject();
            
            // get name
            Name qname = null;
            Range range =null;
            try {
                JsonObject nameObj = annoObj.get("name").getAsJsonObject();
                qname = this.qnameRepo.get( new SimpleName( nameObj.get("namespace").getAsString(), 
                                                                 nameObj.get("localName").getAsString() ) );
                JsonObject rangeObj = annoObj.get("range").getAsJsonObject();
                range = new Range(rangeObj.get("start").getAsLong(), 
                                        rangeObj.get("end").getAsLong());
            } catch (Exception e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Invalid json payload");
            }
            
            JuxtaAnnotation ano = new JuxtaAnnotation( this.set.getId(), witness,  qname, range);
            ano.setManual();
            annotations.add(ano);
        }
        Integer cnt = this.annotationDao.create(annotations);
        return toTextRepresentation( cnt.toString() );
    }
    
    private static class TextExclusion implements ExclusionStrategy {
        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            return false;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return (clazz == Text.class);
        }
    }
}
