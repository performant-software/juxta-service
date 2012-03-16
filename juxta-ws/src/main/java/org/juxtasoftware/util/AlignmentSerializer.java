package org.juxtasoftware.util;

import java.lang.reflect.Type;

import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * GSON serializer class used to ensure proper json format 
 * for alignments
 * 
 * @author loufoster
 *
 */
public class AlignmentSerializer implements JsonSerializer<Alignment> {
    public JsonElement serialize(Alignment align, Type typeOfSrc, JsonSerializationContext context) {

        JsonObject name = new JsonObject();
        name.addProperty("namespace", align.getName().getNamespace().toString());
        name.addProperty("localName", align.getName().getLocalName());

        JsonArray annotations = new JsonArray();
        for (AlignedAnnotation anno : align.getAnnotations()) {
            JsonObject range = new JsonObject();
            range.addProperty("start", anno.getRange().getStart());
            range.addProperty("end", anno.getRange().getEnd());

            JsonObject annoObj = new JsonObject();
            annoObj.addProperty("id", anno.getId());
            annoObj.addProperty("witnessId", anno.getWitnessId());
            annoObj.add("range", range);
            annoObj.addProperty("fragment", anno.getFragment());
            annotations.add(annoObj);
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("id", align.getId());
        obj.add("name", name);
        obj.addProperty("editDistance", align.getEditDistance());
        obj.add("annotations", annotations);
        return obj;
    }
}