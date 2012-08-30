package org.juxtasoftware.resource;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.JuxtaAnnotationDao;
import org.juxtasoftware.dao.WitnessDao;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import eu.interedition.text.Range;

/**
 * Resource used to export sets in various formats. 
 *  
 * @author lfoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class Exporter extends BaseResource {
    @Autowired private ComparisonSetDao setDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private QNameFilters qnameFilters;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private JuxtaAnnotationDao annotationDao;
    private ComparisonSet set;
    private Witness base;

    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        
        Long setId = getIdFromAttributes("id");
        if ( setId == null ) {
            return;
        }
        this.set = this.setDao.find(setId);
        if ( validateModel(this.set) == false ) {
            return;
        }
        
        if (getQuery().getValuesMap().containsKey("mode") ) {
            String mode = getQuery().getValuesMap().get("mode").toLowerCase();
            if ( mode.equals("teips") == false ) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Unsupported export mode specified");
            }
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing required mode parameter");
        }
        
        if (getQuery().getValuesMap().containsKey("base") ) {
            String idStr = getQuery().getValuesMap().get("base");
            Long id = null;
            try {
                id = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid base identifer specified");
            }
            
            this.base = this.witnessDao.find(id);
            if ( validateModel(this.base) == false ) {
                return;
            }
            
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing required base parameter");
        }
    }
    
    @Get("xml")
    public Representation exportSet() {
        try {
            String template = IOUtils.toString(ClassLoader.getSystemResourceAsStream("templates/xml/teips.xml"));
            template = template.replace("$TITLE", "XX-"+this.set.getName());
            
            Set<Witness> witnesses = this.setDao.getWitnesses(this.set);
            
            final String listWit = generateListWitContent(witnesses);
            template = template.replace("$LISTWIT", listWit);
            
            File appFile = generateApparatus(witnesses);
            FileInputStream fis= new FileInputStream(appFile);
            String hack = IOUtils.toString(fis);
            System.err.println( hack );
            IOUtils.closeQuietly(fis);
            appFile.delete();
            
            template = template.replace("$BODY", hack);
            
            return toTextRepresentation(template);
        } catch (IOException e) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Unable to export comparison set");
        }
        
    }

    private File generateApparatus(Set<Witness> witnesses) throws IOException {
        // Algo: stream text from the pase witness until a diff is found
        // at that point, inject an <app>. Each witness content will be
        // added in <rdg> tags.
        // TODO this choice needs to be documented somewhere
        // TODO also note that the final output may not strictly adhere to TEI PS
        // output if things like ignore caps / punctuation are set. ie it could
        // show 'cat.' as the same as 'CAT!'. This should be documented somewhere.
        
        // setup readers/writers for the data
        File out = File.createTempFile("ps_app", "dat");
        out.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(out); 
        OutputStream bout = new BufferedOutputStream(fos);
        OutputStreamWriter ow = new OutputStreamWriter(bout, "UTF-8");
        Reader witReader = this.witnessDao.getContentStream(this.base);
        ow.write("<p>");
        
        // get a batch of alignments to work with... when these are used up
        // another batch will be retrieved
        //final int alignBatchSize = 1000;
        //int alignsFrom = 0;
        QNameFilter changesFilter = this.qnameFilters.getDifferencesFilter();
        AlignmentConstraint constraint = new AlignmentConstraint(set, this.base.getId());
        constraint.setFilter(changesFilter);
        //constraint.setResultsRange(alignsFrom, alignBatchSize);
        List<Alignment> alignments = this.alignmentDao.list(constraint);
        //alignsFrom = alignBatchSize;
        
        List<AppData> appData = generateAppData(alignments);
        Iterator<AppData> itr = appData.iterator();
        
        // set the current align to first in the available list
        AppData currApp = null;
        if ( itr.hasNext() ) {
            currApp = itr.next();
        }
        
        long pos = 0;
        int lastDataWritten = -1;
        while ( true ) {
            int data = witReader.read();
            if ( data == -1 ) {
                break;
            }
            
            // new lines in base turn into TEI linebreaks
            if ( data == '\n' ) {
                ow.write("<lb/>");
            }
            
            if ( currApp != null && pos == currApp.getBaseRange().getStart() ) {
                
                boolean firstPass = true;
                while ( true ) {
                    
                    // write the initial APP, RDG tags
                    ow.write("\n<app>\n");
                    ow.write( "   "+generateRdgTag(witnesses, currApp) );
                    
                    // write the character that triggered this first
                    // Note that this only applies on the first time thru this
                    // loop. Additional entries will not have data pre-seeded
                    // with the initial rdg character.
                    if ( firstPass == true ) {
                        ow.write((char)data);   
                        pos++;
                        firstPass = false;
                    }

                    // write the rest of the rdg content
                    while ( pos < currApp.getBaseRange().getEnd() ) {                       
                        data = witReader.read();
                        if ( data == -1 ) {
                            throw new IOException("invalid aligment: past end of document");
                        } else {
                            if ( data == '\n') {
                                ow.write("<lb/>");
                            } else {
                                ow.write((char)data);
                            }
                            pos++;
                        }
                    }
                    
                    // end the rdg tag 
                    ow.write("</rdg>\n");
 
                    // write witnesses
                    for ( Entry<Long, Range> entry : currApp.getWitnessData().entrySet()) {
                        final String rdg = String.format("   <rdg wit=\"#wit-%d\">", entry.getKey());
                        ow.write(rdg);
                        if ( lastDataWritten != -1 && Character.isWhitespace(lastDataWritten) == false ) {
                            ow.write(" ");
                        }
                        ow.write( getWitnessFragment(entry.getKey(), entry.getValue() ) );
                        ow.write("</rdg>\n");
                    }
                    ow.write("</app>");
                    
                    // move on to the next annotation
                    currApp = null;
                    if ( itr.hasNext() ) {
                        currApp = itr.next();
                        if ( currApp.getBaseRange().getStart() > pos ) {
                            break;
                        }
                    } else {
                        break;
                    }
                }
                
            } else {
                ow.write(data);
                pos++;
                lastDataWritten = data;
            }
        }
        
        ow.write("</p>");
        
        IOUtils.closeQuietly(ow);
        IOUtils.closeQuietly(witReader);
        return out;
    }
    
    private String generateRdgTag(Set<Witness> witnesses, AppData currApp) {
        
        // any wit ids that are NOT present in the app data are the
        // same as the base text. be sure to add them to the rdg below
        List<Long> ids = new ArrayList<Long>();
        for ( Witness w : witnesses ) {
            if ( currApp.getWitnessData().containsKey(w.getId()) == false ) {
                ids.add(w.getId());
            }
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("<rdg wit=\"");
        int cnt=0;
        for (Long id : ids) {
            if ( cnt > 0 ) {
                sb.append(" ");
            }
            sb.append("#wit-").append(id);
            cnt++;
        }
        sb.append("\">");
        return sb.toString();
    }

    /**
     * Extract the text fragment for a witness. NOTE: the isAddedContent flag is necessary
     * to ensure that the proper trailing non-token text gets addded to the <rdg> tag. Without it
     * the pieced together witness would run together without spacing/punctuation for base GAPS
     * @param witId
     * @param range
     * @param isAddedContent
     * @return
     * @throws IOException
     */
    private String getWitnessFragment(Long witId, Range range ) throws IOException {
        Witness w = this.witnessDao.find(witId);
        Reader r = this.witnessDao.getContentStream(w);
        StringBuilder buff = new StringBuilder();
        long pos= 0;
        while ( true) {
            int data = r.read();
            if ( data == -1 ) {
                return buff.toString();
            }
            if ( pos >= range.getStart() && pos < range.getEnd()) {
                if ( data == '\n') {
                    buff.append("<lb/>");
                } else {
                    buff.append((char)data);
                }
                if ( pos == range.getEnd() ) {
                    return buff.toString();
                }
            }
            pos++;
        }
    }

    private List<AppData> generateAppData( List<Alignment> alignments ) {
        List<AppData> data = new ArrayList<Exporter.AppData>();
        Map<Range, AppData> changeMap = new HashMap<Range, AppData>();
        Iterator<Alignment> itr = alignments.iterator();
        while ( itr.hasNext() ) {
            Alignment align = itr.next();
            itr.remove();
            
            // get base and add it to list of found ranges or get
            // pre-existing data for that range
            AlignedAnnotation baseAnno = align.getWitnessAnnotation(this.base.getId());
            Range baseRange = baseAnno.getRange();
            AppData appData = changeMap.get(baseRange);
            if ( appData == null ) {
                appData= new AppData( this.base.getId(), baseRange, align.getGroup() );
                changeMap.put(baseRange, appData);
                data.add(appData);
            }
            
            // add witness data to the app info
            for ( AlignedAnnotation a : align.getAnnotations()) {
                if ( a.getWitnessId().equals( base.getId()) == false ) {
                    Range r = a.getRange();
//                    long n = this.annotationDao.findNextTokenStart( a.getWitnessId(), r.getEnd());
//                    r = new Range(r.getStart(),n);
                    appData.addWitness(a.getWitnessId(), r);
                    break;
                }
            }
        }
        
        // take a pass thru the data and merge items with same group id
        Iterator<AppData> appItr = data.iterator();
        AppData prior = null;
        while ( appItr.hasNext() ) {
            AppData curr = appItr.next();
            
            // extend ranges so spacing is correct in ps output
//            long n = this.annotationDao.findNextTokenStart( curr.getBaseId(), curr.getBaseRange().getEnd());
//            curr.baseRange = new Range(curr.getBaseRange().getStart(), n);
            
            if (prior != null) {
                if ( prior.canMerge( curr )) {
                    prior.merge(curr);
                    appItr.remove();
                } else {
                    prior = curr;
                }
            } else {
                prior = curr;
            }
        }
        
        return data;
    }

    private String generateListWitContent(Set<Witness> witnesses) throws IOException {
        StringBuilder listWit = new StringBuilder();
        for (Witness w : witnesses ) {
            if ( listWit.length() > 0 ) {
                listWit.append("\n                    ");
            }
            String frag = IOUtils.toString(ClassLoader.getSystemResourceAsStream("templates/xml/listwit_frag.xml"));
            frag = frag.replace("$NAME", "XX-"+w.getName());
            frag = frag.replace("$ID", "wit-"+w.getId().toString());
            listWit.append(frag);
        }
        return listWit.toString();
    }
    
    private static class AppData {
        private Long baseId;
        private int groupId;
        private Range baseRange;
        private Map<Long, Range> witnessRanges = new HashMap<Long, Range>();
        
        public AppData( Long baseId, Range r, int groupId) {
            this.baseId = baseId;
            this.baseRange = new Range(r);
            this.groupId = groupId;
        }
        public void addWitness( Long id, Range r) {
            Range orig = this.witnessRanges.get(id);
            if ( orig == null ) {
                this.witnessRanges.put(id, new Range(r));
            } else {
                this.witnessRanges.put(id, new Range( 
                    Math.min( orig.getStart(), r.getStart() ),
                    Math.max( orig.getEnd(), r.getEnd() ) 
                ));
            }
            
        }
        public Map<Long, Range> getWitnessData() {
            return this.witnessRanges;
        }
        public boolean canMerge( AppData other) {
            return this.groupId == other.groupId && 
                   this.baseId.equals(other.getBaseId()) && 
                   hasMatchingWitnesses(other);
        }
        private boolean hasMatchingWitnesses(AppData other) {
            for ( Long witId : this.witnessRanges.keySet() ) {
                if ( other.witnessRanges.containsKey(witId) == false ) {
                    return false;
                }
            }
            return true;
        }
        public void merge(AppData other) {
            this.baseRange = new Range( 
                Math.min( this.baseRange.getStart(), other.getBaseRange().getStart() ),
                Math.max( this.baseRange.getEnd(), other.getBaseRange().getEnd() )
                );
            for (Entry<Long, Range> entry : other.witnessRanges.entrySet() ) {
                Range oldRange = this.witnessRanges.get(entry.getKey());
                if (oldRange == null ) {
                    this.witnessRanges.put(entry.getKey(), entry.getValue());
                } else {
                    Range newRange = new Range( 
                        Math.min( oldRange.getStart(), entry.getValue().getStart() ),
                        Math.max( oldRange.getEnd(),  entry.getValue().getEnd() )
                        );
                    this.witnessRanges.put( entry.getKey(), newRange );
                }
            }
        }
        public Long getBaseId() {
            return this.baseId;
        }
        public Range getBaseRange() {
            return this.baseRange;
        }
    }
}
