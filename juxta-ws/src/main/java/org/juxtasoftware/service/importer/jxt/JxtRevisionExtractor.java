package org.juxtasoftware.service.importer.jxt;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Given a global list of typeless revision occurrences, convert it into
 * a list of typed revision occurrence that can be used to generate single
 * exclusion entries in the xslt file.
 * @author loufoster
 *
 */
final class JxtRevisionExtractor  extends DefaultHandler{
    private Set<Integer> includedRevisions;
    private Integer revisionCount = 0;
    private Set<RevisionOccurrence> excludedRevisonsInfo = new HashSet<RevisionOccurrence>();
    private Map<String, Integer> tagCounts = new HashMap<String, Integer>();
    
    public void extract( final Reader srcReader, final List<Integer> includedRevisions ) throws SAXException, IOException {
        
        // juxta revision lists are zero based. Make them one based
        this.includedRevisions = new HashSet<Integer>();
        for ( Integer rev : includedRevisions ) {
            this.includedRevisions.add( (rev+1) );
        }
        this.tagCounts.put("add", 0);
        this.tagCounts.put("addSpan", 0);
        this.tagCounts.put("del", 0);
        this.tagCounts.put("delSpan", 0);
        Util.saxParser().parse( new InputSource( srcReader), this );
    }
    
    public Set<RevisionOccurrence>  getExcludedRevisions() {
        return this.excludedRevisonsInfo;
    }
    
    public int getTotalRevisionCount() {
        return this.revisionCount;
    }
    
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if ( isRevision(qName) ) {
            // increment total revsion count and individual revision type count
            this.revisionCount++;
            Integer cnt = this.tagCounts.get(qName) + 1;
            this.tagCounts.put(qName, cnt);
            
            // add revisions that are not part of the inclusion list are excluded
            if ( isAdd(qName) ) {
                if ( this.includedRevisions.contains( this.revisionCount ) == false ) {
                    this.excludedRevisonsInfo.add( new RevisionOccurrence(qName, cnt) );
                }
            } else {
                // delete revsions are excluded unless they are part of the list
                if ( this.includedRevisions.contains( this.revisionCount ) ) {
                    this.excludedRevisonsInfo.add( new RevisionOccurrence(qName, cnt) );
                }
            }
        }
    }
    
    private boolean isRevision(final String qName ) {
        return ( isAdd(qName) || isDelete(qName) );
    }
    private boolean isAdd(final String qName ) {
        return ( qName.equals("add") || qName.equals("addSpan"));
    }
    private boolean isDelete(final String qName ) {
        return ( qName.equals("del") || qName.equals("delSpan"));
    }
    
    /**
     * @author loufoster
     */
    static class RevisionOccurrence {
        private final String tagName;
        private final int occurrence;
       
        public RevisionOccurrence( final String tagName, final int occurrence) {
            this.tagName = tagName;
            this.occurrence = occurrence;
        }
        
        public String getTagName() {
            return tagName;
        }


        public int getOccurrence() {
            return occurrence;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + occurrence;
            result = prime * result + ((tagName == null) ? 0 : tagName.hashCode());
            return result;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            RevisionOccurrence other = (RevisionOccurrence) obj;
            if (occurrence != other.occurrence)
                return false;
            if (tagName == null) {
                if (other.tagName != null)
                    return false;
            } else if (!tagName.equals(other.tagName))
                return false;
            return true;
        }
        
        
    }
}
