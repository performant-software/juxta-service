package org.juxtasoftware.model;

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
    
    public void addExclusion(String tag) {
        boolean hasNamespace = this.xslt.contains("xmlns:ns");
        String qName = tag;
        if ( hasNamespace ) {
            qName = "ns:"+tag;
        }
        final String marker = "<!--global-exclusions-->";
        int pos = this.xslt.indexOf(marker)+marker.length();
        String ex = "\n    <xsl:template match=\""+qName+"\"/>";
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
        String newTags = tags.replaceAll("("+qName+"\\||\\|"+qName+")","");
        
        // sub the new tags list back into the xslt
        this.xslt = this.xslt.replace(tags, newTags);
    }
    
    /**
     * Returns TRUE if this tag has been globally excluded from the source
     * @param tagName
     * @return
     */
    public boolean isExcluded( final String tagName ) {
        int pos = xslt.indexOf("<!--global-exclusions-->");
        int limitPos = xslt.indexOf("<!--single-exclusions-->");
        pos = xslt.indexOf("match=\"", pos)+7;
        if ( pos > limitPos ) {
            return false;
        }
        while ( pos > -1 && pos < limitPos) {
            int endPos = xslt.indexOf("\"", pos);
            String tag = stripNamespace( xslt.substring(pos,endPos) );
            if ( tag.equals(tagName)  ) {
                return true;
            }
            pos = xslt.indexOf("match=\"", endPos)+7;
        }
        return false;
    }
    
    private String stripNamespace( final String tagName ) {
        if ( tagName.contains(":") ) {
            return tagName.split(":")[1];
        }
        return tagName;
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
        return tags.contains(tagName);
    }
}
