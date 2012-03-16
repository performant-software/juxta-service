package org.juxtasoftware;

import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ComparisonSetTest {
    private final String baseUrl;
    private Long annotationId;
    private JsonParser parser = new JsonParser();
    private static final Logger LOG = Logger.getRootLogger();
    
    private String setName = "set-"+UUID.randomUUID().hashCode();
    private SetHelper setHelper;
    private FilterHelper profileHelper;
    
    
    public ComparisonSetTest(String url) {
        this.baseUrl = url;
        this.setHelper = new SetHelper(url, SetHelper.basic);
        this.profileHelper = new FilterHelper(url);
    }
    
    public void runTests() throws Exception {
        Long id = null;
        Long profileId = null;
        try {
            LOG.info("TESTING: Comparison Set API");
            this.setHelper.setupSourceAndWitness();
            profileId = this.profileHelper.createTokenFilter();
            id = createSet();
            testGetAll(id);
            testGet(id);
            testUpdate(id);
            testTokenize(id);
            testGetAnnotations( id);
            testGetAnnotationsByProfile(id);
            testGetAnnotation( id, this.annotationId );
            testDeleteAnnotation( id, this.annotationId );
            testAddAnnotationAndAlignment( id );
            
            LOG.info("SUCCESS: Comparison Set API");
        } catch (Exception e ) {
            LOG.error("FAILED: Comparison Set API", e);
            e.printStackTrace();
        } finally {
            if ( id != null ) {
                deleteSet(id);
            }
            if ( profileId != null ) {
                this.profileHelper.delete(profileId);
            }
            this.setHelper.cleanupSourceAndWitness();
        }
    }

    private void testAddAnnotationAndAlignment(Long setId) throws Exception {
        LOG.info("* Test ADD annotations");
        // add a lizard annotation on 'the apple'
        long wId = this.setHelper.getWitnessId(0);
        String j1 = "[ {\"witnessId\":"+wId+",\"name\":{\"namespace\":\"http://juxtasoftware.org/ns\",\"localName\":\"lizard\"},\"range\":{\"start\":0,\"end\":9}}]";
        Long annoId = Helper.post(this.baseUrl+"/set/"+setId+"/witness/"+wId+"/annotation", j1);
        
        // add a lizard annotation on 'the pear'
        long wId2 = this.setHelper.getWitnessId(2);
        String j2 = "[ {\"witnessId\":"+wId2+",\"name\":{\"namespace\":\"http://juxtasoftware.org/ns\",\"localName\":\"lizard\"},\"range\":{\"start\":0,\"end\":8}}]";
        Long annoId2 = Helper.post(this.baseUrl+"/set/"+setId+"/witness/"+wId+"/annotation", j2);
        
        LOG.info("* Validate ADDED annotation");
        String result = Helper.getJson(this.baseUrl+"/set/"+setId+
            "/witness/"+this.setHelper.getWitnessId(0)+"/annotation/"+annoId+"?content=1");
        JsonObject obj = this.parser.parse(result).getAsJsonObject();
        String content = obj.get("content").getAsString().trim();
        if ( content.equals("The apple") == false) {
            throw new Exception("bad content returned");
        }
        result = Helper.getJson(this.baseUrl+"/set/"+setId+
            "/witness/"+this.setHelper.getWitnessId(2)+"/annotation/"+annoId2+"?content=1");
        obj = this.parser.parse(result).getAsJsonObject();
        content = obj.get("content").getAsString().trim();
        if ( content.equals("The pear") == false) {
            throw new Exception("bad content returned");
        }
        
        LOG.info("* Test ADD alignment with editDistance and new name");
        StringBuilder j3 = new StringBuilder();
        j3.append("[");
        j3.append("  {");
        j3.append("    \"name\": {");
        j3.append("         \"namespace\": \"http://juxtasoftware.org/ns\",");
        j3.append("         \"localName\": \"ferret\"");
        j3.append("      }, ");
        j3.append("    \"editDistance\": \"5\", ");
        j3.append("    \"annotations\": [ ").append(annoId).append(",").append(annoId2).append("]");
        j3.append("  }");
        j3.append("]");
        Long cnt = Helper.post(this.baseUrl+"/set/"+setId+"/alignment", j3.toString());
        if ( cnt != 1) {
            throw new IOException("Incorrect number of alinments created");
        }
        
        
        LOG.info("* Test GET alignment with editDistance");
        result = Helper.getJson(this.baseUrl+"/set/"+setId+"/alignment");
        JsonArray alignArray = this.parser.parse(result).getAsJsonArray();
        for ( JsonElement ele : alignArray ) {
            JsonObject alignObj = ele.getAsJsonObject();
            String name = alignObj.get("name").getAsJsonObject().get("localName").getAsString();
            if ( name.equals("ferret")) {
                if ( alignObj.has("editDistance") ) {
                    String editDistance = alignObj.get("editDistance").getAsString();
                    int dist = Integer.parseInt(editDistance);
                    if( dist != 5 ) {
                        throw new IOException("Incorrect edit distance in returned alignment");
                    } else {
                        break;
                    }
                } else {
                    throw new IOException("Missing edit distance in returned alignment");
                }
            }
        }
    }
    
    private void testDeleteAnnotation(Long id, Long delId) throws Exception {
        LOG.info("* Test DELETE annotation");
        Helper.delete(this.baseUrl+"/set/"+id+
            "/witness/"+this.setHelper.getWitnessId(0)+"/annotation", delId);
        String result = Helper.getJson(this.baseUrl+"/set/"+id+
            "/witness/"+this.setHelper.getWitnessId(0)+"/annotation?content=1");
        JsonArray jsonArray = this.parser.parse(result).getAsJsonArray();
        if ( jsonArray.size() != 3 ) {
            throw new Exception("Got bad token count back");
        } 
    }
    
    private void testGetAnnotationsByProfile(Long id) throws Exception {
        LOG.info("* Test GET annotations by PROFILE");
        String result = Helper.getJson(this.baseUrl+"/set/"+id+
            "/witness/"+this.setHelper.getWitnessId(0)+"/annotation?profile="+
            this.profileHelper.getName());
        JsonArray jsonArray = this.parser.parse(result).getAsJsonArray();
        if ( jsonArray.size() != 4 ) {
            throw new Exception("Got bad token count back");
        }
        this.annotationId = jsonArray.get(0).getAsJsonObject().get("id").getAsLong();
    }

    private void testGetAnnotations( Long id) throws Exception {
        LOG.info("* Test GET annotations");
        String result = Helper.getJson(this.baseUrl+"/set/"+id+
            "/witness/"+this.setHelper.getWitnessId(0)+"/annotation?content=1");
        JsonArray jsonArray = this.parser.parse(result).getAsJsonArray();
        if ( jsonArray.size() != 4 ) {
            throw new Exception("Got bad token count back");
        }
        this.annotationId = jsonArray.get(0).getAsJsonObject().get("id").getAsLong();
    }
    
    private void testGetAnnotation( Long id, Long getId) throws Exception {
        LOG.info("* Test GET annotation by ID");
        String result = Helper.getJson(this.baseUrl+"/set/"+id+
            "/witness/"+this.setHelper.getWitnessId(0)+"/annotation/"+getId+"?content=1");
        JsonObject obj = this.parser.parse(result).getAsJsonObject();
        int test = obj.get("id").getAsInt();
        String content = obj.get("content").getAsString();
        if ( test != getId ) {
            throw new Exception("bad id returned");
        }
        if ( content.equals("The") == false) {
            throw new Exception("bad content returned");
        }
    }

    private void testTokenize(Long id) throws Exception {
        LOG.info("* Test tokenize");
        Helper.post(this.baseUrl+"/set/"+id+"/tokenize");
        
        LOG.info("* Get status until done");
        while ( true ) {
            String result = Helper.getJson(this.baseUrl+"/set/"+id+"/tokenizer/status");
            if ( result.contains("COMPLETE") ) {
                break;
            } else if ( result.contains("FAILED") ) {
                throw new Exception("Tokenize failed");
            }
            Thread.sleep(500);
        }
    }

    private void testUpdate(Long id) throws Exception {
        LOG.info("* Update comparison set");
        String result = Helper.getJson(this.baseUrl+"/set", id);
        
        JsonObject jsonObj = parser.parse(result).getAsJsonObject();
        JsonElement witnesses = parser.parse("[ " + this.setHelper.getWitnessId(0) +
            ","+this.setHelper.getWitnessId(2) + " ]");
        jsonObj.remove("witnesses");
        jsonObj.add("witnesses", witnesses);
        String jsonStr = jsonObj.toString();
        Helper.put(this.baseUrl+"/set", id, jsonStr );    
        
        LOG.info("* Validate updated comparison set");
        result = Helper.getJson(this.baseUrl+"/set", id);
        jsonObj = parser.parse(result).getAsJsonObject();
        JsonArray witnessArray = jsonObj.get("witnesses").getAsJsonArray();
        if ( witnessArray.size() != 2 ) {
            throw new Exception("Invalid witness count");
        }
        for ( Iterator<JsonElement> itr = witnessArray.iterator(); itr.hasNext(); ) {
            JsonObject obj = itr.next().getAsJsonObject();
            Long tstId = obj.get("id").getAsLong();
            if ( tstId ==this.setHelper.getWitnessId(1)) {
                throw new Exception("Invalid witness in set");
            }
        }
    }

    private void testGet(Long id) throws Exception {
        LOG.info("* Get comparison set");
        String result = Helper.getJson(this.baseUrl+"/set", id);
        JsonObject jsonObj = parser.parse(result).getAsJsonObject();
        String name = jsonObj.get("name").getAsString();
        if ( name.equals( this.setName ) == false) {
            throw new Exception("bad name returned");
        }
        JsonArray witnesses = jsonObj.get("witnesses").getAsJsonArray();
        if ( witnesses.size() != 2) {
            throw new Exception("Wrong numer of member witnesses");
        }
    }
  
    private void testGetAll(Long id) throws Exception {
        LOG.info("* Get all comparison sets");
        String result = Helper.getJson(this.baseUrl+"/set");
        JsonArray jsonArray = parser.parse(result).getAsJsonArray();
        boolean found = false;
        for ( Iterator<JsonElement> itr = jsonArray.iterator(); itr.hasNext(); ) {
            JsonObject obj = itr.next().getAsJsonObject();
            Long tstId = obj.get("id").getAsLong();
            if ( id == tstId ) {
                found = true;
                break;
            }
         }
        
        if ( found == false ) {
            throw new Exception("New comparison set missing from results");
        }      
    }

    private Long createSet() throws Exception {
        LOG.info("* Create comparison set");
        String json = "{name: '"+this.setName+"', witnesses: [" +  this.setHelper.getWitnessId(0) + 
            "," + this.setHelper.getWitnessId(1)+ "], baseWitnessId: " +  this.setHelper.getWitnessId(0)+ "}";
        Long id = Helper.post(this.baseUrl+"/set", json);
        try {
            LOG.info("* Create DUPLICATE comparison set");
            Helper.post(this.baseUrl+"/set", json);
        } catch (Exception e) {
            LOG.info("   => Got expected exception on duplicate");
        }
        return id;
    }
    
    private void deleteSet(Long id) throws Exception {
        LOG.info("* Delete comparison set");
        Helper.delete(this.baseUrl+"/set", id );
    }
}
