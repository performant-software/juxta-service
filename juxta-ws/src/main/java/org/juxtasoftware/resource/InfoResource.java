package org.juxtasoftware.resource;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ResourceInfo;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class InfoResource extends BaseResource {
    private String type;
    private Long id;
    
    @Autowired private SourceDao sourceDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private ComparisonSetDao setDao;

    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        
        this.type = (String) getRequestAttributes().get("type");
        this.id = getIdFromAttributes("id");
        if ( this.id == null ) {
            return;
        }
    }
    
    @Get("json")
    public Representation getJsonInfo() {
        ResourceInfo info = null;
        if ( type.equals("source") ) {
            info = this.sourceDao.getInfo(this.id);
        } else if ( type.equals("witness") ) {
            info = this.witnessDao.getInfo(this.id);
        } else if ( type.equals("set") ) {
            info = this.setDao.getInfo(this.id);
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Invalid resource type requested");
        }
        
        if ( info == null ) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return toTextRepresentation("Invaid resource identifer specified");
        }
        
        Gson gson = new GsonBuilder().registerTypeAdapter(ResourceInfo.class, new InfoSerializer()).create();
        return toJsonRepresentation(gson.toJson(info));
    }
    
    private class InfoSerializer implements JsonSerializer<ResourceInfo> {
        private final DateFormat format = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

        @Override
        public JsonElement serialize(ResourceInfo inf, Type typeOfSrc, JsonSerializationContext context) {

            JsonObject obj = new JsonObject();
            obj.add("id", new JsonPrimitive(inf.getId()));
            obj.add("workspace", new JsonPrimitive(inf.getWorkspace()));
            obj.add("name", new JsonPrimitive(inf.getName()));
            obj.add("created", new JsonPrimitive(this.format.format(inf.getDateCreated())));
            return obj;
        }

    }
}
