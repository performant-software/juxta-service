package org.juxtasoftware;

import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public class TemplateTest {

    private final String baseUrl;
    private static final Logger LOG = Logger.getRootLogger();
    private final String templateName = "t-"+UUID.randomUUID().toString();
    private final String templateName2 = "t-2-"+UUID.randomUUID().toString();
    
    private final String jsonTemplate = 
        "{'name':'"+this.templateName+"'," +
        " 'isDefault': 'true', " +
        " 'rootElement': { 'namespaceUri': '*', 'namespacePrefix': '*', 'localName':'TEI'}, " +
                "'tagActions':" +
                "[{ tag: {'namespaceUri': '*', 'namespacePrefix': '*', 'localName':'p'},'action':'INCLUDE'}," +
                " { tag: {'namespaceUri': '*', 'namespacePrefix': '*', 'localName':'div'},'action':'INCLUDE'}]}";
    private final String jsonTemplate2 = 
        "{'name':'"+this.templateName2+"'," +
        " 'isDefault': 'true', " +
        " 'rootElement': { 'namespaceUri': '*', 'namespacePrefix': '*', 'localName':'TEI'}, " +
                "'tagActions':" +
                "[{ tag: {'namespaceUri': '*', 'namespacePrefix': '*', 'localName':'p'},'action':'EXCLUDE'}," +
                " { tag: {'namespaceUri': '*', 'namespacePrefix': '*', 'localName':'div'},'action':'EXCLUDE'}]}";
    
    public TemplateTest(String baseUrl) {
        this.baseUrl = baseUrl + "/template";
    }

    public void runTests() {
        try {
            LOG.info("Testing TEMPLATE API");
            Long templateID = postTemplate( );
            postDuplicateTemplate();
            getTemplates( templateID );
            getTemplate( templateID );
            updateTemplate( templateID );

            testDefaults( templateID );
            
            deleteTemplate( templateID );
            
            uploadTemplates();
            
            LOG.info("SUCCESS: Testing TEMPLATE API");
        } catch (Exception e) {
            LOG.error("FAILED: Testing TEMPLATE API",e);
        }
    }

    private void uploadTemplates() throws Exception {
        LOG.info("* Upload Juxta Desktop Templates file");
        String xml = IOUtils.toString( new FileInputStream(new File("data/templates.xml")), "UTF-8");
        String out =  Helper.postXml(this.baseUrl, xml);
        JsonParser parser = new JsonParser();
        JsonArray array = parser.parse(out).getAsJsonArray();
        if ( array.size() != 3) {
            LOG.error("FAIL: Juxta XML upload returned wrong ID count");
        }
        
        LOG.info("* Delete uploaded Juxta Desktop templates");
        for ( int i=0; i<array.size(); i++) {
            Long id = array.get(i).getAsLong();
            Helper.delete( this.baseUrl, id);
        }
    }

    private void deleteTemplate(Long templateID) throws Exception {
        LOG.info("* Delete template");
        Helper.delete( this.baseUrl, templateID);
    }

    private void getTemplate( Long templateID) throws Exception {
        LOG.info("* Get template");
        String resp = Helper.getJson(this.baseUrl, templateID);
        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(resp).getAsJsonObject();
        Long id = obj.get("id").getAsLong();
        if ( templateID.equals(id) == false ) {
            throw new Exception("Unable to GET template "+templateID);
        } 
        if ( obj.get("isDefault").getAsBoolean() == false ) {
            throw new Exception("GET template "+templateID+ " default setting wrong");
        }
    }

    private void getTemplates(Long expectedTemplateID) throws Exception {
        LOG.info("* Get all templates");
        String response = Helper.getJson(this.baseUrl);
        
        // parse it and look for ID in results
        boolean success = false;
        JsonParser parser = new JsonParser();
        JsonArray jsonArray = parser.parse ( response ).getAsJsonArray();
        for ( Iterator<JsonElement> itr = jsonArray.iterator(); itr.hasNext(); ) {
            JsonObject obj = itr.next().getAsJsonObject();
            Long id = obj.get("id").getAsLong();
            if ( expectedTemplateID.equals(id) ) {
                success = true;
                break;
            }
        }
        if ( success == false ) {
            throw new Exception("Expected template ID not found in response");
        }
    }
    
    private void updateTemplate( Long templateID ) throws Exception {

        LOG.info("* Update template");
        String resp = Helper.getJson(this.baseUrl, templateID);
        JsonParser parser = new JsonParser();
        JsonObject originalJson = parser.parse(resp).getAsJsonObject();
        originalJson.remove("isDefault");
        originalJson.add("isDefault", new JsonPrimitive(false));
        originalJson.get("tagActions").getAsJsonArray().add( 
            parser.parse("{tag: {'namespaceUri': '*', 'namespacePrefix': '*', 'localName' :'p'},'action':'NEW_LINE'}") );
        
        // update!
        Helper.put(this.baseUrl, templateID, originalJson.toString());
        
        LOG.info("* Validate update");
        String response = Helper.getJson(this.baseUrl, templateID);
        JsonObject obj = parser.parse(response).getAsJsonObject();
        Long id = obj.get("id").getAsLong();
        if ( templateID.equals(id) == false ) {
            throw new Exception("Unable to GET updated template "+templateID);
        } 
        if ( obj.get("isDefault").getAsBoolean() == true ) {
            throw new Exception("isDefault update failed for template "+templateID);
        }
        
        int actCnt = obj.get("tagActions").getAsJsonArray().size();
        if ( actCnt != 3) {
            throw new Exception("Update failed to add tag action");
        }
    }

    private Long postTemplate() throws Exception {
        LOG.info("* Create template");
        return Helper.post(this.baseUrl, jsonTemplate);
    }
    
    
    private void testDefaults(Long templateID) throws Exception {
        LOG.info("* Create NEW default template");
        Long newId = Helper.post(this.baseUrl, jsonTemplate2);
        
        LOG.info("* Make sure old template is not default");
        String resp = Helper.getJson(this.baseUrl, templateID);
        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(resp).getAsJsonObject();
        Long id = obj.get("id").getAsLong();
        if ( templateID.equals(id) == false ) {
            throw new Exception("Unable to GET template "+templateID);
        } 
        if ( obj.get("isDefault").getAsBoolean() == true ) {
            throw new Exception("GET template "+templateID+ " default setting wrong");
        }
        
        LOG.info("* Make sure new template is default");
        resp = Helper.getJson(this.baseUrl, newId);
        obj = parser.parse(resp).getAsJsonObject();
        if ( obj.get("isDefault").getAsBoolean() == false ) {
            throw new Exception("GET template "+templateID+ " default setting wrong");
        }
        
        deleteTemplate(newId);
    }
    
    private void postDuplicateTemplate() throws Exception {
        LOG.info("* Create DUPLICATE template");
        String resp = Helper.doPostError(this.baseUrl, jsonTemplate);
        if ( resp.contains("already exists") == false) {
            throw new Exception("Acceted duplicate template");
        }
    }
    
}
