package org.juxtasoftware;

import org.restlet.Application;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.LocalReference;
import org.restlet.resource.Directory;
import org.restlet.routing.Router;
import org.restlet.security.ChallengeAuthenticator;
import org.restlet.security.MapVerifier;
import org.springframework.beans.factory.annotation.Autowired;

public class JuxtaWsApplication extends Application {
    @Autowired private Boolean useAuthenticator;
    @Autowired private String authenticatorUser;
    @Autowired private String authenticatorPass;
    
    private RequestFilter requestFilter;
    private ChallengeAuthenticator authenticator;
    private Router router;
    
    public void setRequestFilter( RequestFilter filter ) {
        this.requestFilter = filter;
    }
    
    public void setRouter(Router router ) { 
        this.router = router;
    }
    
    @Override
    public Restlet createInboundRoot() {
        
        // map the puclic folder to the juxta root. This opens up
        // external acess to JS, CSS, HTML and images
        final String curDir = System.getProperty("user.dir");
        final LocalReference pub = LocalReference.createFileReference(curDir+"/public");
        final Directory publicDir = new Directory( getContext().createChildContext(),pub);
        this.router.attach("/juxta", publicDir);
        
        if ( this.useAuthenticator ) {
            // Create a simple password verifier  
            MapVerifier verifier = new MapVerifier();
            verifier.getLocalSecrets().put(this.authenticatorUser, this.authenticatorPass.toCharArray());

            // ... and use it to create an OPTIONAL challenge authenticator
            // It is optional to allow free access to the JS ad CSS files
            boolean optional = true;
            this.authenticator = new ChallengeAuthenticator(getContext(), optional, 
                ChallengeScheme.HTTP_BASIC, 
                "Juxta Login", verifier) {
               
                @Override
                protected boolean authenticate(Request request, Response response) {
                    if (request.getChallengeResponse() == null) {
                        return false;
                    } else {
                        return super.authenticate(request, response);
                    }
                } 
                
            };
            
            // Chain of handling: Auth => filter -> router
            this.requestFilter.setNext( this.router );
            this.authenticator.setNext( this.requestFilter );
            return this.authenticator;
        } else {
            // Chain of handling: filter -> router
            this.requestFilter.setNext( this.router );
            return this.requestFilter;
        }
    }
    
    public boolean authenticate(Request request, Response response) {
        if ( this.useAuthenticator ) {
            if (!request.getClientInfo().isAuthenticated()) {
                this.authenticator.challenge(response, false);
                return false;
            }
            return true;
        }
        return true;
    }
}
