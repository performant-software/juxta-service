package org.juxtasoftware.service;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class XmlTemplateParser {
    
//    private static final String CONFIG_SCHEMA = "templates.xsd";
//    private static final String XML_SCHEMA = "http://www.w3.org/2001/XMLSchema";
//    private Map<String, Template> templateMap = new HashMap<String, Template>();
//    
//    /**
//     * Helper method used to setup a schema aware, validating XML document builder
//     * @return
//     * @throws SAXException
//     * @throws ParserConfigurationException
//     */
//    private DocumentBuilder createDocBuilder() throws SAXException, ParserConfigurationException {
//        // load the template schema
//        SchemaFactory schemaFactory = SchemaFactory.newInstance(XML_SCHEMA);
//        URL schemaUrl = ClassLoader.getSystemResource(CONFIG_SCHEMA);
//        Schema schema = schemaFactory.newSchema(schemaUrl);
//   
//        // setup a docBuilder that will validate against the schema
//        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
//        factory.setValidating(false);
//        factory.setNamespaceAware(true);
//        factory.setSchema(schema);
//        DocumentBuilder docBuilder = factory.newDocumentBuilder();
//        docBuilder.setErrorHandler(new TemplateErrorHander() );
//        return docBuilder;
//    }
//    
//    public void parse( InputStream templateStream ) throws Exception {
//        // setup the schema aware document builder
//        DocumentBuilder docBuilder = createDocBuilder();
//
//        // parse the config into a doc. and walk the template nodes
//        Document doc = docBuilder.parse( templateStream );
//        XPath xpath = XPathFactory.newInstance().newXPath();
//        XPathExpression expr = xpath.compile("//template");
//        XPathExpression tagExpr = xpath.compile("tag");
//        NodeList nodes = (NodeList)expr.evaluate(doc, XPathConstants.NODESET);
//        
//        for ( int i=0; i<nodes.getLength(); i++) {
//            Node templateNode = nodes.item(i);
//            String name = templateNode.getAttributes().getNamedItem("name").getTextContent();
//            String guid = templateNode.getAttributes().getNamedItem("guid").getTextContent();
//            String defaultStr = templateNode.getAttributes().getNamedItem("isDefault").getTextContent();
//            boolean isDefault = Boolean.valueOf(defaultStr);
//            String rootTag = templateNode.getAttributes().getNamedItem("rootTagName").getTextContent();
//            
//            Template template = new Template();
//            template.setName( name );
//            template.setDefault( isDefault );
//            template.setRootElement( WildcardQName.fromString(rootTag) );
//            
//            // Walk all of its children
//            // and add behaviors to this template for each one
//            NodeList tags = (NodeList)tagExpr.evaluate(templateNode, XPathConstants.NODESET);
//            for (int t=0;t<tags.getLength();t++) {
//                Node tag = tags.item(t);
//
//                boolean tagNl = Boolean.parseBoolean(tag.getAttributes().getNamedItem("newLine").getTextContent());
//                String tagAct = tag.getAttributes().getNamedItem("action").getTextContent();
//                String tagName = tag.getAttributes().getNamedItem("name").getTextContent();
//               
//                // unlike desktopm new line is its own action here
//                if ( tagNl ) {
//                    Template.TagAction act = createTagAction( tagName, Template.TagAction.Action.NEW_LINE.toString());
//                    act.setTemplate( template );
//                    template.getTagActions().add(act);
//                }
//                
//                // include happens by default in this system; no need for 
//                // separate action
//                if ( tagAct.equals("INCLUDE") == false ) {
//                    Template.TagAction act = createTagAction( tagName, tagAct);
//                    act.setTemplate( template );
//                    template.getTagActions().add(act);
//                }
//            }
//            
//            this.templateMap.put(guid, template);
//        }
//    }
//    
//    public List<Template> getTemplates() {
//        return new ArrayList<Template>( this.templateMap.values());
//    }
//    
//    public Template findTemplate( final String guid ) {
//        return this.templateMap.get( guid );
//    }    
//    
//    private TagAction createTagAction(String tagName, String action) {
//        TagAction act = new TagAction();
//        act.setTag(  WildcardQName.fromString(tagName) );
//        act.setAction( action.toUpperCase() );
//        return act;
//    }
//    
//    /**
//     * Error handler for parsing template config. Ignore warnings and re-throw
//     * any encountered errors
//     */
//    private static class TemplateErrorHander implements org.xml.sax.ErrorHandler {
//
//        public void error(SAXParseException exception) throws SAXException {
//            throw exception;
//        }
//
//        public void fatalError(SAXParseException exception) throws SAXException {
//            throw exception;
//        }
//
//        public void warning(SAXParseException exception) throws SAXException {
//            // no-op
//        }
//    }
}
