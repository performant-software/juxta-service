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
        String xslt = JuxtaXsltFactory.getGenericTemplate();

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
            
            String breaksXslt = IOUtils.toString( ClassLoader.getSystemResourceAsStream("xslt/breaks.xslt"), "utf-8");
            breaksXslt = breaksXslt.replaceAll("\\{LB_LIST\\}", lb.toString());
            int breakPos = xslt.indexOf("<!--breaks-->")+13;
            xslt = xslt.substring(0, breakPos)+"\n    "+breaksXslt+xslt.substring(breakPos);
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
