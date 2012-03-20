package org.juxtasoftware;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.log4j.Logger;

public class PsImportTest {
    private final String baseUrl;

    private static final Logger LOG = Logger.getRootLogger();
    
    public PsImportTest(String url) {
        this.baseUrl = url;
    }

    public void runTests() throws Exception {
        LOG.info("Testing PS-Import API");
        Long setId = null;
        Long srcId = null;
        try { 
 
            // create a source from the TEI ps file
            srcId = createTeiPsSource();
            
            // use this sourceID to import witnesses and assemble them
            // into a comparison set
            String name = "import-test-set-"+UUID.randomUUID().hashCode();
            PostMethod post = new PostMethod(this.baseUrl+"/import?overwrite");
            String json = "{\"setName\":"+"\""+name+"\", \"teiSourceId\": "+srcId+"}";
            post.setRequestEntity(new StringRequestEntity(json, "application/json", "utf-8"));
            JuxtaServiceTest.execRequest(post);
            String response = JuxtaServiceTest.getResponseString(post);
            long id = Long.parseLong(response);
            
            while ( true ) {
                String result = Helper.getJson(this.baseUrl+"/import/"+id+"/status");
                if ( result.contains("COMPLETE") ) {
                    break;
                } else if ( result.contains("FAILED") ) {
                    throw new Exception("IMPORT failed");
                }
                Thread.sleep(5000);
            }
            
            LOG.info("SUCCESS: Testing Import API");
        } catch (Exception e) {
            LOG.error("FAILED: Testing Import API",e);
        } 
        finally {
            if ( setId == null ) {
                
            }
        }
    }
    
    private Long createTeiPsSource() throws IOException {
        String name = "tei-ps-src-"+UUID.randomUUID().toString();

        File psFile = new File("data/autumn.xml");
        PostMethod post = new PostMethod(this.baseUrl+"/source");
        Part[] parts = {
                        new StringPart("sourceName", name),
                        new StringPart("contentType", "text/xml"),
                        new FilePart("sourceFile", psFile)
                    };
        post.setRequestEntity(
            new MultipartRequestEntity(parts, post.getParams())
            );
        
        
        JuxtaServiceTest.execRequest(post);
        String response = JuxtaServiceTest.getResponseString(post);
        return Long.parseLong(response.substring(1,response.lastIndexOf(']')));
    }
}
