package org.juxtasoftware.resource;

import java.util.HashMap;
import java.util.Map;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaAnnotationDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaAnnotation;
import org.juxtasoftware.model.Witness;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import eu.interedition.text.Text;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class AnnotationResource extends BaseResource {

    private Long annotationId;
    private ComparisonSet set;
    private Witness witness;
    private boolean includeText;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private JuxtaAnnotationDao annotationDao;
    @Autowired private WitnessDao witnessDao;
        
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
        
        this.annotationId = getIdFromAttributes("annotationId");
        if ( this.annotationId == null ) {
            return;
        }
        this.includeText = false;
        
        if (getQuery().getValuesMap().containsKey("content") ) {
            this.includeText = true;
        }
    }
    
    @Get("html")
    public Representation toHtml() { 
        JuxtaAnnotation annotation = getAnnotation();
        if ( annotation == null ) {
            return null;
        }
        
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("witness", this.witness );
        map.put("includeText", this.includeText);
        map.put("annotation", annotation );
        map.put("page", "set");
        map.put("title", "Juxta \""+this.witness.getName()+"\" Annotation");
        return toHtmlRepresentation("annotation.ftl",map);
    }
    
    @Get("json")
    public Representation toJson() { 
        JuxtaAnnotation annotation = getAnnotation();
        if ( annotation != null ) {
            Gson gson = new GsonBuilder()
                .setExclusionStrategies( new TextExclusion() )
                .create();
            String json = gson.toJson(annotation);
            return toJsonRepresentation(json);
        } 
        return null;
    }

    private JuxtaAnnotation getAnnotation() {
        JuxtaAnnotation annotation = this.annotationDao.find(this.annotationId, this.includeText);
        if ( annotation == null ) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Invalid annotation identifier specified");
            return null;
        }
        
        if ( annotation.getText().toString().equals(witness.getText().toString()) == false) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Witness " + this.witness +
                " does not contain annotation "+this.annotationId);
            return null;
        }
        return annotation;
    }
            
    @Delete
    public void deleteAnnotation() {
        LOG.info("Delete annotation " + this.annotationId);
        JuxtaAnnotation anno = getAnnotation();
        if ( anno != null ) {
            this.annotationDao.delete( anno );
        } else {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND, 
                "Annotation" +this.annotationId+" does exist in set "+this.set.getId());
        }
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
