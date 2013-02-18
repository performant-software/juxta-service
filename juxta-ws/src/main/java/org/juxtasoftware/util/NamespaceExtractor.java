package org.juxtasoftware.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;

/**
 * Extract all namespace info from an XML source
 * @author loufoster
 *
 */
public final class NamespaceExtractor {
    
    public enum XmlType {GENERIC, TEI, RAM, JUXTA};
    
    /**
     * Scan the XML source and extract all of the namespace information
     * 
     * @param sourceReader
     * @return
     * @throws IOException
     */
    public static Set<NamespaceInfo> extract( final Reader sourceReader ) throws IOException {
        BufferedReader br = new BufferedReader( sourceReader );
        Set<NamespaceInfo> namespaces = new HashSet<NamespaceInfo>();
        try {
        final String defaultNs = "xmlns=\"";
        final String noNamespace = ":noNamespaceSchemaLocation=\"";
        final String ns = "xmlns:";
        final String commentStart = "<!--";
        final String commentEnd = "-->";
        boolean inComment = false;
        while (true) {
            String line = br.readLine();
            if ( line == null ) {
                break;
            } else {
                line = line.trim();
                if ( inComment ) {
                    if ( line.contains(commentEnd)) {
                        line = line.substring(line.indexOf(commentEnd)+3).trim();
                        inComment = false;
                    } else {
                        continue;
                    }
                }
                
                if ( line.contains(commentStart) ) {
                    if ( line.contains(commentEnd)) {
                        String end =  line.substring(line.indexOf(commentEnd)+3).trim();
                        line = line.substring(0, line.indexOf(commentStart)) + end;
                    } else {
                        line = line.substring(0, line.indexOf(commentStart));
                        inComment = true;
                    }
                }
                
                if (line.length() == 0 ) {
                    continue;
                }
                
                // default namespace?
                if ( line.contains(defaultNs) ) {
                    int pos = line.indexOf(defaultNs)+defaultNs.length();
                    int end = line.indexOf('"', pos);
                    NamespaceInfo info = NamespaceInfo.createDefaultNamespace( line.substring(pos,end) );
                    namespaces.add( info );
                } 
                
                // no-namespace loc?
                if ( line.contains(noNamespace) ) {
                    int pos = line.indexOf(noNamespace)+noNamespace.length();
                    int end = line.indexOf('"', pos);
                    namespaces.add( NamespaceInfo.createNoPrefixNamespace(line.substring(pos,end)) );             
                } 
                
                // specifc namespace(s)?
                if ( line.contains(ns) ) {                
                    int pos = line.indexOf(ns)+ns.length();
                    while ( pos > -1  ) {
                        int nsPos = pos;
                        int nsEndPos = line.indexOf("=\"", pos);
                        pos = nsEndPos+2;
                        int end = line.indexOf('"', pos);
                        String url = line.substring(pos,end);
                        if ( url.contains("XMLSchema-instance") == false ) {
                            String prefix = line.substring(nsPos,nsEndPos);
                            namespaces.add( NamespaceInfo.create(prefix, url) );
                        }
                        int newPos = line.indexOf(ns, end);
                        if (newPos > -1 ) {
                            pos = newPos+6;
                        } else {
                            pos = -1;
                        }
                    }
                }
            }
        }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return namespaces;
    }
    
    /**
     * Examine the namespace declarations of this source and attempt to determine
     * the XML type: TEI, RAM or Generic
     * 
     * @param srcReader
     * @return
     */
    public static XmlType determineXmlType(final Reader srcReader) {
        BufferedReader  br = new BufferedReader(srcReader);
        int lineCnt = 0;
        XmlType type = XmlType.GENERIC;
        try {
            while ( true ) {
                String line = br.readLine();
                if ( line == null ) {
                    break;
                } else {
                    if ( line.contains("http://www.tei-") || line.contains("tei2.dtd") || 
                         line.contains("teiCorpus") || line.contains("DOCTYPE TEI") || line.contains("<TEI")) {
                        type = XmlType.TEI;
                        break;
                    } else if ( line.contains("ram.xsd")) {
                        type = XmlType.RAM;
                        break;
                    }
                    else if ( line.contains("juxta-document")) {
                        type = XmlType.JUXTA;
                        break;
                    }

                    // if we haven't found it  in 20 lines.. give up
                    lineCnt++;
                    if (lineCnt > 20 ) {
                        break;
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
    
    /**
     * Namespace information.
     * NOTES: 
     *      a default namespace does not have a prefix in the XML, but must have one in the xslt
     *      some xml docs include noNamespaceSchemaLocation. these have no prefix in XML nor XSLT
     * @author loufoster
     *
     */
    public static class NamespaceInfo {
        private String prefix;
        private String url;
        private boolean noPrefix;
        private boolean isDefault = false;
        
        public static NamespaceInfo createBlankNamespace()  {
            return new NamespaceInfo();
        }
        public static NamespaceInfo createDefaultNamespace( final String url)  {
            NamespaceInfo ns = new NamespaceInfo();
            ns.isDefault = true;
            ns.noPrefix = false;
            ns.url = url;
            return ns;
        }
        public static NamespaceInfo createNoPrefixNamespace( final String url)  {
            NamespaceInfo ns = new NamespaceInfo();
            ns.isDefault = false;
            ns.noPrefix = true;
            ns.url = url;
            return ns;
        }
        public static NamespaceInfo create( final String prefix, final String url)  {
            NamespaceInfo ns = new NamespaceInfo();
            ns.isDefault = false;
            ns.noPrefix = false;
            ns.url = url;
            ns.prefix = prefix;
            return ns;
        }

        private NamespaceInfo( ) {
            this.isDefault = false;
            this.noPrefix = true;
            this.url = "";
            this.prefix = "jxt";
        }

        public void setDefaultPrefix(String string) {
            this.prefix = string;
            this.isDefault = true;
        }
        public boolean hasNoPrefix() {
            return this.noPrefix;
        }
        
        public boolean isDefault() {
            return this.isDefault;
        }
        public String getPrefix() {
            if ( hasNoPrefix() ) {
                return "";
            }
            return this.prefix;
        }
        
        public String getUrl() {
            return this.url;
        }
        
        public String toString() {
            String p = getPrefix();
            if ( p.length() > 0) {
                return "xmlns:"+getPrefix()+"=\""+getUrl()+"\"";
            }
            return "xmlns=\""+getUrl()+"\"";
        }
        
        
        public String addNamespacePrefix( final String tag ) {
            if ( hasNoPrefix() == false  ) {
                return getPrefix() +":" + tag;
            } 
            return tag;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((prefix == null) ? 0 : prefix.hashCode());
            result = prime * result + ((url == null) ? 0 : url.hashCode());
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
            NamespaceInfo other = (NamespaceInfo) obj;
            if (prefix == null) {
                if (other.prefix != null)
                    return false;
            } else if (!prefix.equals(other.prefix))
                return false;
            if (url == null) {
                if (other.url != null)
                    return false;
            } else if (!url.equals(other.url))
                return false;
            return true;
        }        
    }
}
