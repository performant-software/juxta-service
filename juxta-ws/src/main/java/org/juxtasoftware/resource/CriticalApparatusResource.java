package org.juxtasoftware.resource;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.CacheDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.AlignmentConstraint;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.QNameFilter;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.BackgroundTask;
import org.juxtasoftware.util.BackgroundTaskCanceledException;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.juxtasoftware.util.QNameFilters;
import org.juxtasoftware.util.RangedTextReader;
import org.juxtasoftware.util.TaskManager;
import org.restlet.data.Encoding;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.engine.application.EncodeRepresentation;
import org.restlet.representation.ReaderRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import eu.interedition.text.Range;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class CriticalApparatusResource extends BaseResource {

    @Autowired private ComparisonSetDao setDao;
    @Autowired private QNameFilters filters;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private CacheDao cacheDao;
    @Autowired private TaskManager taskManager;
    
    private ComparisonSet set;
    private Long baseWitnessId;
    
    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        Long id = getIdFromAttributes("id");
        if ( id == null ) {
            return;
        }
        this.set = this.setDao.find(id);
        if ( validateModel(this.set) == false ) {
            return;
        }
        
        this.baseWitnessId = null;
        if (getQuery().getValuesMap().containsKey("base")  ) {
            String baseId = getQuery().getValues("base");
            try {
                this.baseWitnessId = Long.parseLong(baseId);
            } catch (NumberFormatException e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid witness identifier specified");
                return;
            }
        }
    }
    
    /**
     * Get a json representation of the critical apparatus data
     */
    @Get("json")
    public Representation get() {
        if ( this.set.getStatus().equals(ComparisonSet.Status.COLLATED) == false ) {
            setStatus(Status.CLIENT_ERROR_CONFLICT);
            return toTextRepresentation("Unable to generate critical apparatus - set is not collated");
        }
        
        List<Witness> witnesses = new ArrayList<Witness>(this.setDao.getWitnesses( this.set ));
        if ( witnesses.size() < 2 ) {
            setStatus(Status.CLIENT_ERROR_CONFLICT);
            return toTextRepresentation("Unable to generate critical apparatus - set contains less than 2 witnesses");
        }
        
        if ( this.baseWitnessId == null ) {
            this.baseWitnessId = witnesses.get(0).getId();
        }
        
        // send back cached data id it is avaialable
        if ( this.cacheDao.criticalApparatusExists(this.set.getId(), this.baseWitnessId)) {
            Reader rdr = this.cacheDao.getCriticalApparatus(this.set.getId(), this.baseWitnessId);
            if ( rdr != null ) {
                Representation rep = new ReaderRepresentation( rdr, MediaType.APPLICATION_JSON);
                if ( isZipSupported() ) {
                    return new EncodeRepresentation(Encoding.GZIP, rep);
                } else {
                    return rep;
                }
            } else {
                LOG.warn("Unable to retrieved cached data for "+set+". Clearing  bad data");
                this.cacheDao.deleteAll(set.getId());
            }
        }
        
        // kick off a task to render the CA
        final String taskId =  generateTaskId(set.getId(), this.baseWitnessId );
        if ( this.taskManager.exists(taskId) == false ) {
            CaTask task = new CaTask(taskId);
            this.taskManager.submit(task);
        } 
        return toJsonRepresentation( "{\"status\": \"RENDERING\", \"taskId\": \""+taskId+"\"}" );
    }
    
    private String generateTaskId( final Long setId, final Long baseId) {
        final int prime = 31;
        int result = 1;
        result = prime * result + setId.hashCode();
        result = prime * result + baseId.hashCode();
        return "criticalapp-"+result;
    }

    private void render() {
        // init the json result
        List<Witness> witnesses = new ArrayList<Witness>(this.setDao.getWitnesses( this.set ));
        JsonObject jsonObj = new JsonObject();
        
        // add base info
        Witness base = null;
        for ( Witness w : witnesses ) {
            if ( w.getId().equals(this.baseWitnessId)) {
                base = w;
                break;
            }
        }
        jsonObj.add("base", getWitnessObj(base) );
        
        // add witnesses
        JsonArray jsonWits = new JsonArray();
        for ( Witness w : witnesses ) {
            if ( w.getId().equals(this.baseWitnessId) == false) {
                jsonWits.add( getWitnessObj(w) );
            }
        }
        jsonObj.add("witnesses", jsonWits);
        
        // add lemmas
        jsonObj.add("lemmas", generateLemmas(base, witnesses ) );
        
        // cache data and return results
        this.cacheDao.cacheCriticalApparatus(this.set.getId(), this.baseWitnessId, new StringReader(jsonObj.toString()));
    }
    
    private JsonElement generateLemmas( final Witness base, final List<Witness> witnesses ) {
        
        QNameFilter changesFilter = this.filters.getDifferencesFilter();
        AlignmentConstraint constraints = new AlignmentConstraint(this.set, this.baseWitnessId);
        constraints.setFilter(changesFilter);
        List<Alignment> alignments = this.alignmentDao.list(constraints);
        
        // add in transpositions
        constraints = new AlignmentConstraint(this.set, this.baseWitnessId);
        constraints.setFilter( this.filters.getTranspositionsFilter() );
        alignments.addAll( this.alignmentDao.list(constraints) );

        if ( alignments.size() == 0) {
            return new JsonArray();
        }
        
        // sort in document order, relative to the base witness
        Collections.sort(alignments, new Comparator<Alignment>() {
            @Override
            public int compare(Alignment a, Alignment b) {
                // NOTE: There is a bug in interedition Range. It will
                // order range [0,1] before [0,0] when sorting ascending.
                // So.. do NOT use its compareTo. Roll own.
                Range r1 = a.getWitnessAnnotation(base.getId()).getRange();
                Range r2 = b.getWitnessAnnotation(base.getId()).getRange();
                if ( r1.getStart() < r2.getStart() ) {
                    return -1;
                } else if ( r1.getStart() > r2.getStart() ) {
                    return 1;
                } else {
                    if ( r1.getEnd() < r2.getEnd() ) {
                        return -1;
                    } else if ( r1.getEnd() > r2.getEnd() ) {
                        return 1;
                    } 
                }
                return 0;
            }
        });
        
        // walk the differences and generate the critical apparatus data
        Map<Range, Lemma> lemmaRangeMap = new HashMap<Range, Lemma>();
        List<Lemma> lemmas = new ArrayList<CriticalApparatusResource.Lemma>();
        Iterator<Alignment> alignItr = alignments.iterator();
        while ( alignItr.hasNext() ) {
            Alignment diff = alignItr.next();
            alignItr.remove();
            
            // grab the base text and find/create lemma for the base range
            AlignedAnnotation baseAnno = diff.getWitnessAnnotation(base.getId());
            Lemma lemma = lemmaRangeMap.get(baseAnno.getRange());
            if ( lemma == null ) {
                lemma= new Lemma( baseAnno.getRange(), diff.getGroup() );
                lemmaRangeMap.put(baseAnno.getRange(), lemma);
                lemmas.add(lemma);
            }
            
            // Find the annotation details for the diff witness 
            AlignedAnnotation witnessAnnotation = null;
            Witness witness = null;
            for ( AlignedAnnotation a : diff.getAnnotations()) {
                if ( a.getWitnessId().equals( base.getId()) == false ) {
                    witnessAnnotation = a;
                    witness = findWitness(witnesses, witnessAnnotation.getWitnessId());
                    break;
                }
            }
                    
            // Add all witness change details to the lemma
            lemma.addWitnessDetail( witness, witnessAnnotation.getRange() );  
        }
        
        Lemma prior = null;
        for (Iterator<Lemma> itr = lemmas.iterator(); itr.hasNext();) {
            Lemma lemma = itr.next();
            if (prior != null) {
                // See if these are a candidate to merge
                if ( lemma.hasMatchingGroup( prior ) && lemma.hasMatchingWitnesses(prior) ){ 
                    prior.merge(lemma);
                    itr.remove();
                    continue;
                }
            } 

            prior = lemma;
        }
        
        JsonArray out = new JsonArray();
        for ( Lemma lemma : lemmas ) {
            JsonObject jo = new JsonObject();
            JsonArray jsonWits = new JsonArray();
            String baseTxt = getWitnessText(base, lemma.getRange());
            String witnessTxt = "";
            for ( Entry<Long, Range> ent : lemma.getWitnessRangeMap().entrySet() ) {
                Long witId = ent.getKey();
                jsonWits.add( new JsonPrimitive(witId) );
                Range witRng = ent.getValue();
                if ( witnessTxt.length() == 0 ) {
                    witnessTxt = getWitnessText( findWitness(witnesses, witId), witRng);
                }
            }
            
            if ( baseTxt.length() == 0 ) {
                jo.addProperty("text","^"+witnessTxt);
            } else if ( witnessTxt.length() == 0 ) {
                jo.addProperty("text", baseTxt+"~");
            } else {
                jo.addProperty("text", baseTxt+" ] "+witnessTxt);
            }
            jo.add("witnesses", jsonWits);
            
            out.add(jo);
        }
        
        return out;
    }
    
    private Witness findWitness(List<Witness> witnesses, Long tgtId) {
        for ( Witness w : witnesses ) {
            if ( w.getId().equals(tgtId) ) {
                return w;
            }
        }
        return null;
    }
    
    private String getWitnessText( final Witness witness, final Range range ) {
        try {
            final RangedTextReader reader = new RangedTextReader();
            reader.read( this.witnessDao.getContentStream(witness), range );
            return reader.toString();
        } catch (Exception e) {
            LOG.error("Unable to get text for "+witness+", "+range, e);
            return "";
        }
    }

    private JsonObject getWitnessObj( Witness w ) {
        JsonObject j = new JsonObject();
        j.addProperty("id", w.getId());
        j.addProperty("name", w.getName() );
        return j;
    }
    
    private static class Lemma {
        private final int group;
        private Range range;
        private Map<Long, Range> witnessRangeMap = new HashMap<Long, Range>();
        
        public Lemma( final Range r, final int groupId) {
            this.range = new Range(r);
            this.group = groupId;
        }
        
        public boolean hasMatchingWitnesses(Lemma other) {
            for ( Long witId : this.witnessRangeMap.keySet() ) {
                if ( other.getWitnessRangeMap().containsKey(witId) == false)  {
                    return false;
                }
            }
            return true;
        }

        public final Range getRange() {
            return this.range;
        }
        
        public final Map<Long, Range> getWitnessRangeMap() {
            return this.witnessRangeMap;
        }

        public void addWitnessDetail(Witness witness, Range range) {
            Range witRange = this.witnessRangeMap.get(witness.getId());
            if (witRange == null) {
                this.witnessRangeMap.put(witness.getId(), range);
            } else {
                Range expanded = new Range(
                    Math.min(witRange.getStart(), range.getStart()),
                    Math.max(witRange.getEnd(), range.getEnd()));
                this.witnessRangeMap.put(witness.getId(), expanded);
            }
        }
        
        public boolean hasMatchingGroup(Lemma prior) {
            if ( this.group == 0 || prior.group == 0 ) {
                return false;
            } else {
                return (this.group == prior.group);
            }
        } 
        
        public void merge( Lemma mergeFrom ) {
            // new range of this change is the  min/max of the two ranges
            this.range = new Range(//
                    Math.min( this.range.getStart(), mergeFrom.getRange().getStart() ),//
                    Math.max( this.range.getEnd(), mergeFrom.getRange().getEnd() )
            );
            
            // for each of the witness details in the merge source, grab the
            // details and add them to the details on this change. note that
            // all witnesses must match up between mergeFrom and this or the
            // merge will not happen. this is enforced in the heatmap render code
            for ( Entry<Long, Range> mergeEntry : mergeFrom.getWitnessRangeMap().entrySet()) {
                Long witId = mergeEntry.getKey();
                Range witRange = mergeEntry.getValue();
                Range thisRange = this.witnessRangeMap.get(witId);
                if ( thisRange == null ) {
                    this.witnessRangeMap.put(witId, witRange);
                } else {
                    Range expanded = new Range(
                        Math.min(witRange.getStart(), thisRange.getStart()),
                        Math.max(witRange.getEnd(), thisRange.getEnd()));
                    this.witnessRangeMap.put(witId, expanded);
                }
            }
        }
    }
    
    /**
     * Task to asynchronously render the visualization
     */
    private class CaTask implements BackgroundTask {
        private final String name;
        private BackgroundTaskStatus status;
        private Date startDate;
        private Date endDate;
        
        public CaTask(final String name) {
            this.name =  name;
            this.status = new BackgroundTaskStatus( this.name );
            this.startDate = new Date();
        }
        
        @Override
        public Type getType() {
            return BackgroundTask.Type.VISUALIZE;
        }
        
        @Override
        public void run() {
            try {
                LOG.info("Begin task "+this.name);
                this.status.begin();
                CriticalApparatusResource.this.render();
                LOG.info("Task "+this.name+" COMPLETE");
                this.endDate = new Date();   
                this.status.finish();
            } catch ( BackgroundTaskCanceledException e) {
                LOG.info( this.name+" task was canceled");
                this.endDate = new Date();
            } catch (Exception e) {
                LOG.error(this.name+" task failed", e);
                this.status.fail(e.toString());
                this.endDate = new Date();       
            }
        }
        
        @Override
        public void cancel() {
            this.status.cancel();
        }

        @Override
        public BackgroundTaskStatus.Status getStatus() {
            return this.status.getStatus();
        }

        @Override
        public String getName() {
            return this.name;
        }
        
        @Override
        public Date getEndTime() {
            return this.endDate;
        }
        
        @Override
        public Date getStartTime() {
            return this.startDate;
        }
        
        @Override
        public String getMessage() {
            return this.status.getNote();
        }
    }
}
