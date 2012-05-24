package org.juxtasoftware.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;

/**
 * Extract all namespace info from an XML source
 * @author loufoster
 *
 */
public final class NamespaceExtractor {
    
    public static Set<NamespaceInfo> extract( final Reader sourceReader ) throws IOException {
        BufferedReader br = new BufferedReader( sourceReader );
        Set<NamespaceInfo> namespaces = new HashSet<NamespaceInfo>();
        final String defaultNs = "xmlns=\"";
        final String noNamespace = ":noNamespaceSchemaLocation=\"";
        final String ns = "xmlns:";
        while (true) {
            String line = br.readLine();
            if ( line == null ) {
                break;
            } else {
                // default namespace?
                if ( line.contains(defaultNs) ) {
                    int pos = line.indexOf(defaultNs)+defaultNs.length();
                    int end = line.indexOf('"', pos);
                    NamespaceInfo info = new NamespaceInfo(null, line.substring(pos,end));
                    namespaces.add( info );
                } 
                
                // no-namespace loc?
                if ( line.contains(noNamespace) ) {
                    int pos = line.indexOf(noNamespace)+noNamespace.length();
                    int end = line.indexOf('"', pos);
                    NamespaceInfo info = new NamespaceInfo(null, line.substring(pos,end));
                    namespaces.add( info );             
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
                        if ( url.contains("w3.org") == false ) {
                            String prefix = line.substring(nsPos,nsEndPos);
                            namespaces.add( new NamespaceInfo(prefix, url) );
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
        
        return namespaces;
    }
    
    public static class NamespaceInfo {
        private final String prefix;
        private final String url;
        
        public NamespaceInfo( final String prefix, final String url ) {
            this.prefix = prefix;
            this.url = url;
        }
        
        public static String getDefaultPrefix() {
            return "jxd";
        }
        
        public boolean isDefault() {
            return (this.prefix == null);
        }
        public String getPrefix() {
            if ( this.prefix == null ) {
                return getDefaultPrefix();
            }
            return this.prefix;
        }
        
        public String getUrl() {
            return this.url;
        }
        
        public String toString() {
            return "xmlns:"+getPrefix()+"=\""+getUrl()+"\"";
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
