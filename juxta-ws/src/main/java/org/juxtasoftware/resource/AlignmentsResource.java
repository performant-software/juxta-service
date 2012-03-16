package org.juxtasoftware.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaAnnotationDao;
import org.juxtasoftware.dao.QNameFilterDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.AlignmentConstraint;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaAnnotation;
import org.juxtasoftware.model.QNameFilter;
import org.juxtasoftware.util.AlignmentSerializer;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
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

import eu.interedition.text.Name;
import eu.interedition.text.NameRepository;
import eu.interedition.text.Range;
import eu.interedition.text.mem.SimpleName;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class AlignmentsResource extends BaseResource {
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private JuxtaAnnotationDao annotationDao;
    @Autowired private QNameFilterDao filterDao;
    @Autowired private NameRepository nameRepo;
    
    private ComparisonSet set = null;
    private QNameFilter filter = null;
    private Range range = null;
    
    @Override
    protected void doInit() throws ResourceException {

        super.doInit();
        Long setId = Long.parseLong( (String)getRequest().getAttributes().get("setId"));
        this.set = this.setDao.find( setId);
        if ( validateModel(this.set) == false ) {
            return;
        }
        
        // get by filter?
        if (getQuery().getValuesMap().containsKey("filter") ) {
            String filterName = getQuery().getValuesMap().get("filter");
            this.filter = this.filterDao.find(filterName);
            if ( this.filter == null ) {
                setStatus(Status.CLIENT_ERROR_NOT_FOUND, "Invalid filter name \""+filterName+"\"");
                return;
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
    }
    
    @Get("html")
    public Representation toHtml() {
        List<Alignment> aligns = getAlignments();
        
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("setName", set.getName());
        map.put("setId", set.getId());
        map.put("alignments", aligns);
        map.put("page", "set");
        map.put("title", "Juxta \""+set.getName()+"\" Differences");
        return toHtmlRepresentation("alignments.ftl",map);
    }

    private List<Alignment> getAlignments() {
        AlignmentConstraint constraint = new AlignmentConstraint( this.set );
        constraint.setFilter( this.filter );
        constraint.setRange( this.range );
        List<Alignment> aligns = this.alignmentDao.list( constraint );
        return aligns;
    }
                                        
    @Get("json")
    public Representation toJson() {
        List<Alignment> aligns = getAlignments();
        Gson gson = new GsonBuilder()
            .registerTypeAdapter(Alignment.class, new AlignmentSerializer())
            .create();
        String json = gson.toJson(aligns);
        return toJsonRepresentation(json);
    }
    
    @Post("json")
    public Representation acceptJson( final String jsonStr ) {
        LOG.info("Create alignments from "+jsonStr);
        JsonParser parser = new JsonParser();
        JsonArray jsonArray = parser.parse(jsonStr).getAsJsonArray();
        List<Alignment> alignments = new ArrayList<Alignment>();
        for ( Iterator<JsonElement>  itr = jsonArray.iterator(); itr.hasNext(); ) {
            JsonObject jsonObj = itr.next().getAsJsonObject();
            JsonObject nameObj = jsonObj.get("name").getAsJsonObject();
            Name linkQname = getQnameFromJson(nameObj);
            String editDistance = null;
            if ( jsonObj.has("editDistance")) {
                editDistance = jsonObj.get("editDistance").getAsString();
            }
        
            JsonArray annoArray = jsonObj.get("annotations").getAsJsonArray();
            Long aOneId = annoArray.get(0).getAsLong();
            Long aTwoId = annoArray.get(1).getAsLong();
            JuxtaAnnotation a1 = this.annotationDao.find(aOneId, false);
            JuxtaAnnotation a2 = this.annotationDao.find(aTwoId, false);
            if ( a1 == null || a2 == null ) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Invalid annotation ID specified for alignment");
            }
            
            Alignment align = new Alignment();
            align.setName( linkQname );
            if ( editDistance != null ) {
                align.setEditDistance( Integer.parseInt(editDistance) );
            } else  {
                align.setEditDistance( -1 );
            }
            align.setComparisonSetId(this.set.getId());
            align.setManual();
            align.addAnnotation( new AlignedAnnotation(a1) );
            align.addAnnotation( new AlignedAnnotation(a2) );
            alignments.add( align );
        }
        
        int created = this.alignmentDao.create(alignments);
        return toTextRepresentation(""+created);
    }

    private Name getQnameFromJson(JsonObject nameObj) {
        // note: this creates a name if it does not exist
        Name name = this.nameRepo.get( new SimpleName(
            nameObj.get("namespace").getAsString(), 
            nameObj.get("localName").getAsString()) );
        return name;
    }
}
