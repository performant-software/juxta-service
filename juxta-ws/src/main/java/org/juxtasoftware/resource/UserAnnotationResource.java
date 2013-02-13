package org.juxtasoftware.resource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.UserAnnotationDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.UserAnnotation;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.RangedTextReader;
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

import eu.interedition.text.Range;

/**
 * Resource used to manage user annotations on comparison set witness pairs
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class UserAnnotationResource extends BaseResource {
    @Autowired private ComparisonSetDao comparionSetDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private UserAnnotationDao userNotesDao;
    
    private ComparisonSet set;
    private Range range;
    private Long baseId;
    private Long witnessId;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        Long setId = getIdFromAttributes("id");
        if (setId == null) {
            return;
        }
        this.set = this.comparionSetDao.find(setId);
        if (validateModel(this.set) == false) {
            return;
        }
        
        // was a base specified?
        if ( getQuery().getValuesMap().containsKey("base") ) {
            String strVal = getQuery().getValues("base");
            try {
                this.baseId = Long.parseLong(strVal);
            } catch (NumberFormatException e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid base identifier specified");
            }
        }
        
        // was a witness specified?
        if ( getQuery().getValuesMap().containsKey("witness") ) {
            String strVal = getQuery().getValues("witness");
            try {
                this.witnessId = Long.parseLong(strVal);
            } catch (NumberFormatException e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid base identifier specified");
            }
        }

        // was a range requested?
        if (getQuery().getValuesMap().containsKey("range")) {
            String rangeInfo = getQuery().getValues("range");
            String ranges[] = rangeInfo.split(",");
            if (ranges.length == 2) {
                this.range = new Range(Integer.parseInt(ranges[0]), Integer.parseInt(ranges[1]));
            } else {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid Range specified");
            }
        }
    }
    
    @Post("json")
    public Representation create( final String jsonData ) {
        Gson gson = new Gson();
        UserAnnotation newAnno = gson.fromJson(jsonData, UserAnnotation.class);
        if ( newAnno == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Malformed json payload");
        }
        newAnno.setSetId(this.set.getId());
        if ( newAnno.getBaseId() == null || newAnno.getNotes().size() == 0 || newAnno.getBaseRange() == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Missing required data in json payload");
        }
        
        // see if an annotation already exists here. If it does, update with new witness & note info
        List<UserAnnotation> annos = this.userNotesDao.list(this.set, newAnno.getBaseId(), newAnno.getBaseRange());
        for ( UserAnnotation ua : annos ) {
            if ( ua.getBaseRange().equals(newAnno.getBaseRange())) {
                ua.updateNotes( newAnno.getNotes() );
                this.userNotesDao.update(ua);
                return toTextRepresentation("OK");
            }
        }
        
        this.userNotesDao.create(newAnno);
        return toTextRepresentation("OK");
    }
    
    @Get("html")
    public Representation getHtml() {
        List<UserAnnotation> ua = getUserAnnotations();
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("annotations", ua);
        return toHtmlRepresentation("user_annotations.ftl", map,false);
    }
    
    @Get("json")
    public Representation getJson() {
        List<UserAnnotation> ua = getUserAnnotations();
        Gson gson = new Gson();
        String out = gson.toJson(ua);
        return toJsonRepresentation( out );
    }

    private List<UserAnnotation> getUserAnnotations() {
        List<UserAnnotation> ua = this.userNotesDao.list(this.set, this.baseId, this.range);
        List<Witness> witnesses = this.comparionSetDao.getWitnesses(this.set);
        for ( UserAnnotation a : ua ) {
            a.setBaseFragment( getBaseFragment(a.getBaseId(), a.getBaseRange()) );
            for (Iterator<UserAnnotation.Data> itr = a.getNotes().iterator(); itr.hasNext(); ) {
                UserAnnotation.Data note = itr.next();
                if ( this.witnessId != null ) { 
                    if ( note.getWitnessId().equals(this.witnessId) == false) {
                        itr.remove();
                        continue;
                    }
                }
                note.setWitnessName(findName(witnesses, note.getWitnessId()));
            }
        }
        return ua;
    }
    
    private String getBaseFragment(Long baseId, Range baseRange) {
        final int contextSize=20;
        Witness base = this.witnessDao.find(baseId);
        Range tgtRange = new Range(
            Math.max(0, baseRange.getStart()-contextSize),
            Math.min(base.getText().getLength(), baseRange.getEnd()+contextSize));
        try {
            // read the full fragment
            final RangedTextReader reader = new RangedTextReader();
            reader.read( this.witnessDao.getContentStream(base), tgtRange );
            String frag = reader.toString().trim();
            int pos = frag.indexOf(' ');
            if ( pos > -1 ) {
                frag = "..."+frag.substring(pos+1);
            }
            pos = frag.lastIndexOf(' ');
            if ( pos > -1 ) {
                frag = frag.substring(0,pos)+"...";
            }
            frag = frag.replaceAll("\\n+", " / ").replaceAll("\\s+", " ").trim();
            return frag;
        } catch (IOException e) {
            // couldn't get fragment. skip it for now
            return "";
        }
    }

    private String findName(List<Witness> wl, Long id) {
        for ( Witness w : wl ) {
            if (w.getId().equals(id) ) {
                return w.getJsonName();
            }
        }
        return "";
    }
    
    @Delete("json")
    public Representation deleteUserAnnotation( final String jsonData ) { 

        if ( this.witnessId == null ) {
            // mass delete all user annotations
            this.userNotesDao.delete( this.set, this.baseId, this.range   );
        } else {            
            // Just remove the specified note from the base range.
            for (UserAnnotation ua : this.userNotesDao.list(this.set, baseId, this.range) ) {
                for ( Iterator<UserAnnotation.Data> itr=ua.getNotes().iterator(); itr.hasNext(); ) {
                    UserAnnotation.Data note = itr.next();
                    if ( this.witnessId.equals(note.getWitnessId())) {
                        itr.remove();
                    }
                }

                // if all notes are gone, kill the whole thing
                if ( ua.getNotes().size() == 0) {
                    this.userNotesDao.delete( this.set, ua.getBaseId(), ua.getBaseRange() );
                } else {
                    // just update it with newly shortened info
                    this.userNotesDao.update(ua);
                }
            }
        }
        
        return toTextRepresentation(""+this.userNotesDao.count(this.set, baseId));
    }
    
    public static class FragmentInfo {
        private final String frag;
        private final Range r;
        public FragmentInfo(Range r, String f) {
            this.frag = f;
            this.r = new Range(r.getStart(), r.getEnd());
        }
        public Range getRange() {
            return this.r;
        }
        public String getFragment() {
            return this.frag;
        }
    }
}
