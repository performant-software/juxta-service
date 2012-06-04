package org.juxtasoftware.service.importer;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.service.importer.XmlTemplateParser.TemplateInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Factory for creating JuxtaXslt instances
 * @author loufoster
 */
@Component
@Scope(BeanDefinition.SCOPE_SINGLETON)
public final class JuxtaXsltFactory {
    @Autowired private JuxtaXsltDao xsltDao;
    
    /**
     * Create a new instance of a JuxtaXslt based on setings extracted from a desktop template XML file
     * 
     * @param workspaceId
     * @param name
     * @param info
     * @return
     * @throws IOException
     */
    public JuxtaXslt create(final Long workspaceId, final String name, final TemplateInfo info ) throws IOException {
        JuxtaXslt jxXslt = new JuxtaXslt();
        jxXslt.setName(name+"-transform");
        jxXslt.setWorkspaceId( workspaceId );
        String xslt = IOUtils.toString( ClassLoader.getSystemResourceAsStream("xslt/basic.xslt"), "utf-8");

        // desktop has no concept of namespaces. Blank them
        // out here, and specify WILDACARD namespace prefix everywhere
        xslt = xslt.replaceAll("\\{NAMESPACE\\}", "");
        xslt = xslt.replaceAll("\\{NOTE\\}", wildCardNamespace("note") );
        xslt = xslt.replaceAll("\\{PB\\}", wildCardNamespace("pb") );
        xslt = xslt.replaceAll("\\{LINEBREAK\\}", "&#10;");
        
        // get the template exclusion / linebreak info and insert to XSLT
        if ( info.getLineBreaks().size() > 0 ) {
            StringBuilder lb = new StringBuilder();
            for ( String tag : info.getLineBreaks() ) {
                if ( lb.length() > 0 ) {
                    lb.append("|");
                }
                lb.append( wildCardNamespace(tag) );
            }
            xslt = xslt.replaceAll("\\{LB_LIST\\}", lb.toString());
        } else {
            xslt = xslt.replaceAll("\\{LB_LIST\\}", "*" );
        }
        
        // all desktop excludes are global excludes. Add them to the xslt
        StringBuilder excludes = new StringBuilder();
        for ( String tag : info.getExcludes() ) {
            excludes.append("\n    <xsl:template match=\"");
            excludes.append( wildCardNamespace(tag) ).append("\"/>"); 
        }
        final String key = "<!--global-exclusions-->";
        int pos = xslt.indexOf(key)+key.length();
        xslt = xslt.substring(0, pos) + excludes.toString() + xslt.substring(pos);
       
        jxXslt.setXslt(xslt);
        Long id = this.xsltDao.create(jxXslt);
        jxXslt.setId(id);
        return jxXslt;
    }
    
    private String wildCardNamespace( final String tag ) {
        return "*[local-name()='"+tag+"']";
    }
}
