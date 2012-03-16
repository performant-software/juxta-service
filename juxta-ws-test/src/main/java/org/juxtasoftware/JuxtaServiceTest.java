package org.juxtasoftware;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.zip.GZIPInputStream;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

/**
 * Standalone tester client that will exercise the juxtaWS restful API
 * 
 * @author loufoster
 *
 */
public class JuxtaServiceTest {
    private final String baseUrl;
    private static final Logger LOG = Logger.getRootLogger();
    private static final int REQUEST_TIMEOUT = 2 * 60 * 1000;   // 2 secs
    
    /**
     * Start up the test. Expects to have 1 arg that is the base service URL
     * 
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        if ( args.length < 1) {
            LOG.error("Missing required base URL");
            System.exit(0);
        }
        
        String tests = "all";
        if ( args.length == 2) {
            tests = args[1];
        }
        
        JuxtaServiceTest tester = new JuxtaServiceTest(args[0]);
        tester.runTests( tests );
    }
    
    /**
     * Construct the text and exec test calls
     * @param url
     */
    public JuxtaServiceTest(final String url ) {
        this.baseUrl = url;
    }
    
    public void runTests( final String tests) {
        LOG.info("==> START RESTful API test at " + this.baseUrl +" <==");
        try {
            if ( tests.equals("all") || tests.equals("template") ) {
                new TemplateTest( this.baseUrl ).runTests();
            }
            if ( tests.equals("all") || tests.equals("source") ) {
                new SourceTest( this.baseUrl ).runTests();
            }
            if ( tests.equals("all") || tests.equals("transform") ) {
                new TransformTest( this.baseUrl ).runTests();
            }
            if ( tests.equals("all") || tests.equals("set") ) {
                new ComparisonSetTest( this.baseUrl ).runTests();
            }
            if ( tests.equals("all") || tests.equals("collator") ) {
                new CollatorTest( this.baseUrl ).runTests();
                new CollateFilterTest( this.baseUrl ).runTests();
                new CollateXmlTest( this.baseUrl, false ).runTests();
            }
            if ( tests.equals("big_collate") ) {
                new CollateXmlTest( this.baseUrl, true ).runTests();
            }
            if ( tests.equals("all") || tests.equals("filter") ) {
                new QNameFilterTest( this.baseUrl ).runTests();
            }
            if ( tests.equals("all") || tests.equals("import") ) {
                new PsImportTest( this.baseUrl ).runTests();
                new ImportTest( this.baseUrl ).runTests();
            }
            LOG.info("==> RESTful API testing complete <==");
        } catch (Exception e) {
            LOG.info("==> FAIL: RESTful API testing caught exception", e);
        }
    }
    
    public final static void execRequest( HttpMethod request ) throws IOException {
        execRequest(request, true);
    }
    public final static void execRequest( HttpMethod request, boolean throwOnError ) throws IOException {
        
        HttpClient httpClient = newHttpClient();
        request.setDoAuthentication( true );
        int responseCode = httpClient.executeMethod(request);
        if (responseCode != 200 && throwOnError) {
            String msg = getResponseString(request);
            if ( msg == null || msg.length() == 0) {
                msg = request.getStatusText();
            }
            throw new IOException("ERROR "+responseCode+" "+getResponseString(request));
        }
    }
    
    public static final String getResponseString(HttpMethod httpMethod) throws IOException {
        boolean zipped = false;
        for (Header h : httpMethod.getResponseHeaders()) {
           if ( h.getValue().equals("gzip")) {
               zipped = true;
               break;
           }
        }
        
        if ( zipped ) {
            return getZippedResponseString(httpMethod);
        }
        InputStream is = httpMethod.getResponseBodyAsStream();
        String resp = IOUtils.toString(is, "UTF-8");
        if ( resp == null || resp.length() == 0 ) {
            resp = httpMethod.getStatusText();
        }
        return resp;
    }
    
    private static final String getZippedResponseString(HttpMethod httpMethod) throws IOException {
        StringWriter responseBody = new StringWriter();
        PrintWriter responseWriter = new PrintWriter(responseBody);
        InputStream is = httpMethod.getResponseBodyAsStream();
        byte[] bytes = IOUtils.toByteArray(is);
        if (bytes != null && bytes.length > 0) {
            GZIPInputStream zippedInputStream = new GZIPInputStream( new ByteArrayInputStream(bytes));
            BufferedReader r = new BufferedReader(new InputStreamReader(zippedInputStream));
            String line = null;
            while ((line = r.readLine()) != null) {
                responseWriter.println(line);
            }
            return responseBody.toString().trim();
        }
        return "";
    }

    public static final HttpClient newHttpClient() {
        HttpClient httpClient = new HttpClient();
        Credentials credentials = new UsernamePasswordCredentials("juxta", "juxta!@l0g1n!");
        httpClient.getState().setCredentials(AuthScope.ANY, credentials);
        httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(
            REQUEST_TIMEOUT);
        httpClient.getHttpConnectionManager().getParams().setIntParameter(
            HttpMethodParams.BUFFER_WARN_TRIGGER_LIMIT, 10000 * 1024); 
        return httpClient;
    }
}