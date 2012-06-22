package org.juxtasoftware.model;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

import org.juxtasoftware.service.importer.JuxtaXsltFactory;

import com.google.common.base.Objects;

public class JuxtaXslt extends WorkspaceMember {
    private String name;
    private String xslt;
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getXslt() {
        return xslt;
    }
    public void setXslt(String xslt) {
        this.xslt = xslt;
    }
    
    @Override
    public String toString() {
        return "JuxtaXslt [name=" + name + ", id=" + id + "]";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this.id != 0 && obj != null && obj instanceof JuxtaXslt) {
            return this.id == ((JuxtaXslt) obj).id;
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return this.id == 0 ? super.hashCode() : Objects.hashCode(this.id);
    }
    
    /**
     * Add an exclusion for a specific occurence of a tag. Only this instance will
     * be excluded from the restlating witness. If the tag is alread globally
     * excluded, this call does nothing
     * 
     * @param tagName
     * @param occurrence
     * @throws IOException 
     */
    public void addSingleExclusion(String tagName, Integer occurrence) throws IOException {
        // do nothing if the tag is already excluded
        if ( isExcluded( tagName, occurrence ) ) {
            return;
        }
        
        // bound the regon of text to work with: everything beteen
        // the single exclusion marker and the breaks marker
        final String single = "<!--single-exclusions-->";
        final String breaks = "<!--breaks-->";
        final String matchKey = "match=\"";
        final String testKey = "test=\"";
        final String strOccurrence = Integer.toString(occurrence);
        int pos = this.xslt.indexOf( single );
        int limitPos = this.xslt.indexOf( breaks );
        
        // see if there is already a single exclusion for this tag present
        pos = this.xslt.indexOf(matchKey, pos)+matchKey.length();
        while ( pos > -1 && pos < limitPos) {
            int endPos = this.xslt.indexOf("\"", pos);
            String tag = stripNamespace( this.xslt.substring(pos,endPos) );
            if ( tag.equals(tagName)  ) {
                // found a match. See if the specified occurrence is excluded
                int testPos = this.xslt.indexOf(testKey, pos)+testKey.length();
                int endTest = this.xslt.indexOf("\"", testPos);
                String conditions = this.xslt.substring(testPos, endTest);
                
                // strip out everything by a space separated list of occurrences
                // generate a set and see if the taget number is present
                String occurrences = conditions.replaceAll("(\\$count\\s+!=\\s+|\\s+and\\s+)", " ").trim();
                Set<String> nums = new HashSet<String>( Arrays.asList(occurrences.split(" ")) );
                if ( nums.contains( strOccurrence) ) {
                    // already present, nothing more to do
                    return;
                } else {
                    // not present: add the occurrence to the conditions
                    conditions += " and $count != ";
                    conditions += strOccurrence;
                    this.xslt = this.xslt.substring(0,testPos)+conditions+this.xslt.substring(endTest);
                    return;
                    
                }
            }
            pos = this.xslt.indexOf(matchKey, endPos);
            if ( pos > -1 ) {
                pos += matchKey.length();
            }
        }
        
        // if we got here, we need to add a new single exclusion for this tag occurrence.
        // NOTE: use Matcher to avoid problems caused by the $ in the replacement ext
        String xsltFrag = JuxtaXsltFactory.getSingleExclusionTemplate();
        xsltFrag = xsltFrag.replaceAll("\\{TAG\\}",  getWildcardName(tagName) );
        String condition = Matcher.quoteReplacement("$count != "+strOccurrence);
        try {
        xsltFrag = xsltFrag.replaceAll("\\{CONDITION\\}",  condition );
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        pos = this.xslt.indexOf( single ) + single.length()+1;
        this.xslt = this.xslt.substring(0,pos)+"    "+xsltFrag+"\n"+this.xslt.substring(pos);
    }
    
    /**
     * Add a global tag exclusion. All occurrences of this tag will be
     * excluded when transforming the source into a witness
     * @param tag
     */
    public void addGlobalExclusion(String tag) {
        final String marker = "<!--global-exclusions-->";
        int pos = this.xslt.indexOf(marker)+marker.length();
        String ex = "\n    <xsl:template match=\""+getWildcardName(tag)+"\"/>";
        this.xslt = xslt.substring(0,pos)+ex+this.xslt.substring(pos);   
        
        // remove from breaks so there is not a template ambiguity
        pos = xslt.indexOf("<!--breaks-->");
        int limitPos = xslt.indexOf("<xsl:template match=\"text()\">");
        pos = xslt.indexOf("match=\"", pos)+7;
        if ( pos > limitPos ) {
            return;
        }
        int endPos = xslt.indexOf("\"", pos);
        String tags = xslt.substring(pos,endPos);
        
        // remove the tag and clean up an double | that may remain
        String regex = "("+getWildcardName(tag)+"\\||\\|"+getWildcardName(tag)+")";
        regex = regex.replace("*", "\\*");
        String newTags = tags.replaceAll(regex,"");
        
        // sub the new tags list back into the xslt
        this.xslt = this.xslt.replace(tags, newTags);
    }
    
    /**
     * Returns true of this tag occurrence has been specifically excluded or
     * the tag has been globally excluded
     * @param tagName
     * @return
     */
    public boolean isExcluded( final String tagName, final int occurence ) {
        // first check the global exclude...
        final String global = "<!--global-exclusions-->";
        final String single = "<!--single-exclusions-->";
        final String breaks = "<!--breaks-->";
        final String matchKey = "match=\"";
        final String testKey = "test=\"";
        int pos = xslt.indexOf( global );
        int limitPos = xslt.indexOf( single );
        pos = xslt.indexOf(matchKey, pos)+matchKey.length();
        if ( pos < limitPos ) {
            while ( pos > -1 && pos < limitPos) {
                int endPos = xslt.indexOf("\"", pos);
                String tag = stripNamespace( xslt.substring(pos,endPos) );
                if ( tag.equals(tagName)  ) {
                    return true;
                }
                pos = xslt.indexOf(matchKey, endPos);
                if ( pos > -1 ) {
                    pos += matchKey.length();
                }
            }
        }
        
        // now see if it has been singly excluded. Valid search range is between 
        // single exclusion marker and breaks marker. Final all instances
        // of 'match="' and see if the tag name matches
        final String strOccurrence = Integer.toString(occurence);
        pos = xslt.indexOf( single );
        limitPos = xslt.indexOf( breaks );
        pos = xslt.indexOf(matchKey, pos)+matchKey.length();
        if ( pos > limitPos ) {
            return false;
        }
        while ( pos > -1 && pos < limitPos) {
            int endPos = xslt.indexOf("\"", pos);
            String tag = stripNamespace( xslt.substring(pos,endPos) );
            if ( tag.equals(tagName)  ) {
                // found a match. See if the specified occurrence is excluded
                int testPos = xslt.indexOf(testKey, pos)+testKey.length();
                int endTest = xslt.indexOf("\"", testPos);
                String conditions = xslt.substring(testPos, endTest);
                
                // strip out everything by a space separated list of occurrences
                // generate a set and see if the taget number is present
                conditions = conditions.replaceAll("(\\$count\\s+!=\\s+|\\s+and\\s+)", " ").trim();
                Set<String> nums = new HashSet<String>( Arrays.asList(conditions.split(" ")) );
                if ( nums.contains( strOccurrence) ) {
                    return true;
                }
            }
            pos = xslt.indexOf(matchKey, endPos);
            if ( pos > -1 ) {
                pos += matchKey.length();
            }
        }
        
        return false;
    }
    
    private String stripNamespace( final String tagName ) {
        if ( tagName.contains("*[")  ) {
            int pos = tagName.lastIndexOf("']");
            return tagName.substring(16,pos);
        } else if ( tagName.contains(":") ) {
            return tagName.split(":")[1];
        }
        return tagName;
    }
    private String getWildcardName( final String name ) {
        return "*[local-name()='"+name+"']";
    }
    
    /**
     * REturns TRUE if all occurrences of this tag have linebreaks
     * @param tagName
     * @return
     */
    public boolean hasLineBreak( final String tagName ) {
        int pos = xslt.indexOf("<!--breaks-->");
        int limitPos = xslt.indexOf("<xsl:template match=\"text()\">");
        pos = xslt.indexOf("match=\"", pos)+7;
        if ( pos > limitPos ) {
            return false;
        }
        int endPos = xslt.indexOf("\"", pos);
        String tags = xslt.substring(pos,endPos);
        if ( tags.equals("*")) {
            return true;
        }
        String[] tagArray = tags.split("\\|");
        for ( int i=0; i<tagArray.length; i++) {
            String tag = stripNamespace(tagArray[i]);
            if ( tag.equals(tagName) ) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Strip out all single exclusions from this template
     */
    public void stripSingleExclusions() {
        final String single = "<!--single-exclusions-->";
        int pos = this.xslt.indexOf(single)+single.length();
        int endPos = this.xslt.indexOf("<!--breaks-->");
        // +5 to preserve linefeed and formatting spaces
        this.xslt = this.xslt.substring(0,pos+5)+this.xslt.substring(endPos);  
    }
}
