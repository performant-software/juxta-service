package org.juxtasoftware;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

public class CollateXmlTest {
    private final String baseUrl;
    private final String srcUrl;
    private final String templateUrl;
    private final String transformUrl;
    private JsonParser parser = new JsonParser();
    private List<Long> templateIds = new ArrayList<Long>();
    private List<Long> sourceIds = new ArrayList<Long>();
    private List<Long> witnessIds = new ArrayList<Long>();
    private Long setId;
    private static final Logger LOG = Logger.getRootLogger();
    private static final String[] large_docs = {"data/doc.xml", "data/doc2.xml"};
    private static final String[] damozel_docs = {
        "data/1-1847.morgms.rad.xml", "data/1-1870.2ndedn.prin.rad.xml", 
        "data/1-1870.1pr.trox.rad.xml", "data/1-1881.1stedn.rad.xml",
        "data/1-1870.1stedn.rad.xml", "data/ap4.g415.1.rad.xml"};
    private static final String[] note_docs = {
        "data/brn425.xml", "data/d1723.xml"
    };
    private static final String[] simple_note_docs = {
        "data/note1.xml", "data/note2.xml"
    };
    private static final String[] whitman_docs = {
        "data/whitman_works.xml", "data/whitman_facs.xml", "data/whitman_frag.xml"
    };
    private static final String[] welcome_docs = {
        "data/welcome1.xml", "data/welcome2.xml"
    };
    private static final String[] evil_docs = {
        "data/danteCircle.txt", "data/earlyItalianPoets.txt"
    };
    private String[] test_docs;
    private int statusCheckInterval;
    private String setName;
    private boolean addTemplates;

    public CollateXmlTest(String url, boolean large) {
        this.baseUrl = url;
        this.templateUrl = url + "/template";
        this.srcUrl = url + "/source";
        this.transformUrl = url + "/transform";
        
        if ( large ) {
            setName = "large";
            test_docs = large_docs;
            statusCheckInterval = 15*1000;
            addTemplates = false;
        } else {
            
            statusCheckInterval = 1000;
        }
    }
    
    @SuppressWarnings("unused")
    private void initWelcome(boolean profiles) {
        setName = "welcome";
        test_docs = welcome_docs;
        addTemplates  = profiles;
        this.sourceIds.clear();
        this.witnessIds.clear();
        this.setId = null;
    }
    private void initDamozel(boolean profiles) {
        setName = "damozel";
        test_docs = damozel_docs;
        addTemplates  = profiles;
    }
    @SuppressWarnings("unused")
    private void initWhitman(boolean profiles) {
        setName = "whitman";
        test_docs = whitman_docs;
        addTemplates  = profiles;
    }
    @SuppressWarnings("unused")
    private void initCcl(boolean profiles) {
        setName = "ccl";
        test_docs = note_docs;
        addTemplates  = profiles;
    }
    @SuppressWarnings("unused")
    private void initSimpleNotes(boolean profiles) {
        setName = "Simple Notes";
        test_docs = simple_note_docs;
        addTemplates  = profiles;
    }
    @SuppressWarnings("unused")
    private void initDante() {
        setName = "Dante";
        test_docs = evil_docs;
        addTemplates  = false;
        statusCheckInterval = 15*1000;
    }
    
    public void runTests() throws Exception {
        LOG.info("Testing Collation with larger XML docs");
        try { 

//            initSimpleNotes(true);
//            setup("text/xml");   
//            collate();
            
//            initWelcome(true);
//            setup("text/xml");   
//            collate();
            
//            initDante();
//            setup("text/plain");   
//            collate();
            
            initDamozel(true);
            setup("text/xml");   
            collate();
            
//            initWhitman(false);
//            setup("text/xml");   
//            collate();
            
//            initCcl(false);
//            setup("text/xml");   
//            collate();
            
            LOG.info("SUCCESS: Testing XML Collate");
        } catch (Exception e) {
            LOG.error("FAILED: Testing XML Collate",e);
        } finally {
            cleanup();
        }
    }

    private void collate() throws Exception {
        LOG.info("* Tokenize");
        Helper.post(this.baseUrl+"/set/"+this.setId+"/tokenize");
        while ( true ) {
            String result = Helper.getJson(this.baseUrl+"/set/"+this.setId+"/tokenizer/status");
            if ( result.contains("COMPLETE") ) {
                break;
            } else if ( result.contains("FAILED") ) {
                throw new Exception("Tokenize failed");
            }
            Thread.sleep(5000);
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
            Thread.sleep(statusCheckInterval);
        }
    }
                
    private void setup( final String contentType ) throws Exception{
        LOG.info("* Adding source documents");
        for ( int i=0; i<test_docs.length; i++) {
            
            File srcFile = new File( test_docs[i] );
            String name = test_docs[i].substring(5);
            
            PostMethod post = new PostMethod(this.baseUrl+"/source");
            Part[] parts = {
                new StringPart("sourceName", name),
                new StringPart("contentType", contentType),
                new FilePart("sourceFile", srcFile)
            };
            post.setRequestEntity(
                new MultipartRequestEntity(parts, post.getParams())
                );

            JuxtaServiceTest.execRequest(post);
            String response = JuxtaServiceTest.getResponseString(post);
            response = response.replace("[", "").replace("]", "");
            long id = Long.parseLong(response);
            this.sourceIds.add(i, id );
        }
        
        if ( this.addTemplates ) {
            LOG.info("* Upload parsing templates");
            String xml = IOUtils.toString( new FileInputStream(new File("data/templates.xml")), "UTF-8");
            String out =  Helper.postXml(this.templateUrl, xml);
            JsonArray array = this.parser.parse(out).getAsJsonArray();
            for ( int i=0; i<array.size(); i++) {
                this.templateIds.add(i, array.get(i).getAsLong());
            }
        }
        
        LOG.info("* Transform sources (using defaults)");
        for ( int i=0; i<sourceIds.size(); i++) {
            String json = "{source: "+sourceIds.get(i)+"}";
            this.witnessIds.add(i, Helper.post(this.transformUrl, json));
        }
        
        LOG.info("* Create set");
        String json = "{name: \"" + setName +"\", witnesses: [";
        for ( int i=0; i<witnessIds.size(); i++) {
            if ( i>0) {
                json+=",";
            }
            json+=this.witnessIds.get(i);
        }
        json += "] }";
        this.setId = Helper.post(this.baseUrl+"/set", json);
    }

    private void cleanup() throws Exception {
        LOG.info("* Delete it all");
        Helper.delete(this.baseUrl+"/set", this.setId);
        for ( int i=0; i<witnessIds.size(); i++) {
            Helper.delete(this.baseUrl+"/witness", this.witnessIds.get(i));
        }
        for ( int i=0; i<sourceIds.size(); i++) {
            Helper.delete(this.srcUrl, this.sourceIds.get(i));
        }
        for ( int i=0; i<this.templateIds.size(); i++) {
            Helper.delete(this.templateUrl, this.templateIds.get(i));
        }
    }
}
