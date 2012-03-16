package org.juxtasoftware.util.xml.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.juxtasoftware.model.PageBreak;

import eu.interedition.text.Name;
import eu.interedition.text.xml.XMLEntity;
import eu.interedition.text.xml.XMLParserState;
import eu.interedition.text.xml.module.XMLParserModuleAdapter;

/**
 * XML parser module to collect PB tag information so it can be 
 * visualized on the UI
 * 
 * @author loufoster
 *
 */
public class PageBreakXmlParserModule extends XMLParserModuleAdapter {
    private List<PageBreak> breaks = new ArrayList<PageBreak>();
    
    public List<PageBreak> getPageBreaks() {
        return this.breaks;
    }
    
    @Override
    public void end(XMLEntity entity, XMLParserState state) {
        final long textOffset = state.getTextOffset();
        final Map<Name, String> attributes = entity.getAttributes();
        final String localName = entity.getName().getLocalName();
        
        // collect pb info and add it to the list
        if ("pb".equals(localName)) {
            PageBreak pb = new PageBreak();
            pb.setOffset(textOffset);
            for (Name attrName : attributes.keySet()) {
                final String attrLocalName = attrName.getLocalName();
                if ("n".equals(attrLocalName)) {
                   pb.setLabel( attributes.get(attrName) );
                   break;
                }
            }
            this.breaks.add(pb);
        } 
    }
}
