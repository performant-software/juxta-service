package org.juxtasoftware.resource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
import org.juxtasoftware.util.FragmentFormatter;
import org.juxtasoftware.util.QNameFilters;
import org.juxtasoftware.util.RangedTextReader;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import eu.interedition.text.Range;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class FragmentResource extends BaseResource {
    @Autowired private ComparisonSetDao setDao;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private QNameFilters filters;
    @Autowired private UserAnnotationDao userNotesDao;
    
    private Long baseWitnessId;
    private ComparisonSet set;
    private Range range;
    private Set<Long> witnessIdList;
    private static final int MAX_RANGE = 5000;
    private static final int FRAG_SIZE = 25;
    
    @Override
    protected void doInit() throws ResourceException {

        super.doInit();
        
        Long id = getIdFromAttributes("id");
        if ( id == null ) {
            return;
        }
        this.set = this.setDao.find(id);
        if (validateModel(this.set) == false) {
            return;
        }
        
        if (getQuery().getValuesMap().containsKey("base") == false ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing base parameter");
        } else {
            String baseId = getQuery().getValues("base");
            try {
                this.baseWitnessId = Long.parseLong(baseId);
            } catch (NumberFormatException e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid witness identifier specified");
                return;
            }
        }
        
        if (getQuery().getValuesMap().containsKey("range") == false) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing range parameter");
        } else {
            String rangeInfo = getQuery().getValues("range");
            String ranges[] = rangeInfo.split(",");
            if ( ranges.length == 2) {
                this.range = new Range(
                    Integer.parseInt(ranges[0]),
                    Integer.parseInt(ranges[1]) );
                if (this.range.length() > MAX_RANGE) {
                    setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Range exceeds maximum length of "+MAX_RANGE);
                }
            } else {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid Range specified");
            }
        }
        
        // grab the witness id filter. These witness IDs will be filtered out
        this.witnessIdList = new HashSet<Long>();
        if (getQuery().getValuesMap().containsKey("filter")  ) {
            String[] docStrIds = getQuery().getValues("filter").split(",");
            for ( int i=0; i<docStrIds.length; i++ ) {
                try {
                    Long witId = Long.parseLong(docStrIds[i]);
                    if ( witId.equals(this.baseWitnessId)  ) {
                        setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Cannot filter out base witness");
                        return;
                    } else {
                        this.witnessIdList.add(witId);
                    }
                } catch (Exception e ) {
                    setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid document id specified");
                    return;
                }
            }
        }
    }
    
    @Get("json")
    public Representation toJson() {        
        // get all of the diff alignments in the specified range
        AlignmentConstraint constraint = new AlignmentConstraint( this.set, this.baseWitnessId );
        constraint.setFilter( this.filters.getDifferencesFilter() );
        constraint.setRange( this.range );
        for (Long witId : this.witnessIdList ) {
            constraint.addWitnessIdFilter(witId);
        }
        List<Alignment> aligns = this.alignmentDao.list( constraint );
        
        // consolidate ranges
        Map<Long, WitnessFragment > witnessDiffMap = new HashMap<Long, WitnessFragment>();
        Map<Long, Witness> witnessMap = new HashMap<Long, Witness>();
        for (Alignment align : aligns ) {
            
            // if the diff is an addition, and it is on the first char on the range it is 
            // on the line between 2 adjacent changes. count it in the prior change and skip it here
            AlignedAnnotation baseAnno = align.getWitnessAnnotation(this.baseWitnessId);
            if ( baseAnno.getRange().length() == 0 && 
                 baseAnno.getRange().getStart() == this.range.getStart() &&
                 this.range.length() > 0) {
                continue;
            }
            
            // find the WITNESS portion of this alignment
            AlignedAnnotation witnessAnno = null;
            for ( AlignedAnnotation a : align.getAnnotations()) {
                if ( a.getWitnessId().equals(this.baseWitnessId) == false) {
                    witnessAnno = a;
                    break;
                }
            }
            
            WitnessFragment info = witnessDiffMap.get(witnessAnno.getWitnessId());
            if ( info == null ) {
                
                // lookup witness info and cache for use later
                Witness w = this.witnessDao.find( witnessAnno.getWitnessId() );
                witnessMap.put(witnessAnno.getWitnessId(), w);
                
                // initialize a new fragment and add it to the fragments map
                // these fragments have no initial content. will be populated below
                info = new WitnessFragment( w, witnessAnno.getRange(), align.getEditDistance());
                info.witnessId = witnessAnno.getWitnessId();
                witnessDiffMap.put(witnessAnno.getWitnessId(), info);
            } else {
                info.range = new Range(
                    Math.min(info.getStart(), witnessAnno.getRange().getStart()), 
                    Math.max(info.getEnd(), witnessAnno.getRange().getEnd()));
            }
        }
        
        // Get any user annotations on this range & base id combination
        UserAnnotation userAnno = this.userNotesDao.find(this.set, this.baseWitnessId, this.range);
        String groupAnnotation = "";
        if (userAnno  != null && userAnno.hasGroupAnnotation()) {
            groupAnnotation = userAnno.getGroupNoteContent();
        }
           
        // lookup a fragment for each witness
        for ( Entry<Long, WitnessFragment> entry : witnessDiffMap.entrySet()) {
            Long witnessId = entry.getKey();
            WitnessFragment info = entry.getValue();
            Witness w = witnessMap.get( witnessId );
            Range fragRange = new Range(
                Math.max(0, info.range.getStart() - FRAG_SIZE), 
                Math.min(info.range.getEnd()+FRAG_SIZE, w.getText().getLength()));
            try {
                final RangedTextReader reader = new RangedTextReader();
                reader.read( this.witnessDao.getContentStream(w), fragRange );
                info.fragment = FragmentFormatter.format(reader.toString(), info.range, fragRange, w.getText().getLength());
            } catch (Exception e) {
                LOG.error("Error retrieving diff fragment for witness "+witnessId, e);
            }
            
            // see if this particular witness diff has been annotated
            if ( userAnno != null ) {
                for ( UserAnnotation.Data noteData : userAnno.getNotes() ) {
                    if ( noteData.getWitnessId().equals(witnessId))  {
                        info.note = noteData.getText();
                    }
                }
            }
        }

        JsonObject out = new JsonObject();
        out.addProperty("groupAnnotation", groupAnnotation);
        JsonArray frags = new JsonArray();
        out.add("fragments", frags);
        for ( WitnessFragment f :  witnessDiffMap.values() ) {
            JsonObject obj = new JsonObject();
            obj.addProperty("witnessId", f.witnessId);
            obj.addProperty("witnessName", f.witnessName);
            obj.addProperty("typeSymbol", f.typeSymbol);
            obj.addProperty("fragment", f.fragment);
            obj.addProperty("note", f.note);
            JsonObject rng = new JsonObject();
            rng.addProperty("start", f.getStart());
            rng.addProperty("end", f.getEnd());
            obj.add("range", rng);
            frags.add(obj);
        }
        
        String goo = out.toString();
        return toJsonRepresentation( goo );
    }

    private static class WitnessFragment {
        private Range range;
        private Long witnessId;
        private String witnessName;
        private final String typeSymbol;
        private String fragment;
        private String note = "";
        
        public WitnessFragment( final Witness witness, final Range r, final int editDist) {
            this.witnessName = witness.getName();
            this.range = r;
            if ( editDist > -1 ) {
                this.typeSymbol = "&#9650;&nbsp;";
            } else if (  r.length() == 0 ) {
                // del
                this.typeSymbol = "&#10006;&nbsp;";    
            } else {
                // add
                this.typeSymbol = "&#10010;&nbsp;";
            }
        }
        public long getStart() {
            return this.range.getStart();
        }
        public long getEnd() {
            return this.range.getEnd();
        }
    }
}
