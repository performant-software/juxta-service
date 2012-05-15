package org.juxtasoftware;

import java.util.ArrayList;
import java.util.List;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.ClientInfo;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Preference;
import org.restlet.engine.header.Header;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.routing.Filter;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filter to detect dotted typs in request segments. It will strip
 * the type info from the segment and reset the Accept headers 
 * appropriately. It also uses an access list to allow ajax access
 * from foreign sites.
 * 
 * @author loufoster
 *
 */
public class RequestFilter extends Filter {    
    private List<String> allowAccessList;
    
    private static final Logger LOG = LoggerFactory.getLogger("Request");
    
    public void setAccessList( final String accessList ) {
        String[] origins = accessList.split(",");
        this.allowAccessList = new ArrayList<String>();
        for ( int i=0; i< origins.length; i++ ) {
            this.allowAccessList.add("http://"+origins[i]);
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    protected int beforeHandle(Request request, Response response) {
        
        // If the OPTIONS requens has been recieved, accept it if it is
        // from a host listed on the access list.
        if (Method.OPTIONS.equals(request.getMethod())) {
            Series<Header> requestHeaders = (Series<Header>) request.getAttributes().get(
                HeaderConstants.ATTRIBUTE_HEADERS);
            String origin = requestHeaders.getFirstValue("Origin", true);

            if ( this.allowAccessList.contains(origin) )  {
                Series<Header> responseHeaders = (Series<Header>) response.getAttributes().get(
                    HeaderConstants.ATTRIBUTE_HEADERS);
                if (responseHeaders == null) {
                    responseHeaders = new Series<Header>(Header.class);
                    response.getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, responseHeaders);
                }
                responseHeaders.add(new Header("Access-Control-Allow-Origin", origin));
                responseHeaders.add(new Header("Access-Control-Allow-Methods", "GET,PUT,POST,DELETE,OPTIONS"));
                responseHeaders.add(new Header("Access-Control-Allow-Headers", "Content-Type"));
                responseHeaders.add(new Header("Access-Control-Allow-Credentials", "true"));
                responseHeaders.add(new Header("Access-Control-Max-Age", "60"));
                response.setEntity(new EmptyRepresentation());
                response.getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, responseHeaders);
                return SKIP;
            }
        }

        return super.beforeHandle(request, response);
    }
    
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
        } else {
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

    @Override
    @SuppressWarnings("unchecked")
    protected void afterHandle(Request request, Response response) {
        // If this is not an OPTIONS request and the origin IS set,
        // make sure that the origin is on the access list. If not,
        // the request will be rejected.
        if (Method.OPTIONS.equals(request.getMethod()) == false) {
            Series<Header> requestHeaders = (Series<Header>) request.getAttributes().get(
                HeaderConstants.ATTRIBUTE_HEADERS);
            String origin = requestHeaders.getFirstValue("Origin", true);

            if (origin != null && this.allowAccessList.contains(origin)) {
                Series<Header> responseHeaders = (Series<Header>) response.getAttributes().get(
                    HeaderConstants.ATTRIBUTE_HEADERS);
                if (responseHeaders == null) {
                    responseHeaders = new Series<Header>(Header.class);
                    response.getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, responseHeaders);
                }
                responseHeaders.add(new Header("Access-Control-Allow-Origin", origin));
                responseHeaders.add(new Header("Access-Control-Allow-Methods", "GET,PUT,POST,DELETE,OPTIONS"));
                responseHeaders.add(new Header("Access-Control-Allow-Headers", "Content-Type"));
                responseHeaders.add(new Header("Access-Control-Allow-Credentials", "true"));
                responseHeaders.add(new Header("Access-Control-Max-Age", "60"));
                response.getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, responseHeaders);
            }
        }
        super.afterHandle(request, response);
    }
}
