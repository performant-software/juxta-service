package org.juxtasoftware;

import java.util.ArrayList;
import java.util.List;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.ClientInfo;
import org.restlet.data.MediaType;
import org.restlet.data.Preference;
import org.restlet.routing.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filter to detect dotted typs in request segments. It will strip
 * the type info from the segment and reset the Accept headers 
 * appropriately.
 * 
 * @author loufoster
 *
 */
public class RequestFilter extends Filter {    
    
    private static final Logger LOG = LoggerFactory.getLogger(Constants.WS_LOGGER_NAME);
    
    
    @Override 
    protected int doHandle(Request request, Response response) {
               
        String lastSegment = request.getResourceRef().getLastSegment();
        if ( lastSegment.contains(".json")) {
            request.getResourceRef().setLastSegment(lastSegment.substring(0, lastSegment.length()-5));
            setAccepts( request, MediaType.APPLICATION_JSON);
        } else if ( lastSegment.contains(".xml")) {
            request.getResourceRef().setLastSegment(lastSegment.substring(0, lastSegment.length()-4));
            setAccepts( request, MediaType.TEXT_XML);
        } else if ( lastSegment.contains(".txt")) {
            request.getResourceRef().setLastSegment(lastSegment.substring(0, lastSegment.length()-4));
            setAccepts( request, MediaType.TEXT_PLAIN);
        } else if ( lastSegment.contains(".html")) {
            request.getResourceRef().setLastSegment(lastSegment.substring(0, lastSegment.length()-5));
            setAccepts( request, MediaType.TEXT_HTML);
        } 
        int resp = super.doHandle(request, response);
      
        // collect info and write out the request/response details to the log file
        ClientInfo info = request.getClientInfo();
        String clientIp = info.getAddress();
        String user = "-";
        if (info.getUser() != null ) {
            user = info.getUser().toString();
        }
        LOG.info(clientIp+"\t"+user+"\t"+request.toString()+"\t"+response.getStatus().getCode()+"\t"+info.getAgent());
        
        return resp;
    }
    
    
    private void setAccepts(Request request, MediaType mediaType) {
        Preference<MediaType> pref = new Preference<MediaType>( mediaType );
        List<Preference<MediaType>> accept = new ArrayList<Preference<MediaType>>();
        accept.add(pref);
        request.getClientInfo().setAcceptedMediaTypes(accept);
    }
}
