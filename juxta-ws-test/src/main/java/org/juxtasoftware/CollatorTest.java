package org.juxtasoftware;

import java.util.Iterator;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class CollatorTest {
    private final String baseUrl;
    private SetHelper setHelper;
    private FilterHelper filterHelper;
    private Long alignId;
    private JsonParser parser = new JsonParser();
    private String setName = "collate-set-"+UUID.randomUUID().hashCode();
    private static final Logger LOG = Logger.getRootLogger();
    
    public CollatorTest(String url) {
        this.baseUrl = url;
        this.setHelper = new SetHelper(url, SetHelper.basic);
        this.filterHelper = new FilterHelper(url);
    }

    public void runTests() throws Exception {
        LOG.info("Testing Collation API");
        Long setId = null;
        Long filterId = null;
        try { 
            this.setHelper.setupSourceAndWitness();
            filterId = this.filterHelper.createTokenFilter();
            setId = createSet();
            testGetConfig( setId );
            testCollate( setId ); 
            
            testGetAlignments( setId );
            testGetAlignment( setId );
            testTranspose( setId );
            
            testUpdateConfig( setId );
            
            LOG.info("SUCCESS: Testing Collation API");
        } catch (Exception e) {
            LOG.error("FAILED: Testing Collation API",e);
        } finally {
            if ( setId != null ) {
                deleteSet(setId);
            }
            if ( filterId != null ) {
                this.filterHelper.delete(filterId);
            }
            this.setHelper.cleanupSourceAndWitness();
        }
    }
    
    private void testTranspose(Long setId) throws Exception {
        LOG.info("* Test TRANSPOSE - setup");
        String result = Helper.getJson(this.baseUrl+"/set/"+setId+
            "/witness/"+this.setHelper.getWitnessId(0)+"/annotation?profile="+
            this.filterHelper.getName()+"&content=yes");
        JsonArray jsonArray = this.parser.parse(result).getAsJsonArray();
        Long transposeIdOne = jsonArray.get(0).getAsJsonObject().get("id").getAsLong();
        String strOne = jsonArray.get(0).getAsJsonObject().get("content").getAsString();
        
        result = Helper.getJson(this.baseUrl+"/set/"+setId+
            "/witness/"+this.setHelper.getWitnessId(1)+"/annotation?profile="+
            this.filterHelper.getName()+"&content=yes");
        jsonArray = this.parser.parse(result).getAsJsonArray();
        Long transposeIdTwo = jsonArray.get( jsonArray.size()-1 ).getAsJsonObject().get("id").getAsLong();
        String strTwo = jsonArray.get( jsonArray.size()-1 ).getAsJsonObject().get("content").getAsString();
        
        LOG.info("* Test TRANSPOSE - transpose "+transposeIdOne+" - "+strOne
            +" with "+transposeIdTwo+" - "+strTwo);
        String json = "[{name: {namespace: \"http://juxtasoftware.org/ns\", localName: \"transposition\"}, "+ 
            "annotations: ["+
            "  "+transposeIdOne+", "+transposeIdTwo+" ]}]";
        result = Helper.jsonResponsePost(this.baseUrl+"/set/"+setId+"/alignment", json);
        int cnt = Integer.parseInt(result);
        if ( cnt != 1 ) {
            throw new Exception("Create transpose failed");
        }
   
        LOG.info("* Test TRANSPOSE - validate create");
        result = Helper.getJson(this.baseUrl+"/set/"+setId+"/alignment?filter=transpositions");
        JsonArray array = this.parser.parse(result).getAsJsonArray();
        if ( array.size() != 1) {
            throw new Exception("Bad number of transpose alignments found");
        }
        Long id = null;
        for ( JsonElement ele : array) {
            JsonObject obj = ele.getAsJsonObject();
            id = obj.get("id").getAsLong(); 
        }
        
        LOG.info("* Test TRANSPOSE - delete");
        Helper.delete(this.baseUrl+"/set/"+setId+"/alignment", id);
        
        LOG.info("* Test TRANSPOSE - validate delete");
        result = Helper.getJson(this.baseUrl+"/set/"+setId+"/alignment");
        jsonArray = this.parser.parse(result).getAsJsonArray();
        for ( Iterator<JsonElement> itr = jsonArray.iterator(); itr.hasNext(); ) {
            JsonObject o = itr.next().getAsJsonObject();
            if ( o.get("id").getAsLong() == id) {
                throw new Exception("transpose not deleted");
            }
        }
    }

    private void testGetAlignment(Long setId) throws Exception {
        LOG.info("* GET alignment");
        String result = Helper.getJson(this.baseUrl+"/set/"+setId+"/alignment", this.alignId);
        JsonObject obj = this.parser.parse(result).getAsJsonObject();
        Long id = obj.get("id").getAsLong();        
        if ( this.alignId.equals(id) == false) {
            throw new Exception("Got bad ID in alignment response");
        }
    }
    
    private void testGetAlignments(Long setId) throws Exception {
        LOG.info("* GET alignments");
        String result = Helper.getJson(this.baseUrl+"/set/"+setId+"/alignment");
        JsonArray arr = this.parser.parse(result).getAsJsonArray();
        if ( arr.size() != 1) {
            throw new Exception("Got wrong number of alignments");
        }
        this.alignId = arr.get(0).getAsJsonObject().get("id").getAsLong();
    }

    private void testCollate(Long id) throws Exception {
        LOG.info("* Tokenize");
        Helper.post(this.baseUrl+"/set/"+id+"/tokenize");
        while ( true ) {
            String result = Helper.getJson(this.baseUrl+"/set/"+id+"/tokenizer/status");
            if ( result.contains("COMPLETE") ) {
                break;
            } else if ( result.contains("FAILED") ) {
                throw new Exception("Tokenize failed");
            }
            Thread.sleep(500);
        }
        
        LOG.info("* Begin collate");
        Helper.post(this.baseUrl+"/set/"+id+"/collate");
        
        LOG.info("* Get status until done");
        while ( true ) {
            String result = Helper.getJson(this.baseUrl+"/set/"+id+"/collator/status");
            if ( result.contains("COMPLETE") ) {
                break;
            } else if ( result.contains("FAILED") ) {
                throw new Exception("Tokenize failed");
            }
            Thread.sleep(500);
        }
    }
    
    private void testGetConfig(Long id) throws Exception {
        LOG.info("* Get comparison set collator config");
        String result = Helper.getJson(this.baseUrl+"/set/"+id+"/collator");
        JsonObject jsonObj = parser.parse(result).getAsJsonObject();
        final String[] names = {"filterWhitespace", "filterPunctuation", "filterCase"};
        for ( int i=0; i<names.length;i++) {
            if ( jsonObj.get( names[i] ).getAsBoolean() == false ) {
                throw new Exception("Incorrect/Missing setting");
            }
        }
    }
    
    private void testUpdateConfig(Long id) throws Exception {
        LOG.info("* Update comparison set collator config");
        String json = "{filterWhitespace: 'no', filterPunctuation: 'no', filterCase: 'no'}";
        Helper.post(this.baseUrl+"/set/"+id+"/collator", json);
        
        LOG.info("* Validate updated comparison set collator config");
        String result = Helper.getJson(this.baseUrl+"/set/"+id+"/collator");
        JsonObject jsonObj = parser.parse(result).getAsJsonObject();
        final String[] names = {"filterWhitespace", "filterPunctuation", "filterCase"};
        for ( int i=0; i<names.length;i++) {
            if ( jsonObj.get( names[i] ).getAsBoolean() == true ) {
                throw new Exception("Incorrect/missing settngs");
            }
        } 
    }   
    
    private Long createSet() throws Exception {
        LOG.info("* Create comparison set for collation");
        String json = "{name: '"+this.setName+"', witnesses: [" +  
            this.setHelper.getWitnessId(0) + "," + 
            this.setHelper.getWitnessId(1) + "], " + 
            "baseWitnessId: " +  this.setHelper.getWitnessId(0)+ "}";
        Long setId = Helper.post(this.baseUrl+"/set", json);
        
        
        LOG.info("* ADD AN ANNOTATION");
        long wId = this.setHelper.getWitnessId(0);
        String j2 = "[ {\"witnessId\":"+wId+",\"name\":{\"namespace\":\"http://juxtasoftware.org/ns\",\"localName\":\"token\"},\"range\":{\"start\":0,\"end\":3}}]";
        Long annoId = Helper.post(this.baseUrl+"/set/"+setId+"/witness/"+wId+"/annotation", j2);
        System.out.println(annoId);
        
        return setId;
    }
    
    private void deleteSet(Long id) throws Exception {
        LOG.info("* Delete collation comparison set");
        Helper.delete(this.baseUrl+"/set", id );
    }
}
