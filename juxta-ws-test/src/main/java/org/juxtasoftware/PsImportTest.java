package org.juxtasoftware;

import java.io.File;
import java.util.UUID;

import org.apache.commons.httpclient.methods.PostMethod;
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
        try { 
 
            String name = "import-test-set-"+UUID.randomUUID().hashCode();
            File jxtFile = new File("data/autumn.xml");

            PostMethod post = new PostMethod(this.baseUrl+"/import?overwrite");
            Part[] parts = {
                new StringPart("setName", name),
                new FilePart("teiFile", jxtFile)
            };
            post.setRequestEntity(
                new MultipartRequestEntity(parts, post.getParams())
                );

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
}
