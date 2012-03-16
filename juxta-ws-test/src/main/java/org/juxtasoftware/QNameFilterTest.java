package org.juxtasoftware;

import java.util.Iterator;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class QNameFilterTest {
    private final String baseUrl;
    private Long filterId;
    private static final Logger LOG = Logger.getRootLogger();
    
    private static final String name =  "filter"+UUID.randomUUID().hashCode();
    private static final String filterJson =
        "{'name':'"+name+"','qnames':" +
                "[{namespace: 'http://juxtasoftware.org/ns', localName: 'token'}]}";
    
    public QNameFilterTest(String url) {
        this.baseUrl = url + "/filter";
    }

    public void runTests() throws Exception {
        this.filterId = null;
        try {
            LOG.info("Testing QName Filter API");
            this.filterId = createFilter();
            createDuplicateFilter();
            getFilters( );
            getFilter( );
            updateFilter( );
            LOG.info("SUCCESS: Testing QName Filter API");
        } catch (Exception e) {
            LOG.error("FAILED: Testing QName Filter API",e);
        } finally {
            deleteFilter();
        }
    }

    private void deleteFilter() throws Exception {
        LOG.info("* Delete QName filter");
        Helper.delete(this.baseUrl, this.filterId);
    }

    private void createDuplicateFilter() throws Exception {
        LOG.info("* Create DUPLICATE QName filter");
        String resp = Helper.doPostError(this.baseUrl, filterJson);
        if ( resp.contains("already exists") == false) {
            throw new Exception("Accepted duplicate profile");
        }
    }

    private Long createFilter() throws Exception {
        LOG.info("* Create QName filter");
        return Helper.post(this.baseUrl, filterJson);
    }

    private void updateFilter() throws Exception {
        LOG.info("* Update filter");
        String resp = Helper.getJson(this.baseUrl, this.filterId);
        JsonParser parser = new JsonParser();
        JsonObject originalJson = parser.parse(resp).getAsJsonObject();
        originalJson.add("qnames", 
            parser.parse("[{namespace: 'http://juxtasoftware.org/ns', localName: 'diff'}]") );
        
        // update!
        Helper.put(this.baseUrl, this.filterId, originalJson.toString());
        
        LOG.info("* Validate update");
        String response = Helper.getJson(this.baseUrl, this.filterId);
        JsonObject obj = parser.parse(response).getAsJsonObject();
        Long id = obj.get("id").getAsLong();
        if ( this.filterId.equals(id) == false ) {
            throw new Exception("Unable to GET updated filter "+this.filterId);
        } 
        
        int actCnt = obj.get("qnames").getAsJsonArray().size();
        if ( actCnt != 1) {
            throw new Exception("Update failed to add qname");
        }
    }

    private void getFilter() throws Exception {
        LOG.info("* Get filter");
        String resp = Helper.getJson(this.baseUrl, this.filterId);
        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(resp).getAsJsonObject();
        Long id = obj.get("id").getAsLong();
        if ( this.filterId.equals(id) == false ) {
            throw new Exception("Unable to GET filter "+this.filterId);
        } 
    }

    private void getFilters() throws Exception {
        LOG.info("* Get all profiles");
        String response = Helper.getJson(this.baseUrl);
        
        // parse it and look for ID in results
        boolean success = false;
        JsonParser parser = new JsonParser();
        JsonArray jsonArray = parser.parse ( response ).getAsJsonArray();
        for ( Iterator<JsonElement> itr = jsonArray.iterator(); itr.hasNext(); ) {
            JsonObject obj = itr.next().getAsJsonObject();
            Long id = obj.get("id").getAsLong();
            if ( this.filterId.equals(id) ) {
                success = true;
                break;
            }
        }
        if ( success == false ) {
            throw new Exception("Expected filter ID not found in response");
        }
    }
}
