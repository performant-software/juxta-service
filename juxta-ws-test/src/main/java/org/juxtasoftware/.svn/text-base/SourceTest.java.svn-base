package org.juxtasoftware;

import java.io.File;
import java.io.FileWriter;
import java.util.Iterator;
import java.util.UUID;

import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class SourceTest {
    private final String baseUrl;
    private static final Logger LOG = Logger.getRootLogger();
    
    public SourceTest(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public void runTests() {
        try {
            LOG.info("TESTING: Source API");
            postBadXmlSource();
            Long xmlSrcId = postXmlSource( );
            Long plainSrcId = postTextSource( );
            getSources( xmlSrcId, plainSrcId );
            getSource( xmlSrcId );
            getSource( plainSrcId );
            deleteSource( xmlSrcId );
            deleteSource( plainSrcId );
            postRaw();
            postUrls();
            LOG.info("SUCCESS: Source API");
        } catch (Exception e ) {
            LOG.error("FAILED: Source API", e);
        }
    }

    private void deleteSource(Long sourceID) throws Exception  {
        LOG.info("* Delete source");
        Helper.delete(this.baseUrl+"/source", sourceID);
    }

    private void getSource(Long sourceID) throws Exception {
        LOG.info("* Get source");
        String response = Helper.getJson(this.baseUrl+"/source", sourceID);
        if ( response == null || response.length() == 0 ) {
            throw new Exception("Unable to get source");
        }
    }

    private void getSources(Long src1, Long src2) throws Exception {
        LOG.info("* Get all sources");
        String response = Helper.getJson(this.baseUrl+"/source");
        
        // parse it and look for ID in results
        JsonParser parser = new JsonParser();
        JsonArray jsonArray = parser.parse ( response ).getAsJsonArray();
        boolean found1 = false;
        boolean found2 = false;
        for ( Iterator<JsonElement> itr = jsonArray.iterator(); itr.hasNext(); ) {
            JsonObject obj = itr.next().getAsJsonObject();
            Long id = obj.get("id").getAsLong();
            if ( src1.equals(id) ) {
                found1 = true;
            }
            if ( src2.equals(id) ) {
                found2 = true;
            }
        }
        if ( found1 == false || found2 == false) {
            throw new Exception("Expected source ID not found in response");
        }
    }
    

    private void postUrls() throws Exception {
        LOG.info("* Create sources from URL list");
        final String json = 
            "[ {\"type\": \"url\", \"name\": \"broadway\", \"data\": \"http://www.whitmanarchive.org/plaintext/broadway.txt\"}, " +
            "  {\"type\": \"url\", \"name\": \"american\", \"data\": \"http://www.whitmanarchive.org/plaintext/american.txt\"} ]";
        PostMethod post = new PostMethod(this.baseUrl+"/source");
        post.setRequestEntity(new StringRequestEntity(json, "application/json", "utf-8"));
        JuxtaServiceTest.execRequest(post);
        String response = JuxtaServiceTest.getResponseString(post);
        response = response.substring(1, response.lastIndexOf(']'));
        String[] bits = response.split(",");
        for ( int i=0;  i < bits.length; i++ ) {
            Long id = Long.parseLong(bits[i]);
            deleteSource(id);
        }
        if ( bits.length != 2) {
            throw new Exception("Wrong number of sources created");
        }
    }
    
    private void postRaw() throws Exception {
        LOG.info("* Create sources from URL list");
        final String json = 
            "[ {\"type\": \"txt\", \"name\": \"broadway\", \"data\": \"Hello there\"}, " +
            "  {\"type\": \"xml\", \"name\": \"american\", \"data\": \"<x>DATA</x>\"} ]";
        PostMethod post = new PostMethod(this.baseUrl+"/source");
        post.setRequestEntity(new StringRequestEntity(json, "application/json", "utf-8"));
        JuxtaServiceTest.execRequest(post);
        String response = JuxtaServiceTest.getResponseString(post);
        response = response.substring(1, response.lastIndexOf(']'));
        String[] bits = response.split(",");
        for ( int i=0;  i < bits.length; i++ ) {
            Long id = Long.parseLong(bits[i]);
            deleteSource(id);
        }
        if ( bits.length != 2) {
            throw new Exception("Wrong number of sources created");
        }
    }

    private Long postXmlSource() throws Exception {
        LOG.info("* Create new source");
        
        String name = "fake-source-"+UUID.randomUUID().toString();
        String content = "<fake>content</fake>";
        File tmp = File.createTempFile("fake-source",".xml");
        FileWriter fw = new FileWriter(tmp);
        fw.write(content);
        fw.flush();
        fw.close();
        tmp.deleteOnExit();
        
        PostMethod post = new PostMethod(this.baseUrl+"/source");
        Part[] parts = {
                        new StringPart("sourceName", name),
                        new StringPart("contentType", "text/xml"),
                        new FilePart("sourceFile", tmp)
                    };
        post.setRequestEntity(
            new MultipartRequestEntity(parts, post.getParams())
            );
        
        
        JuxtaServiceTest.execRequest(post);
        String response = JuxtaServiceTest.getResponseString(post);
        Long id =  Long.parseLong(response.substring(1,response.lastIndexOf(']')));
        
        LOG.info("* Attempt to create DUPLICATE source");
        JuxtaServiceTest.execRequest(post, false);  // dont throw
        response = JuxtaServiceTest.getResponseString(post);
        if ( response.toLowerCase().contains("conflict") == false ) {
            throw new Exception("Allowed creation of source with duplicate name");
        }

        tmp.delete();
        return id;
    }
    
    private void postBadXmlSource() throws Exception {
        LOG.info("* Create INVALID source");
        String name = "fake-source-"+UUID.randomUUID().toString();
        String json = "{'sourceName':'"+name+"','contentType': 'text/xml'}";
        String content = "<fake>content</broken>";
        File tmp = File.createTempFile("fake-source",".xml");
        FileWriter fw = new FileWriter(tmp);
        fw.write(content);
        fw.flush();
        fw.close();
        tmp.deleteOnExit();
        
        PostMethod post = new PostMethod(this.baseUrl+"/source");
        Part[] parts = {
                        new StringPart("jsonHeader", json),
                        new FilePart("sourceFile", tmp)
                    };
        post.setRequestEntity(
            new MultipartRequestEntity(parts, post.getParams())
            );
        
        
        JuxtaServiceTest.execRequest(post, false);
        String resp = JuxtaServiceTest.getResponseString(post);
        tmp.delete();
        if ( resp.contains("malformed") == false) {
            throw new Exception("Accepted malformed XML");
        }
    }
    
    private Long postTextSource() throws Exception {
        LOG.info("* Create new source");
        String name = "fake-source-"+UUID.randomUUID().toString();
        String json = "{'sourceName':'"+name+"','contentType': 'text/plain'}";
        String content = "This is plain text content";
        File tmp = File.createTempFile("fake-source",".txt");
        FileWriter fw = new FileWriter(tmp);
        fw.write(content);
        fw.close();
        tmp.deleteOnExit();
        
        PostMethod post = new PostMethod(this.baseUrl+"/source");
        Part[] parts = {
                        new StringPart("jsonHeader", json),
                        new FilePart("sourceFile", tmp)
                    };
        post.setRequestEntity(
            new MultipartRequestEntity(parts, post.getParams())
            );
        
        
        JuxtaServiceTest.execRequest(post);
        String response = JuxtaServiceTest.getResponseString(post);
        tmp.delete();
        return Long.parseLong(response.substring(1,response.lastIndexOf(']')));
    }
}
