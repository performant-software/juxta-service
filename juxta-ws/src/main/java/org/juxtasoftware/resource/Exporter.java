package org.juxtasoftware.resource;

import java.io.IOException;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Witness;
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
    private ComparisonSet set;

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
    }
    
    @Get("xml")
    public Representation exportSet() {
        try {
            String template = IOUtils.toString(ClassLoader.getSystemResourceAsStream("templates/xml/teips.xml"));
            template = template.replace("$TITLE", this.set.getName());
            
            Set<Witness> witnesses = this.setDao.getWitnesses(this.set);
            
            final String listWit = generateListWitContent(witnesses);
            template = template.replace("$LISTWIT", listWit);
            
            return toTextRepresentation(template);
        } catch (IOException e) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Unable to export comparison set");
        }
        
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
