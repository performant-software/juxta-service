package org.juxtasoftware.resource;

import java.util.Set;

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
import org.juxtasoftware.util.NamespaceExtractor.XmlType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Post;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
        Long sourceId = null;
        try {
            sourceId = json.get("source").getAsLong();
        } catch (NumberFormatException e) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation( "Invalid source id" );
        }
        
        Source srcDoc = this.sourceDao.find(this.workspace.getId(), sourceId);
        if ( validateModel(srcDoc) == false ) {
            getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return toTextRepresentation( "Invalid source "+sourceId);
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
        boolean newXslt = false;
        Workspace pub = this.workspaceDao.getPublic();
        if ( json.has("xslt") ) {
            Long xsltId = json.get("xslt").getAsLong();
            xslt = this.xsltDao.find(xsltId);
        } else {
            // if none specifed and source is xml, generate a new XSLT
            // based in the starter template
            if ( srcDoc.getType().equals(Source.Type.XML)) {
                try {
                    newXslt = true;
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
            return toTextRepresentation( "Requested XSLT does not exist");    
        }

        
        // prevent duplicate witnesses from being created
        if ( this.witnessDao.exists(this.workspace, finalName) ) {
            setStatus(Status.CLIENT_ERROR_CONFLICT);
            if ( newXslt ) {
                this.xsltDao.delete(xslt);
            }
            return toTextRepresentation(
                "Witness '"+finalName+"' already exists");
        }
        
        try {
            Long witnessId = this.transformer.transform(srcDoc, xslt, finalName);
            return toTextRepresentation( witnessId.toString() );
        } catch (Exception e) {
            LOG.error("Caught Excepion: unable to transform source", e);
            setStatus(Status.SERVER_ERROR_INTERNAL);
            if ( newXslt ) {
                this.xsltDao.delete(xslt);
            }
            return toTextRepresentation("Transform Failed: "+e.getMessage());
        } 
    }

    private JuxtaXslt createXsltFromTemplate(final Source src, final String name) throws Exception {
        // if a document has multiple namespaces, do not try to apply TEI or ram templates to it, just generic
        Set<NamespaceInfo> namespaces = NamespaceExtractor.extract( this.sourceDao.getContentReader(src) ); 
        if ( namespaces.size() > 1 ) {
            return this.xsltFactory.create(this.workspace.getId(), name );
        } else {
            // get the NS info....
            NamespaceInfo nsInfo = NamespaceInfo.createBlankNamespace();
            if ( namespaces.size() == 1 ) {
                nsInfo = (NamespaceInfo)namespaces.toArray()[0];
            }
            
            // create XSLT based on a scan thru the src to determine XML type
            XmlType xmlType = NamespaceExtractor.determineXmlType( this.sourceDao.getContentReader(src) );
            LOG.info(src.toString()+" appears to be XmlType: "+xmlType.toString());
            switch (xmlType ) {
                case TEI:
                    return getTeiXslt( name, nsInfo);
                case RAM:
                    return getRamXslt( name, nsInfo);
                case JUXTA:
                    return getJuxtaXslt( name, nsInfo);
                default:
                    return this.xsltFactory.create(this.workspace.getId(), name );
            }
        }
    }

    private JuxtaXslt getJuxtaXslt(String name, NamespaceInfo nsInfo) throws Exception {
        this.templateParser.parse( ClassLoader.getSystemResourceAsStream("juxta-document.xml") );
        TemplateInfo jxInfo = this.templateParser.getTemplates().get(0);
        return this.xsltFactory.createFromTemplateInfo(this.workspace.getId(), name, jxInfo, nsInfo);
    }

    private JuxtaXslt getTeiXslt(final String name, final NamespaceInfo namespace ) throws Exception {
        this.templateParser.parse( ClassLoader.getSystemResourceAsStream("tei-template.xml") );
        TemplateInfo teiInfo = this.templateParser.getTemplates().get(0);
        namespace.setDefaultPrefix("tei");
        return this.xsltFactory.createFromTemplateInfo(this.workspace.getId(), name, teiInfo, namespace);
    }
    
    private JuxtaXslt getRamXslt(final String name, final NamespaceInfo namespace ) throws Exception {
        this.templateParser.parse( ClassLoader.getSystemResourceAsStream("ram.xml") );
        TemplateInfo ramInfo = this.templateParser.getTemplates().get(0);
        return this.xsltFactory.createFromTemplateInfo(this.workspace.getId(), name, ramInfo, namespace);
    }

    
}
