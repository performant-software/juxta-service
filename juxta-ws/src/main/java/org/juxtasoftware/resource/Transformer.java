package org.juxtasoftware.resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Workspace;
import org.juxtasoftware.service.SourceTransformer;
import org.juxtasoftware.service.importer.JuxtaXsltFactory;
import org.juxtasoftware.service.importer.XmlTemplateParser;
import org.juxtasoftware.service.importer.XmlTemplateParser.TemplateInfo;
import org.juxtasoftware.util.NamespaceExtractor;
import org.juxtasoftware.util.NamespaceExtractor.NamespaceInfo;
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
    @Autowired private WitnessDao witnessDao;
    @Autowired private SourceTransformer transformer;
    @Autowired private XmlTemplateParser templateParser;
    @Autowired private JuxtaXsltFactory xsltFactory;
    
    public enum XmlType {GENERIC, TEI, RAM};
    
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
                    xslt = createXsltFromTemplate(srcDoc, finalName);
                } catch (Exception e) {
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
        
        try {
            Long witnessId = this.transformer.transform(srcDoc, xslt, finalName);
            return toTextRepresentation( witnessId.toString() );
        } catch (Exception e) {
            LOG.error("Caught Excepion: unable to transform source", e);
            setStatus(Status.SERVER_ERROR_INTERNAL);
            this.xsltDao.delete(xslt);
            return toTextRepresentation("Transform Failed: "+e.getMessage());
        } 
    }

    private JuxtaXslt createXsltFromTemplate(final Source src, final String name) throws Exception {
        XmlType xmlType = determineXmlType(src);
        LOG.info(src.toString()+" appears to be XmlType: "+xmlType.toString());
        switch (xmlType ) {
            case TEI:
                return getTeiXslt( name );
            case RAM:
                return getRamXslt( name );
            default:
                return  getGenericXslt( src, name );
        }
    }
    
    private JuxtaXslt getGenericXslt( final Source src, final String name ) throws IOException {
        String xslt = JuxtaXsltFactory.getGenericTemplate();
        xslt = xslt.replaceAll("\\{LINEBREAK\\}", "&#10;");
        
        // for now act like desktop: if namespaces are included in the xml, wildcard all tag names
        Set<NamespaceInfo> namespaces = NamespaceExtractor.extract( this.sourceDao.getContentReader(src) );        
        if ( namespaces.size() > 0 ) {
            xslt = xslt.replaceAll("\\{NAMESPACE\\}", "");
            xslt = xslt.replaceAll("\\{NOTE\\}", getWildcardName("note"));
            xslt = xslt.replaceAll("\\{PB\\}", getWildcardName("pb"));
        } else {
            xslt = xslt.replaceAll("\\{NAMESPACE\\}", "");
            xslt = xslt.replaceAll("\\{NOTE\\}", "note");
            xslt = xslt.replaceAll("\\{PB\\}", "pb");
        }
        
        String breaksXslt = JuxtaXsltFactory.getBreaksTemplate();
        breaksXslt = breaksXslt.replaceAll("\\{LB_LIST\\}", "*");
        int breakPos = xslt.indexOf("<!--breaks-->")+13;
        xslt = xslt.substring(0, breakPos)+"\n    "+breaksXslt+xslt.substring(breakPos);
        
        JuxtaXslt jxXslt = new JuxtaXslt();
        jxXslt.setName(name+"-transform");
        jxXslt.setWorkspaceId( this.workspace.getId() );
        jxXslt.setXslt(xslt);
        Long id = this.xsltDao.create(jxXslt);
        jxXslt.setId(id);
        return jxXslt;
    }
    
    private JuxtaXslt getTeiXslt(final String name ) throws Exception {
        this.templateParser.parse( ClassLoader.getSystemResourceAsStream("tei-template.xml") );
        TemplateInfo teiInfo = this.templateParser.getTemplates().get(0);
        return this.xsltFactory.create(this.workspace.getId(), name, teiInfo);
    }
    
    private JuxtaXslt getRamXslt(final String name ) throws Exception {
        this.templateParser.parse( ClassLoader.getSystemResourceAsStream("ram.xml") );
        TemplateInfo teiInfo = this.templateParser.getTemplates().get(0);
        return this.xsltFactory.create(this.workspace.getId(), name, teiInfo);
    }

    private XmlType determineXmlType(Source src) {
        Reader r = this.sourceDao.getContentReader(src);
        BufferedReader  br = new BufferedReader(r);
        boolean foundNs = false;
        int noNsCount = 0;
        XmlType type = XmlType.GENERIC;
        try {
            while ( true ) {
                String line = br.readLine();
                if ( line == null ) {
                    break;
                } else {
                    if ( foundNs == false ) {
                        foundNs = line.contains(" xmlns") ;
                    } 
                    
                    if ( foundNs == true) {
                        if ( line.contains(" xmlns")==false ) {
                            noNsCount++;
                            // once the first namespace has been found,
                            // give up if we go a bit and see no more
                            if (noNsCount > 5 ) {
                                break;
                            }
                        } else {
                            if ( line.contains("http://www.tei-")) {
                                type = XmlType.TEI;
                                break;
                            } else if ( line.contains("ram.xsd")) {
                                type = XmlType.RAM;
                                break;
                            }
                        }
                    }
                } 
            }
        } catch (IOException e ) {
            // swallow it
        } finally {
            IOUtils.closeQuietly(br);
        }
        return type;
    }

    private String getWildcardName( final String name ) {
        return "*[local-name()='"+name+"']";
    }
}
