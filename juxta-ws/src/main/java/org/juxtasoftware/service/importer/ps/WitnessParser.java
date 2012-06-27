package org.juxtasoftware.service.importer.ps;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Filter the TEI parallel segmentation input stream for
 * witList information and collect a list of witnesses.
 * 
 * @author loufoster
 *
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class WitnessParser extends DefaultHandler {
    private static SAXParserFactory parserFactory;
    private List<PsWitnessInfo> witnesses = new ArrayList<PsWitnessInfo>();
    private Stack<String> groupIdStack = new Stack<String>();
    private PsWitnessInfo currWitness = null;
    private StringBuilder currDesc = new StringBuilder();

    static {
        parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(false);
        parserFactory.setValidating(false);
    }
    
    public void parse( Reader teiReader ) throws ParserConfigurationException, SAXException, IOException {
        SAXParser parser = parserFactory.newSAXParser();  
        parser.parse( new InputSource(teiReader), this);
    }
    
    @Override
    public InputSource resolveEntity(String systemId, String publicId) throws IOException, SAXException {
        if (publicId.contains(".dtd")) {
            return new InputSource(new StringReader(""));
        } else {
            return null;
        }
    }
    
    public List<PsWitnessInfo> getWitnesses() {
        return this.witnesses;
    }
    
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if ( qName.equals("listWit")) {
            String id = getId(attributes);
            if ( id != null ) {
                this.groupIdStack.push( id );
            }
        } else if ( qName.equals("witness") ) {
            this.currWitness = new PsWitnessInfo( getId(attributes) );
            if ( this.groupIdStack.empty() == false ) {
                this.currWitness.groupId = this.groupIdStack.peek();
            }
        }
    }

    private String getId(Attributes attributes) {
        String id = attributes.getValue("xml:id");
        if ( id == null ) {
            id = attributes.getValue("id");
        }
        return id;
    }
    
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if ( qName.equals("listWit")) {
            if ( this.groupIdStack.empty() == false ) {
                this.groupIdStack.pop();
            }
        } else if ( qName.equals("witness") ) {
            String out = this.currDesc.toString();
            out = out.trim();
            out = out.replaceAll("\\t+", " ");
            out = out.replaceAll("\\n+", " ");
            out = out.replaceAll(" +", " ");
            this.currWitness.description = out;
            this.witnesses.add( this.currWitness );
            this.currWitness = null;
            this.currDesc = new StringBuilder();
        }
    }
    
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if ( this.currWitness  != null ) {
            this.currDesc.append( ch , start, length);
        }
    }
    
    
    /**
     * Collection of basic data from the witList tags
     */
    public static class PsWitnessInfo {
        private final String id;
        private String groupId;
        private String description;
        private PsWitnessInfo( final String id) {
            this.id = id;
        }
        public boolean hasGroupAlias() {
            return (this.groupId != null && this.groupId.length() > 0);
        }
        public String getId() {
            return id;
        }
        public String getGroupId() {
            return groupId;
        }
        public String getDescription() {
            return description;
        }
        public String getName() {
            if ( this.description != null && this.description.length() >0 ) {
                return this.id+" : "+this.description;
            }
            return this.id;
        }
        @Override
        public String toString() {
            return "PsWitnessInfo [id=" + id + ", description=" + description + "]";
        }
        
    }
}
