package org.juxtasoftware;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.log4j.Logger;

public class Helper {
    private static final Logger LOG = Logger.getRootLogger();

    public static void delete(String url, Long sourceID) throws Exception {
        LOG.info("  => DELETE "+url + "/"+sourceID);
        DeleteMethod del = new DeleteMethod( url + "/" + sourceID);
        JuxtaServiceTest.execRequest(del);
        del.releaseConnection();
        LOG.info("  <= Deleted");
    }
    
    public static Long post(final String url) throws Exception {
        return post(url, null);
    }
    public static Long post(final String url, final String json) throws Exception {
        String response = doPost(url, json);
        if (response != null && response.length() > 0) {
            if (response.contains("[")) {
                response = response.replace("[", "").replace("]", "").trim();
            }

            return Long.valueOf(response.trim());
        }
        return null;
    }

    public static String postXml(final String url, final String data) throws UnsupportedEncodingException, IOException {

        LOG.info("  => POST XML "+url );
        PostMethod post = new PostMethod(url);
        post.setRequestEntity(new StringRequestEntity(data, "text/xml", "utf-8"));
        post.setRequestHeader("Content-type", "text/xml; charset=utf-8");
        JuxtaServiceTest.execRequest(post);
        String response = JuxtaServiceTest.getResponseString(post);
        post.releaseConnection();
        LOG.info("  <= Response: " + response);
        return response;
    }
    
    public static String doPostError(final String url, final String json) throws Exception {

        LOG.info("  => POST "+url );
        PostMethod post = new PostMethod(url);
        if ( json != null ) {
            post.setRequestEntity(new StringRequestEntity(json, "application/json", "utf-8"));
        }
        post.setRequestHeader("Content-type", "application/json; charset=utf-8");
        JuxtaServiceTest.execRequest(post, false);
        String response = JuxtaServiceTest.getResponseString(post);
        post.releaseConnection();
        LOG.info("  <= Response: " + response);
        return response;
    }
    
    private static String doPost(final String url, final String json) throws UnsupportedEncodingException, IOException {

        LOG.info("  => POST "+url );
        PostMethod post = new PostMethod(url);
        if ( json != null ) {
            post.setRequestEntity(new StringRequestEntity(json, "application/json", "utf-8"));
        }
        post.setRequestHeader("Content-type", "application/json; charset=utf-8");
        JuxtaServiceTest.execRequest(post);
        String response = JuxtaServiceTest.getResponseString(post);
        post.releaseConnection();
        LOG.info("  <= Response: " + response);
        return response;
    }
    
    public static String jsonResponsePost(final String url, final String json) throws Exception {
        return doPost(url, json);
    }
    
    public static String getJson(final String url, final Long id)  throws Exception {
        LOG.info("  => GET "+url+"/"+id);
        GetMethod get = new GetMethod(url +"/"+id);
        get.setRequestHeader("accept", "application/json");
        JuxtaServiceTest.execRequest(get);
        String response = JuxtaServiceTest.getResponseString(get);
        get.releaseConnection();
        LOG.info("  <= Response: " + response);
        return response;
    }
    
    public static String getText(final String url, final Long id)  throws Exception {
        LOG.info("  => GET "+url+"/"+id);
        GetMethod get = new GetMethod(url +"/"+id);
        get.setRequestHeader("accept", "text/plain");
        JuxtaServiceTest.execRequest(get);
        String response = JuxtaServiceTest.getResponseString(get);
        get.releaseConnection();
        LOG.info("  <= Response: " + response);
        return response;
    }
    
    public static String getJson(final String url)  throws Exception {
        LOG.info("  => GET "+url);
        GetMethod get = new GetMethod(url);
        get.setRequestHeader("accept", "application/json");
        JuxtaServiceTest.execRequest(get);
        String response = JuxtaServiceTest.getResponseString(get);
        get.releaseConnection();
        LOG.info("  <= Response: " + response);
        return response;
    }
    
    public static void put(final String url, final Long id, final String json) throws Exception {
        LOG.info("  => PUT "+url+"/"+id+" : " + json);
        PutMethod put = new PutMethod(url+"/"+id);
        put.setRequestEntity(new StringRequestEntity(json, "application/json", "utf-8"));
        put.setRequestHeader("Content-type", "application/json");
        JuxtaServiceTest.execRequest(put);
        String response = JuxtaServiceTest.getResponseString(put);
        LOG.info("  <= Response: " + response);
        put.releaseConnection();
    }
}
