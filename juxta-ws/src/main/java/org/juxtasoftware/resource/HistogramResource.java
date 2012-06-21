package org.juxtasoftware.resource;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.AlignmentConstraint;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.QNameFilter;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.QNameFilters;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Resource used to GET a json object containing histogram
 * data for a comparison set
 * 
 * @author loufoster
 *
 */
public class HistogramResource extends BaseResource {
    @Autowired private ComparisonSetDao setDao;
    @Autowired private QNameFilters filters;
    @Autowired private AlignmentDao alignmentDao;
    
    private ComparisonSet set;
    private Long baseWitnessId;
    
    protected static final Logger LOG = LoggerFactory.getLogger( "JuxtWsResource" );
    
    @Override
    protected void doInit() throws ResourceException {

        super.doInit();
        
        Long setId = Long.parseLong( (String)getRequest().getAttributes().get("id"));
        this.set = this.setDao.find(setId);
        if ( validateModel(this.set) == false) {
            return;
        }
        
        if (getQuery().getValuesMap().containsKey("base") == false ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing base parameter");
        } else {
            String baseId = getQuery().getValues("base");
            this.baseWitnessId = Long.parseLong(baseId);
        }
    }
    
    @Get("json")
    public Representation toJson() throws IOException {
        // Algorithm: 
        // The histogram is an array of integers sized to match 
        // the length of the base document.
        // Get all diffs for the selected set.
        // Walk thru each, and get the base diff start and end position.
        // For each position in range from start to end, add 1 to 
        // the corresponding position in the histogram array.
        
        // start of by getting the base witness and the cout of all 
        // witnesses in this comparison set
        Set<Witness> witnesses = this.setDao.getWitnesses(this.set);
        Witness base = null;
        int maxValue = witnesses.size();
        for ( Witness w : witnesses ) {
            if ( w.getId().equals(this.baseWitnessId)) {
                base = w;
                break;
            }
        }
        
        // next, get al of the differences and apply the above algorithm
        LOG.info("Create histogram buffer size " + (int)base.getText().getLength());
        try{
            byte[] histogram = createHistogram( (int)base.getText().getLength() );
            QNameFilter changesFilter = this.filters.getDifferencesFilter();
            AlignmentConstraint constraints = new AlignmentConstraint(this.set, this.baseWitnessId);
            constraints.setFilter(changesFilter);
            List<Alignment> diffs =  this.alignmentDao.list(constraints);
            for (Alignment diff :  diffs ) {
                
                // get the base witness annotation
                AlignedAnnotation anno = diff.getWitnessAnnotation(this.baseWitnessId);
                
                // mark of its range in the histogram
                int start = (int)anno.getRange().getStart();
                int end = (int)anno.getRange().getEnd();
                for (int i=start; i<end; i++) {
                    histogram[i]++;
                }
            }
            
            LOG.info("Created histogram byte buffer. Now creating return data...");
    
            // scale to max value and stuff in out string
            StringBuffer out = new StringBuffer();
            for ( int i=0;i<histogram.length;i++) {
                if ( out.length() > 0) {
                    out.append(",");
                }
                double scaled = (double)histogram[i]/(double)maxValue;
                if ( scaled > 1.0 ) {
                    scaled = 1.0;
                }
                out.append(String.format("%1.2f",  scaled));
            }
            
            String jsonStr = "{\"baseName\": \""+base.getName()+"\", \"histogram\": ["+out.toString()+"]}";
            return toJsonRepresentation( jsonStr );
        } catch (Exception e) {
            LOG.error("Unable to create histogram", e);
            setStatus(Status.SERVER_ERROR_INSUFFICIENT_STORAGE);
            return toTextRepresentation("unable to create histogram!");
        }
    }
    
    /**
     * create and init histogram array with all zeros
     * @param size
     * @return
     */
    private byte[] createHistogram( long size ) {
        byte[] out = new byte[(int)size];
        for ( int i=0; i<size; i++) {
            out[i] = 0;
        }
        return out;
    }
}
