package org.juxtasoftware.resource;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.FileWriterWithEncoding;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.CacheDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.AlignmentConstraint;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.QNameFilter;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.QNameFilters;
import org.restlet.data.Encoding;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.engine.application.EncodeRepresentation;
import org.restlet.representation.ReaderRepresentation;
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
    @Autowired private CacheDao cacheDao;
    
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
        // FIRST, see if the cached version is available:
        if ( this.cacheDao.histogramExists(this.set.getId(), this.baseWitnessId)) {
            Representation rep = new ReaderRepresentation( 
                this.cacheDao.getHistogram(this.set.getId(), this.baseWitnessId), 
                MediaType.APPLICATION_JSON);
            return new EncodeRepresentation(Encoding.GZIP, rep);
        }
        
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
        
        // NOTE: rough size of alignment object has been determined to be 5600 bytes
        // Determination was rough; dump bytes free, query a range of aligments
        // get difference in bytes free and divide by alignment count. Average size
        // was about  5600 bytes. Over estimate here for a bit of safety.
        final long estimatedAlignmentSize = 7500;
        
        long bytesFree = Runtime.getRuntime().freeMemory();
        LOG.info("["+bytesFree+"] bytes free at start of histogram request");
        
        // set up a filter to get the annotations necessary for this histogram
        QNameFilter changesFilter = this.filters.getDifferencesFilter();
        AlignmentConstraint constraints = new AlignmentConstraint(this.set, this.baseWitnessId);
        constraints.setFilter(changesFilter);
        
        // Get the number of annotations that will be returned and do a rough calcuation
        // to see if generating this histogram will exhaust available memory - with a 5M pad
        Long count = this.alignmentDao.count(constraints);
        long estimatedByteUsage = count*estimatedAlignmentSize + base.getText().getLength();
        if ( (bytesFree - estimatedByteUsage) / 1048576 <= 5) {
            setStatus(Status.SERVER_ERROR_INSUFFICIENT_STORAGE);
            return toTextRepresentation("Insufficient resources to create histogram. Try again later.");
        }
        
        // next, get al of the differences and apply the above algorithm
        LOG.info("Create histogram buffer size " + (int)base.getText().getLength());
        byte[] histogram = createHistogram( (int)base.getText().getLength() );
        LOG.info( Runtime.getRuntime().freeMemory()+" bytes free after histogram buffer");
        List<Alignment> diffs =  this.alignmentDao.list(constraints);
        LOG.info( Runtime.getRuntime().freeMemory()+" bytes free after alignment retrieval");
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
        diffs.clear();
        diffs = null;

        // scale to max value and dump to temp file
        File hist = File.createTempFile("histo", "data");
        hist.deleteOnExit();
        BufferedWriter bw = new BufferedWriter( new FileWriterWithEncoding(hist, "UTF-8") );
        try {
            boolean firstWrite = true;
            bw.write( "{\"baseName\": \""+base.getName()+"\", \"histogram\": [" );
            for ( int i=0;i<histogram.length;i++) {
                if ( firstWrite == false ) {
                    bw.write(",");
                }
                firstWrite = false;
                double scaled = (double)histogram[i]/(double)maxValue;
                if ( scaled > 1.0 ) {
                    scaled = 1.0;
                }
                bw.write(String.format("%1.2f",  scaled));
            }
            bw.write("] }");
        } catch (IOException e) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Unable to generate histogram");
        } finally {
            IOUtils.closeQuietly(bw);
        }
        
        // cache the results and kill temp file
        FileReader r = new FileReader(hist);
        this.cacheDao.cacheHistogram(this.set.getId(), this.baseWitnessId, r);
        IOUtils.closeQuietly(r);
        hist.delete();
        
        // send cached data from DB up to client
        Representation rep = new ReaderRepresentation( this.cacheDao.getHistogram(this.set.getId(), this.baseWitnessId), MediaType.APPLICATION_JSON);
        return new EncodeRepresentation(Encoding.GZIP, rep);
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
