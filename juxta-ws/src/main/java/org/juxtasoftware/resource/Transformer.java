package org.juxtasoftware.resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.dao.RevisionDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.RevisionSet;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Workspace;
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
    @Autowired private JuxtaXsltDao xsltDao;
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
        
        // if an alternate name for the parsed witness was passed
        // get it; otherwise its just the src file minus extenson
        String finalName = "";
        if ( json.has("finalName")) {
            finalName = json.get("finalName").getAsString();
        } else {
            finalName = srcDoc.getName();
            int pos = finalName.lastIndexOf('.');
            if ( pos > -1 ) {
                finalName = finalName.substring(0, pos);
            }
        }

        // if requested, get the xslt transform
        JuxtaXslt xslt = null;
        Workspace pub = this.workspaceDao.getPublic();
        if ( json.has("xslt") ) {
            Long xsltId = json.get("xslt").getAsLong();
            xslt = this.xsltDao.find(xsltId);
        } else {
            // if none specifed and source is xml, generate a new XSLT
            // based in the starter template
            if ( srcDoc.getText().getType().equals(Text.Type.XML)) {
                try {
                    xslt = createXsltFromTemplate(srcDoc, finalName+"-transform");
                } catch (IOException e) {
                    setStatus(Status.SERVER_ERROR_INTERNAL);
                    return toTextRepresentation("Unable to generate XSLT for transform: "+e.toString());
                }
            }
        }
        
        // validate template/workspace match (except public)
        if ( xslt != null && this.workspace.getId().equals(xslt.getWorkspaceId()) == false && xslt.getWorkspaceId().equals(pub.getId())==false){
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation( "Template "+sourceId+
                " does not exist in workspace "+this.workspace.getName());    
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
            Long witnessId = this.transformer.transform(srcDoc, xslt, revSet, finalName);
            return toTextRepresentation( witnessId.toString() );
        } catch (Exception e) {
            LOG.error("Caught Excepion: unable to transform source", e);
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Transform Failed: "+e.getMessage());
        } 
    }

    private JuxtaXslt createXsltFromTemplate(final Source src, final String name) throws IOException {
        String xslt = IOUtils.toString( ClassLoader.getSystemResourceAsStream("xslt/basic.xslt"), "utf-8");
        xslt = xslt.replaceAll("\\{LB_LIST\\}", "*");
        xslt = xslt.replaceAll("\\{LINEBREAK\\}", "&#10;");
        
        BufferedReader br = new BufferedReader(this.sourceDao.getContentReader(src));
        List<String> namespaces = new ArrayList<String>();
        while (true) {
            String line = br.readLine();
            if ( line == null ) {
                break;
            }
            
            // default namespace?
            if ( line.contains("xmlns=\"") ) {
                int pos = line.indexOf("xmlns=\"")+7;
                int end = line.indexOf('"', pos);
                String namespace = "xmlns:ns=\""+line.substring(pos,end)+"\"";
                namespaces.add( namespace );
            } 
            
            // no-namespace loc?
            if ( line.contains(":noNamespaceSchemaLocation=\"") ) {
                int pos = line.indexOf(":noNamespaceSchemaLocation=\"")+28;
                int end = line.indexOf('"', pos);
                String namespace = "xmlns:ns=\""+line.substring(pos,end)+"\"";
                namespaces.add( namespace );               
            } 
            
            // specifc namespace?
            if ( line.contains("xmlns:") ) {                
                int pos = line.indexOf("xmlns:")+6;
                while ( pos > -1  ) {
                    int nsPos = pos;
                    int nsEndPos = line.indexOf("=\"", pos);
                    pos = nsEndPos+2;
                    int end = line.indexOf('"', pos);
                    String url = line.substring(pos,end);
                    if ( url.contains("w3.org") == false ) {
                        String namespace = "xmlns:"+line.substring(nsPos,nsEndPos)+"=\""+url+"\"";
                        namespaces.add(namespace);
                    }
                    int newPos = line.indexOf("xmlns:", end);
                    if (newPos > -1 ) {
                        pos = newPos+6;
                    } else {
                        pos = -1;
                    }
                }
            }
        }
        
        if ( namespaces.size() > 0 ) {
            StringBuilder sb = new StringBuilder();
            for ( String ns : namespaces ) {
                sb.append(ns).append(" ");
            }
            xslt = xslt.replaceAll("\\{NAMESPACE\\}", sb.toString() );
            xslt = xslt.replaceAll("\\{NOTE\\}", "ns:note");
            xslt = xslt.replaceAll("\\{PB\\}", "ns:pb");
        } else {
            xslt = xslt.replaceAll("\\{NAMESPACE\\}", "");
            xslt = xslt.replaceAll("\\{NOTE\\}", "note");
            xslt = xslt.replaceAll("\\{PB\\}", "pb");
        }
        
        JuxtaXslt jxXslt = new JuxtaXslt();
        jxXslt.setName(name);
        jxXslt.setWorkspaceId( this.workspace.getId() );
        jxXslt.setXslt(xslt);
        Long id = this.xsltDao.create(jxXslt);
        jxXslt.setId(id);
        return jxXslt;
    }
}
