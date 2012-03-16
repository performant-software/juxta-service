package org.juxtasoftware;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.CharacterSet;
import org.restlet.data.Language;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.service.StatusService;

/**
 * Custom status service used to return plain text error messges instead of html pages
 * Easier for consumers of the API to deal with this than to parse html and find reasons
 * 
 * @author loufoster
 *
 */
public class JuxtaStatusService extends StatusService {
    @Override
    public Representation getRepresentation(Status status, Request request, Response response) {
        if ( status.isError() ) {
            String txt = status.getCode()+" : "+status.getDescription();
            return new StringRepresentation(txt,
                MediaType.TEXT_PLAIN, Language.DEFAULT, CharacterSet.UTF_8);
        } else {
            return super.getRepresentation(status, request, response);
        }
    }
}
