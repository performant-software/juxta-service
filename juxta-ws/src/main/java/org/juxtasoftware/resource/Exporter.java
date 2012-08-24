package org.juxtasoftware.resource;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.Alignment;
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
            template = template.replace("$TITLE", this.set.getName());
            
            Set<Witness> witnesses = this.setDao.getWitnesses(this.set);
            
            final String listWit = generateListWitContent(witnesses);
            template = template.replace("$LISTWIT", listWit);
            
            File appFile = generateApparatus(witnesses);
            
            return toTextRepresentation(template);
        } catch (IOException e) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Unable to export comparison set");
        }
        
    }

    private File generateApparatus(Set<Witness> witnesses) throws IOException {
        // Algo: stream text from the pase witness until a diff is found
        // at that point, inject an <app>. Each non-base witness content will be
        // added in <rdg> tags. The base version in a <lem> tag.
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
        final int alignBatchSize = 1000;
        int alignsFrom = 0;
        QNameFilter changesFilter = this.qnameFilters.getDifferencesFilter();
        AlignmentConstraint constraint = new AlignmentConstraint(set, this.base.getId());
        constraint.setFilter(changesFilter);
        constraint.setResultsRange(alignsFrom, alignBatchSize);
        List<Alignment> alignments = this.alignmentDao.list(constraint);
        alignsFrom = alignBatchSize;
        Iterator<Alignment> itr = alignments.iterator();
        
        // set the current align to first in the available list
        Alignment currAlign = null;
        if ( itr.hasNext() ) {
            currAlign = itr.next();
        }
        
        int pos = 0;
        while ( true ) {
            int data = witReader.read();
            if ( data == -1 ) {
                break;
            }
            
            ow.write(data);
            
            pos++;
        }
        
        ow.write("</p>");
        
        IOUtils.closeQuietly(ow);
        IOUtils.closeQuietly(witReader);
        return null;
    }

    private String generateListWitContent(Set<Witness> witnesses) throws IOException {
        StringBuilder listWit = new StringBuilder();
        for (Witness w : witnesses ) {
            if ( listWit.length() > 0 ) {
                listWit.append("\n                    ");
            }
            String frag = IOUtils.toString(ClassLoader.getSystemResourceAsStream("templates/xml/listwit_frag.xml"));
            frag = frag.replace("$NAME", w.getName());
            frag = frag.replace("$ID", "wit-"+w.getId().toString());
            listWit.append(frag);
        }
        return listWit.toString();
    }
}
