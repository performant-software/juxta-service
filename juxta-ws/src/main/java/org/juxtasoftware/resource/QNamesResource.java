package org.juxtasoftware.resource;

import java.util.List;

import org.juxtasoftware.dao.QNameDao;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;

import eu.interedition.text.Name;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class QNamesResource extends BaseResource {
    @Autowired private QNameDao qNameDao;

    @Override
    protected void doInit() throws ResourceException { 
        super.doInit();
    }
    
    /**
     * Get Json representation of all available QNames
     * @return
     */
    @Get("json")
    public Representation toJson() {
        List<Name> qnames = this.qNameDao.list();
        Gson gson = new Gson();
        return toJsonRepresentation(gson.toJson(qnames));
    }
}
