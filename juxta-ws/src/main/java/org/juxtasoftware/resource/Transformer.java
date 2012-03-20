package org.juxtasoftware.resource;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import org.juxtasoftware.dao.TemplateDao;
import org.juxtasoftware.dao.RevisionDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.Template;
import org.juxtasoftware.model.RevisionSet;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.service.SourceTransformer;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Post;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.interedition.text.Text;

/**
 * Resource used to transform source documents into
 * texts by applying a parsing template
 *
 * @author lfoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class Transformer extends BaseResource {
    @Autowired private SourceDao sourceDao;
    @Autowired private TemplateDao templateDao;
    @Autowired private RevisionDao revisionDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private SourceTransformer transformer;
    
    /**
     * Transform the source identified by <code>sourceID</id> into a new
     * text using parsing template <code>templateID</code>
     *
     * @param jsonString
     * @return
     */
    @Post("json")
    public Representation transformSource( final String jsonString ) {

        LOG.info("Transform post data: "+jsonString);

        // Get and validate the source from json
        JsonParser parser = new JsonParser();
        JsonObject json = parser.parse(jsonString).getAsJsonObject();
        Long sourceId = json.get("source").getAsLong();
        Source srcDoc = this.sourceDao.find(this.workspace.getId(), sourceId);
        if ( validateModel(srcDoc) == false ) {
            getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return toTextRepresentation( "Invalid source "+sourceId);
        }
        if ( this.workspace.getId().equals(srcDoc.getWorkspaceId()) == false) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return toTextRepresentation( "Source "+sourceId+
                " does not exist in workspace "+this.workspace.getName());    
        }

        // if requested, get the parse template
        Template template = null;
        if ( json.has("template") ) {
            Long templateId = json.get("template").getAsLong();
            template = this.templateDao.find(templateId);
        } else {
            // if none specifed and source is xml, first search for
            // a suitable default template. If none are found leave
            // the template null. This will be treated by the transformer
            // as include all tags.
            if ( srcDoc.getText().getType().equals(Text.Type.XML)) {
                String rootEle = this.sourceDao.getRootElement(srcDoc);
                template = this.templateDao.findDefault( this.workspace, rootEle );
            }
        }
        
        // validate template/workspace match
        if ( template != null && this.workspace.getId().equals(template.getWorkspaceId()) == false) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation( "Template "+sourceId+
                " does not exist in workspace "+this.workspace.getName());    
        }

        // if an alternate name for the parsed witness was passed
        // get it; otherwise its just the src file minus extenson
        String finalName = "";
        if ( json.has("finalName")) {
            finalName = json.get("finalName").getAsString();
        } else {
            finalName = srcDoc.getFileName();
            int pos = finalName.lastIndexOf('.');
            if ( pos > -1 ) {
                finalName = finalName.substring(0, pos);
            }
        }
        
        // prevent duplicate witnesses from being created
        if ( this.witnessDao.exists(this.workspace, finalName) ) {
            setStatus(Status.CLIENT_ERROR_CONFLICT);
            return toTextRepresentation(
                "Witness '"+finalName+"' already exists in workspace '"+this.workspace.getName()+"'");
        }
        
        RevisionSet revSet = null;
        if ( json.has("revision")) {
            Long revId = json.get("revision").getAsLong();
            revSet = this.revisionDao.getRevsionSet(revId);
        }

        try {
            Long witnessId = this.transformer.transform(srcDoc, template, revSet, finalName);
            return toTextRepresentation( witnessId.toString() );
        } catch (IOException e) {
            LOG.error("Caught Excepion: unable to transform source", e);
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Transform Failed: "+e.getMessage());
        } catch (XMLStreamException e) {
            LOG.error("Caught Exception: unable to transform source", e);
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Transform Failed: "+e.getMessage());
        }
    }
}
