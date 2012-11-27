package org.juxtasoftware.resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.AlignmentConstraint;
import org.juxtasoftware.model.ComparisonSet;
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

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;

import eu.interedition.text.Range;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class FragmentResource extends BaseResource {
    @Autowired private ComparisonSetDao setDao;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private QNameFilters filters;
    
    private Long baseWitnessId;
    private ComparisonSet set;
    private Range range;
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
    }
    
    @Get("json")
    public Representation toJson() {
        // get all of the diff alignments in the specified range
        AlignmentConstraint constraint = new AlignmentConstraint( this.set, this.baseWitnessId );
        constraint.setFilter( this.filters.getDifferencesFilter() );
        constraint.setRange( this.range );
        List<Alignment> aligns = this.alignmentDao.list( constraint );
        
        // consolidate ranges
        Map<Long, WitnessFragment > witnessDiffMap = new HashMap<Long, WitnessFragment>();
        Map<Long, Witness> witnessMap = new HashMap<Long, Witness>();
        for (Alignment align : aligns ) {
            
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
                witnessDiffMap.put(witnessAnno.getWitnessId(), info);
            } else {
                info.range = new Range(
                    Math.min(info.getStart(), witnessAnno.getRange().getStart()), 
                    Math.max(info.getEnd(), witnessAnno.getRange().getEnd()));
            }
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
                e.printStackTrace();
            }
        }
        Gson gson = new Gson();
        return toJsonRepresentation( gson.toJson(witnessDiffMap.values()) );
    }

    private static class WitnessFragment {
        private Range range;
        @Expose private Long witnessId;
        @Expose private String witnessName;
        @Expose private final String typeSymbol;
        @Expose private String fragment;
        
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
