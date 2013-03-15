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
import org.juxtasoftware.model.UserAnnotation.Data;
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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
        UserAnnotation newAnno = parseRequest(jsonData);
        if ( newAnno == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Malformed json payload");
        }
        newAnno.setSetId(this.set.getId());
        if ( newAnno.getBaseId() == null || newAnno.getNotes().size() == 0 || newAnno.getBaseRange() == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Missing required data in json payload");
        }
        
        // Handle updates and additions to existing user annotation
        UserAnnotation prior = this.userNotesDao.find(this.set, newAnno.getBaseId(), newAnno.getBaseRange());
        if ( prior != null  ) {
            
            // newly added group annos have to skip this bit and be added as new at the
            // bottom of this function
            boolean newAddition = (prior.hasGroupAnnotation() != newAnno.hasGroupAnnotation() && 
                                   prior.hasWitnessAnnotation() !=  newAnno.hasWitnessAnnotation() );
            
            if ( !newAddition ) {
                boolean handled = false;
                Data newNote = (Data) newAnno.getNotes().toArray()[0];
                for ( Data n : prior.getNotes() ) {
                    if ( n.getWitnessId().equals(newNote.getWitnessId())) {
                        n.setText(newNote.getText());
                        handled = true;
                        if (n.isGroup()) {
                            Long groupId = this.userNotesDao.findGroupId(this.set, newAnno.getBaseId(), newAnno.getBaseRange());
                            this.userNotesDao.updateGroupNote(groupId, newAnno.getGroupNoteContent());
                        } else {
                            this.userNotesDao.updateWitnessNote(n.getId(), newNote.getText());
                        }
                    }
                }
                
                // if the above loop didn't catch the new data
                // add it as a separate note
                if ( handled == false ) {
                    this.userNotesDao.addWitnessNote(prior, newNote.getWitnessId(), newNote.getText());
                }
                
                return toTextRepresentation("OK");
            }
        }
        
        // a wholly new user annotation
        Long id = this.userNotesDao.create(newAnno);
        if ( newAnno.hasGroupAnnotation() ) {
            newAnno.setId(id);
            newAnno.setGroupId(id);
            this.userNotesDao.updateGroupId(newAnno, id);
            createReciprocalAnnotations( newAnno );
        }
       
        return toTextRepresentation("OK");
    }
    
    private UserAnnotation parseRequest(String jsonData) {
        JsonParser p = new JsonParser();
        JsonObject obj = p.parse(jsonData).getAsJsonObject();
        UserAnnotation anno = new UserAnnotation();
        anno.setBaseId( obj.get("baseId").getAsLong() );
        JsonObject rngObj = obj.get("baseRange").getAsJsonObject();
        anno.setBaseRange( new Range( rngObj.get("start").getAsLong(), rngObj.get("end").getAsLong() ));
        Data note = UserAnnotation.createNote(obj.get("witnessId").getAsLong(), obj.get("note").getAsString());
        note.setGroup(obj.get("isGroup").getAsBoolean());
        anno.addNote( note );
        return anno;
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
            UserAnnotation a = new UserAnnotation();
            Data note = UserAnnotation.createNote(ent.getKey(), groupAnno.getGroupNoteContent() );
            note.setGroup(true);
            a.addNote( note );
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
        
        // replace the name of any group notes with the names of
        // all witnesses that ahve been annotated
        List<Witness> wits = this.comparionSetDao.getWitnesses(this.set);
        for (UserAnnotation a : ua ) {
            if ( a.hasGroupAnnotation() ) {
                for (Data n : a.getNotes() ) {
                    if ( n.isGroup() ) {
                        StringBuilder newTitle = new StringBuilder();
                        for (Long witId :  this.userNotesDao.getGroupWitnesses(a.getGroupId()) ) {
                            for ( Witness w : wits ) {
                                if ( w.getId().equals(witId)) {
                                    if ( newTitle.length() != 0) {
                                        newTitle.append("<br/>");
                                    }
                                    newTitle.append(w.getName());
                                    break;
                                }
                            }
                        }
                        n.setWitnessName(newTitle.toString());
                    }
                }
            }
        }
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
    public Representation deleteUserAnnotation( ) { 
        // make sure someting exists to be deleted
        UserAnnotation tgtAnno = this.userNotesDao.find( this.set, this.baseId, this.range );
        if ( tgtAnno == null ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("No matching user annotation found");
        }
        
        // blanket delete of all?
        if (this.witnessId == null) {
            this.userNotesDao.delete( this.set, this.baseId, this.range );
            return toTextRepresentation(""+this.userNotesDao.count(this.set, baseId));
        }
        
        // Group annotation delete
        if ( this.witnessId == 0  ) {
            // Flag attempt to delete group anno where none exist
            if (tgtAnno.hasGroupAnnotation() == false ) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("No matching user annotation found");
            }
            this.userNotesDao.deleteGroupNote(this.set, tgtAnno.getGroupId());
            return toTextRepresentation(""+this.userNotesDao.count(this.set, baseId));
        }
        

        // single delete
        // Just remove the specified note from the base range.
        for (Iterator<UserAnnotation.Data> itr = tgtAnno.getNotes().iterator(); itr.hasNext();) {
            UserAnnotation.Data note = itr.next();
            if (this.witnessId.equals(note.getWitnessId())) {
                itr.remove();
                this.userNotesDao.deleteWitnessNote(note.getId());
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
