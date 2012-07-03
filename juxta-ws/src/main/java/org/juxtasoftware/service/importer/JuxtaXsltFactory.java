package org.juxtasoftware.service.importer;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.service.importer.XmlTemplateParser.TemplateInfo;
import org.juxtasoftware.util.NamespaceExtractor.NamespaceInfo;
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
    
    private static final String BASIC_XSLT = "xslt/basic-strip-ws.xslt";
    private static final String SINGLE_XSLT = "xslt/single-exclusion.xslt";
    private static final String GLOBAL_XSLT = "xslt/global-exclusion.xslt";
    private static final String BREAKS_XSLT = "xslt/breaks.xslt";
    
    /**
     * Get the generic XSLT template as a string
     * @return
     * @throws IOException
     */
    public static String getGenericTemplate() throws IOException {
        return IOUtils.toString( ClassLoader.getSystemResourceAsStream(BASIC_XSLT), "utf-8");
    }
    
    /**
     * Get the single exclusion XSLT template fragment as a string
     * @return
     * @throws IOException
     */
    public static String getSingleExclusionTemplate() throws IOException {
        return IOUtils.toString( ClassLoader.getSystemResourceAsStream(SINGLE_XSLT), "utf-8");
    }
    
    /**
     * Get the global exclusion XSLT template fragment as a string
     * @return
     * @throws IOException
     */
    public static String getGlobalExclusionTemplate() throws IOException {
        return IOUtils.toString( ClassLoader.getSystemResourceAsStream(GLOBAL_XSLT), "utf-8");
    }
    
    /**
     * Get the linebreaks XSLT fragment as a string
     * @return
     * @throws IOException
     */
    public static String getBreaksTemplate() throws IOException {
        return IOUtils.toString( ClassLoader.getSystemResourceAsStream(BREAKS_XSLT), "utf-8");
    }
    
    public JuxtaXslt create( final Long workspaceId, final String name ) throws IOException {
        String xslt = JuxtaXsltFactory.getGenericTemplate();
        xslt = xslt.replaceAll("\\{LINEBREAK\\}", "&#10;");
        
        // for this generic template, no tags are referred to directly (just *),
        // so no namespace info is needed. 
        xslt = xslt.replaceAll("\\{NAMESPACE\\}", "");
        
        // Wildcard notes and pb tags so they always get the specialized tag handling
        xslt = xslt.replaceAll("\\{NOTE\\}", wildCardNamespace("note"));
        xslt = xslt.replaceAll("\\{PB\\}", wildCardNamespace("pb"));

        String breaksXslt = JuxtaXsltFactory.getBreaksTemplate();
        breaksXslt = breaksXslt.replaceAll("\\{LB_LIST\\}", "*");
        int breakPos = xslt.indexOf("<!--breaks-->")+13;
        xslt = xslt.substring(0, breakPos)+"\n    "+breaksXslt+xslt.substring(breakPos);
        
        JuxtaXslt jxXslt = new JuxtaXslt();
        jxXslt.setName(name+"-transform-"+System.currentTimeMillis());
        jxXslt.setWorkspaceId( workspaceId );
        jxXslt.setXslt(xslt);
        Long id = this.xsltDao.create(jxXslt);
        jxXslt.setId(id);
        return jxXslt;
    }
    
    /**
     * Create a new instance of a JuxtaXslt based on setings extracted from a desktop-derived template XML file
     * Note that these types of files
     * 
     * @param workspaceId
     * @param name
     * @param info
     * @return
     * @throws IOException
     */
    public JuxtaXslt createFromTemplateInfo(final Long workspaceId, final String name, final TemplateInfo info, final NamespaceInfo namespace ) throws IOException {
        JuxtaXslt jxXslt = new JuxtaXslt();
        jxXslt.setName(name+"-transform-"+System.currentTimeMillis());
        jxXslt.setWorkspaceId( workspaceId );
        String xslt = JuxtaXsltFactory.getGenericTemplate();
        
        // stuff the correct namespace info in the declaration and setup
        // special note/pb handling
        if ( namespace.hasNoPrefix() ) {
            xslt = xslt.replaceAll("\\{NAMESPACE\\}", "");
            xslt = xslt.replaceAll("\\{NOTE\\}", "note" );
            xslt = xslt.replaceAll("\\{PB\\}", "pb" );
        } else {
            xslt = xslt.replaceAll("\\{NAMESPACE\\}", namespace.toString());
            xslt = xslt.replaceAll("\\{NOTE\\}", namespace.getPrefix()+":note" );
            xslt = xslt.replaceAll("\\{PB\\}", namespace.getPrefix()+":pb" );
        }
        
        // for serverside transforms, the linefeed can be an actual linefeed char
        xslt = xslt.replaceAll("\\{LINEBREAK\\}", "&#10;");
        
        // get the template exclusion / linebreak info and insert to XSLT
        if ( info.getLineBreaks().size() > 0 ) {
            StringBuilder lb = new StringBuilder();
            for ( String tag : info.getLineBreaks() ) {
                if ( lb.length() > 0 ) {
                    lb.append("|");
                }
                lb.append( namespace.addNamespacePrefix(tag) );
            }
            
            String breaksXslt = IOUtils.toString( ClassLoader.getSystemResourceAsStream("xslt/breaks.xslt"), "utf-8");
            breaksXslt = breaksXslt.replaceAll("\\{LB_LIST\\}", lb.toString());
            int breakPos = xslt.indexOf("<!--breaks-->")+13;
            xslt = xslt.substring(0, breakPos)+"\n    "+breaksXslt+xslt.substring(breakPos);
        }
        
        // all desktop excludes are global excludes. Add them to the xslt
        StringBuilder excludes = new StringBuilder();
        for ( String tag : info.getExcludes() ) {
            excludes.append("\n    <xsl:template match=\"");
            excludes.append( namespace.addNamespacePrefix(tag) ).append("\"/>"); 
        }
        final String key = "<!--global-exclusions-->";
        int pos = xslt.indexOf(key)+key.length();
        xslt = xslt.substring(0, pos) + excludes.toString() + xslt.substring(pos);
        if ( namespace.isDefault() ) {
            jxXslt.setDefaultNamespace(namespace.getPrefix());
        }
        jxXslt.setXslt(xslt);
        Long id = this.xsltDao.create(jxXslt);
        jxXslt.setId(id);
        return jxXslt;
    }
    
    private String wildCardNamespace( final String tag ) {
        return "*[local-name()='"+tag+"']";
    }
}
