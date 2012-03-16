package org.juxtasoftware;

import java.util.UUID;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

public class CollateFilterTest {
    private final String baseUrl;
    private FilterHelper filterHelper;
    private Long setId;
    private Long filterId;
    private String filterName;
    private JsonParser parser = new JsonParser();
    private String setName = "collate-set-"+UUID.randomUUID().hashCode();
    private static final Logger LOG = Logger.getRootLogger();
    
    public CollateFilterTest(String url) {
        this.baseUrl = url;
        this.filterHelper = new FilterHelper(url);
    }

    public void runTests() throws Exception {
        LOG.info("Testing Collate Filter API");
        try { 
            this.filterId = this.filterHelper.createHeatmapFilter();
            this.filterName = this.filterHelper.getName();
    
            testSame();
            
            LOG.info("SUCCESS: Testing Collation API");
        } catch (Exception e) {
            LOG.error("FAILED: Testing Collation API",e);
            this.filterHelper.delete(this.filterId);
        } 
    }
    
    private void testSame() {
        SetHelper helper = new SetHelper(this.baseUrl, SetHelper.damozel_same );
        try {
            
            helper.setupSourceAndWitness();
            createSet( helper );
            doCollation();
            
            LOG.info("* GET differences");
            String result = Helper.getJson(this.baseUrl+"/set/"+setId+"/alignment?filter="+this.filterName);
            JsonArray arr = this.parser.parse(result).getAsJsonArray();
            if ( arr.size() != 0) {
                LOG.error("FAILED: Found difference when there should be NONE");
            }
            
            deleteSet();
        } catch (Exception e ) {
            try{
            helper.cleanupSourceAndWitness();
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
    }
    
    private void createSet(SetHelper helper) throws Exception {
        LOG.info("* Create comparison set for collation");
        String json = "{name: '"+this.setName+"', witnesses: [" +  
            helper.getWitnessString() + "], " +
            "baseWitnessId: " +  helper.getWitnessId(0)+ "}";
        this.setId =  Helper.post(this.baseUrl+"/set", json);
    }
    
    private void doCollation() throws Exception {
        LOG.info("* Tokenize");
        Helper.post(this.baseUrl+"/set/"+this.setId+"/tokenize");
        while ( true ) {
            String result = Helper.getJson(this.baseUrl+"/set/"+this.setId+"/tokenizer/status");
            if ( result.contains("COMPLETE") ) {
                break;
            } else if ( result.contains("FAILED") ) {
                throw new Exception("Tokenize failed");
            }
            Thread.sleep(500);
        }
        
        LOG.info("* Begin collate");
        Helper.post(this.baseUrl+"/set/"+this.setId+"/collate");
        
        LOG.info("* Get status until done");
        while ( true ) {
            String result = Helper.getJson(this.baseUrl+"/set/"+this.setId+"/collator/status");
            if ( result.contains("COMPLETE") ) {
                break;
            } else if ( result.contains("FAILED") ) {
                throw new Exception("Collate failed");
            }
            Thread.sleep(500);
        }
    }
    
   
    private void deleteSet() throws Exception {
        LOG.info("* Delete collation comparison set");
        Helper.delete(this.baseUrl+"/set", this.setId );
    }
}
