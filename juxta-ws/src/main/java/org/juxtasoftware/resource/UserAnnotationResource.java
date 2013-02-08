package org.juxtasoftware.resource;

import java.util.List;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.UserAnnotation;
import org.juxtasoftware.model.Witness;
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
        try {
            UserAnnotation ua = parseAnnotation(jsonData, true);
            
            // see if an annotation already exists here. If it does, delete it and replace with current
            for (UserAnnotation a : this.comparionSetDao.listUserAnnotations(this.set, ua.getBaseId(), ua.getBaseRange()) ) {
                if ( a.matches(ua)) {
                    this.comparionSetDao.updateUserAnnotation(a.getId(), ua.getNote());
                    return toTextRepresentation("OK");
                }
            }
            
            this.comparionSetDao.createUserAnnotation(ua);
            return toTextRepresentation("OK");
        } catch (ResourceException e) {
            setStatus(e.getStatus()  );
            return toTextRepresentation(e.getStatus().getDescription());
        }
    }
    
    private UserAnnotation parseAnnotation( final String jsonData, boolean includeNote ) throws ResourceException {
        JsonParser parser = new JsonParser();
        JsonObject jsonObj = parser.parse(jsonData).getAsJsonObject();
        if ( jsonObj.has("base") == false || jsonObj.has("start") == false || jsonObj.has("end") == false ||
             jsonObj.has("witness") == false ) {
           throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Missing required data in json payload");
        }
        
        Long baseId = jsonObj.get("base").getAsLong();
        Range r = new Range( jsonObj.get("start").getAsLong(), jsonObj.get("end").getAsLong());
        Long witnessId = jsonObj.get("witness").getAsLong();
        String note = "";
        if ( jsonObj.has("note")) {
            note = jsonObj.get("note").getAsString();
        }
        if ( includeNote && note.length() == 0 ) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Missing required data in json payload");
        }
        
        // validate that witnesses are part of set
        boolean foundBase = false;
        boolean fountWit= false;
        for (Witness w : this.comparionSetDao.getWitnesses(this.set) ) {
            if ( w.getId().equals(baseId)) {
                foundBase = true;
            }
            if ( w.getId().equals(witnessId)) {
                fountWit = true;
            }
        }
        
        if ( foundBase == false ) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid base identifier specified");
        }
        
        if ( fountWit == false ) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid witness identifier specified");
        }
        
        UserAnnotation ua = new UserAnnotation();
        ua.setBaseId(baseId);
        ua.setBaseRange(r);
        ua.setNote(note);
        ua.setSetId(this.set.getId());
        ua.setWitnessId(witnessId);
        return ua;
    }
    
    @Get("json")
    public Representation get() {
        List<UserAnnotation> ua = this.comparionSetDao.listUserAnnotations(this.set, this.baseId, this.range);
        Gson gson = new Gson();
        return toJsonRepresentation( gson.toJson(ua) );
    }
    
    @Delete("json")
    public void deleteUserAnnotation( final String jsonData ) {
        try {
            UserAnnotation ua = new UserAnnotation();
            ua.setBaseId(this.baseId);
            ua.setWitnessId(this.witnessId);
            ua.setBaseRange(this.range);
            this.comparionSetDao.deleteUserAnnotation(ua);
        } catch (ResourceException e) {
            setStatus(e.getStatus()  );
        }
    }
}
