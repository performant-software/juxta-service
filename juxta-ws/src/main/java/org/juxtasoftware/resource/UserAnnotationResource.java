package org.juxtasoftware.resource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.UserAnnotationDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.AlignmentConstraint;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.UserAnnotation;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.QNameFilters;
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
    @Autowired private QNameFilters filters;
    @Autowired private AlignmentDao alignmentDao;
    
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
        UserAnnotation prior = this.userNotesDao.find(this.set, newAnno.getBaseId(), newAnno.getBaseRange());
        if ( prior != null  ) {
            if ( newAnno.isGroupAnnotation() != prior.isGroupAnnotation()  ) {
                setStatus(Status.CLIENT_ERROR_CONFLICT);
                return toTextRepresentation("Conflicting group/single user annotation");
            } 
            
            if ( prior.isGroupAnnotation() ) {
                this.userNotesDao.updateGroupAnnotation(this.set, prior.getGroupId(), newAnno.getGroupNoteContent());
                return toTextRepresentation("OK");
            } else {
                prior.updateNotes( newAnno.getNotes() );
                this.userNotesDao.update(prior);
                return toTextRepresentation("OK");
            }
        }
        
        Long id = this.userNotesDao.create(newAnno);
        if ( newAnno.isGroupAnnotation() ) {
            newAnno.setId(id);
            newAnno.setGroupId(id);
            this.userNotesDao.update(newAnno);
            createReciprocalAnnotations( newAnno );
        }
       
        return toTextRepresentation("OK");
    }
    
    private void createReciprocalAnnotations( UserAnnotation groupAnno ) {
        // get all of the diff alignments in the specified range
        AlignmentConstraint constraint = new AlignmentConstraint( this.set, groupAnno.getBaseId() );
        constraint.setFilter( this.filters.getDifferencesFilter() );
        constraint.setRange( groupAnno.getBaseRange() );
        List<Alignment> aligns = this.alignmentDao.list( constraint );
        
        // consolidate ranges
        Map<Long, Range > witRangeMap = new HashMap<Long, Range>();
        for (Alignment align : aligns ) {     
            AlignedAnnotation witnessAnno = null;
            for ( AlignedAnnotation a : align.getAnnotations()) {
                if ( a.getWitnessId().equals(groupAnno.getBaseId()) == false) {
                    witnessAnno = a;
                    break;
                }
            }
            
            Range range = witRangeMap.get(witnessAnno.getWitnessId());
            if ( range == null ) {
                witRangeMap.put(witnessAnno.getWitnessId(), witnessAnno.getRange());
            } else {
                witRangeMap.put(witnessAnno.getWitnessId(), new Range(
                    Math.min(range.getStart(), witnessAnno.getRange().getStart()), 
                    Math.max(range.getEnd(), witnessAnno.getRange().getEnd())) );
            }
        }
        
        for ( Entry<Long, Range> ent : witRangeMap.entrySet() ) {
            if ( ent.getKey().equals(groupAnno.getBaseId())) {
                throw new RuntimeException("Bad things");
            }
            UserAnnotation a = new UserAnnotation();
            a.addNote(0L, groupAnno.getGroupNoteContent() );
            a.setBaseId(ent.getKey());
            a.setSetId(this.set.getId());
            a.setBaseRange(ent.getValue());
            a.setGroupId( groupAnno.getGroupId() );
            this.userNotesDao.create(a);
        }
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
        List<UserAnnotation> ua = this.userNotesDao.list(this.set, this.baseId);
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
        UserAnnotation tgtAnno = this.userNotesDao.find( this.set, this.baseId, this.range );
        if ( tgtAnno == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("No matching user annotation found");
        }
        
        // if this is a group annotation... must delete all other annotations in the group
        if ( tgtAnno.isGroupAnnotation() ) {
            this.userNotesDao.deleteGroup(this.set, tgtAnno.getGroupId());
            return toTextRepresentation(""+this.userNotesDao.count(this.set, baseId));
        } 
        
        // normal delete
        if (this.witnessId != null) {
            // Just remove the specified note from the base range.
            for (Iterator<UserAnnotation.Data> itr = tgtAnno.getNotes().iterator(); itr.hasNext();) {
                UserAnnotation.Data note = itr.next();
                if (this.witnessId.equals(note.getWitnessId())) {
                    itr.remove();
                }
            }

            // if all notes are gone, kill the whole thing
            if (tgtAnno.getNotes().size() == 0) {
                this.userNotesDao.delete(this.set, tgtAnno.getBaseId(), tgtAnno.getBaseRange());
            } else {
                // just update it with newly shortened info
                this.userNotesDao.update(tgtAnno);
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
