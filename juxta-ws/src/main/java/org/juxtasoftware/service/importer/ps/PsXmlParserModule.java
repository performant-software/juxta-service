package org.juxtasoftware.service.importer.ps;

import java.util.Map;

import org.juxtasoftware.service.importer.ps.WitnessParser.WitnessInfo;

import eu.interedition.text.Name;
import eu.interedition.text.xml.XMLEntity;
import eu.interedition.text.xml.XMLParserState;
import eu.interedition.text.xml.module.TextXMLParserModule;

class PsXmlParserModule extends TextXMLParserModule {
    private final WitnessInfo tgtWitness;
    
    public PsXmlParserModule( final WitnessInfo witness ) {
        this.tgtWitness = witness;  
    }
    
    @Override
    public void start(XMLEntity entity, XMLParserState state) {

        final String localName = entity.getName().getLocalName();

        // is this a per-witness-difference?
        if (localName.equals("rdg") || localName.equals("lem")) {
            
            // pull the id from attributes and see if it is the
            // one we care about for this iteration
            boolean matchesTarget = false;
            final Map<Name, String> attributes = entity.getAttributes();
            for (Name attrName : attributes.keySet()) {
                final String attrLocalName = attrName.getLocalName();
                if ("wit".equals(attrLocalName) || "lem".equals(attrLocalName) ) {

                    String idAttr = attributes.get(attrName).trim();
                    
                    // This id is prefixed with #. Multiple ids can be on
                    // one line, separated by spaces.
                    String[] ids = idAttr.split(" ");
                    for ( int i=0; i<ids.length; i++) {
                        String id = ids[i].substring(1).trim();
                        if ( id.equals(this.tgtWitness.getId()) || 
                             id.equals(this.tgtWitness.getGroupId()) ) {
                            matchesTarget = true;
                            break;
                        }
                    }
                    break;
                }
            }
            
            // toss anything that doesn match this witness
            if ( matchesTarget == false ) {
                state.getInclusionContext().push(false);
            }
        }
    }
}
