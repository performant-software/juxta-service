package org.juxtasoftware;

import java.util.UUID;

import org.apache.log4j.Logger;

public class TransformTest {
    private final String baseUrl;
    
    final String name = "xform-src-"+UUID.randomUUID().toString()+".xml";
    final String content = 
        "<sample>" +
        "  <header>this is header content</header>" +
        "  <body>" +
        "    <p>paragraph 1</p>" +
        "    <junk>junk text</junk>" +
        "    <p>paragraph 2</p>" +
        "  </body>" +
        "</sample>";
    
    private final String txtContent = "The quick brown fox got rabies";
    
    private final String templateName = "xform-tpl-"+UUID.randomUUID().toString();
    private final String template = 
               
        "{'name':'"+this.templateName+"',"+
        " 'isDefault': 'true', " +
        " 'rootElement': { 'namespaceUri': '*', 'namespacePrefix': '*', 'localName':'sample'}, " +
        " 'tagActions': " +
                "[ { tag: {'namespaceUri': '*', 'namespacePrefix': '*', 'localName':'sample'}, 'action':'INCLUDE'}," +
                "  { tag: {'namespaceUri': '*', 'namespacePrefix': '*', 'localName':'header'}, 'action':'EXCLUDE'}," +
                "  { tag: {'namespaceUri': '*', 'namespacePrefix': '*', 'localName':'body'}, 'action':'INCLUDE'}," +
                "  { tag: {'namespaceUri': '*', 'namespacePrefix': '*', 'localName':'p'}, 'action':'INCLUDE'}," +
                "  { tag: {'namespaceUri': '*', 'namespacePrefix': '*', 'localName':'p'}, 'action':'NEW_LINE'}," +
                "  { tag: {'namespaceUri': '*', 'namespacePrefix': '*', 'localName':'junk'}, 'action':'EXCLUDE'}]}";
    
    private static final Logger LOG = Logger.getRootLogger();
    
    public TransformTest(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void runTests() {
        LOG.info("Testing Transform API");        
        try {      
            testNormalTransform();
            testNullTransform();
            LOG.info("SUCCESS: Transform API");
           
        } catch (Exception e) {
            LOG.info("FAILED: Transform API", e);
        }
    }
    
    private void testNullTransform() throws Exception {
        boolean failed = false;
        LOG.info("* Setting up NULL Transform test");
        String json = "[ {\"type\": \"txt\", \"name\": \""+name+"\", \"data\": \""+txtContent+"\"} ]";
        Long id = Helper.post(this.baseUrl+"/source", json);
        
        LOG.info("* Transform");
        String xformJson = "{source: '"+id+"', finalName: 'parsed-"+id+"'}";
        Long witnessId = Helper.post(this.baseUrl+"/transform", xformJson);
        
        LOG.info("* Validate result");
        String result = Helper.getText(this.baseUrl+"/witness", witnessId);
        if ( result.equals( this.txtContent ) == false ) {
            LOG.error("Null  transform text different from source");
            failed= true;
        }
       
        LOG.info("* Cleaning up test");
        Helper.delete(this.baseUrl+"/witness", witnessId);
        Helper.delete(this.baseUrl+"/source", id);
        
        if ( failed == true ) {
            throw new Exception("NULL Transform failed");
        }
    }

    private void testNormalTransform() throws Exception {
        boolean failed = false;
        LOG.info("* Setting up Normal Transform test");
        String json = "[ {\"type\": \"xml\", \"name\": \""+name+"\", \"data\": \""+content+"\"} ]";
        Long id = Helper.post(this.baseUrl+"/source", json);
        Long profId = Helper.post(this.baseUrl+"/template", template);
        
        LOG.info("* Transform");
        String xformJson = "{source: '"+id+"', template: '"+profId+"', finalName: 'parsed-"+id+"'}";
        Long witnessId = Helper.post(this.baseUrl+"/transform", xformJson);
        
        LOG.info("* Validate result");
        String result = Helper.getText(this.baseUrl+"/witness", witnessId);
        if ( result.contains("junk text") ) {
            LOG.error("REsult contains text that should be stripped");
            failed= true;
        }
       
        LOG.info("* Cleaning up test");
        Helper.delete(this.baseUrl+"/witness", witnessId);
        Helper.delete(this.baseUrl+"/template", profId);
        Helper.delete(this.baseUrl+"/source", id);
        
        if ( failed == true ) {
            throw new Exception("Transform failed");
        }
    }
}
