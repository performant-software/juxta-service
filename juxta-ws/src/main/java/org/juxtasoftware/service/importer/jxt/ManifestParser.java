package org.juxtasoftware.service.importer.jxt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.commons.io.IOUtils;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.ComparisonSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parse juxta manifest.xml and parse out a list of sources present. Data 
 * reported back for sources includes the temp file containing the raw 
 * source document and the parse template guid it uses to be transformed
 * into a witness.
 * 
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ManifestParser extends DefaultHandler  {
    @Autowired private ComparisonSetDao comparisonSetDao;

    private File sessionData;
    private ComparisonSet comparisonSet;
    private List<SourceInfo> sources = null;
    private StringBuilder filePathBuilder;
    
    private static final Logger LOG = LoggerFactory.getLogger(ManifestParser.class);

    public ComparisonSet parse(final ComparisonSet set, File sessionData, File file) throws IOException, SAXException {
        this.sessionData = sessionData;
        this.comparisonSet = set;
        this.sources = new ArrayList<SourceInfo>();
        this.filePathBuilder = null;
        Util.saxParser().parse(file, this);
        return this.comparisonSet;
    }
    
    public List<SourceInfo> getSources() {
        return this.sources;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if ( qName.equals("juxta") ) {
            // Only support versions 1.6.x
            String version = attributes.getValue("version");
            if ( version == null ) {
                LOG.error("Missing version information");
                throw new SAXException("Missing Version!");
            } else if ( version.indexOf("1.6") == -1 && version.indexOf("1.7") == -1) {
                LOG.error("Unsupported manifest version: " + version );
                throw new SAXException("Unsupported Version!");
            }
        } else if ("comparison-set".equals(qName)) {
            // Update settings to match the file
            this.comparisonSetDao.updateCollatorConfig(this.comparisonSet, new CollatorConfig(
                    Util.defaultBoolean(attributes.getValue("filter-whitespace"), true),
                    Util.defaultBoolean(attributes.getValue("filter-punctuation"), true),
                    Util.defaultBoolean(attributes.getValue("filter-case"), true)));
            
        } else if ("file".equals(qName)) {
         
            this.filePathBuilder = new StringBuilder();
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
  
        if ("file".equals(qName)) {
            File comparandFile = new File(this.sessionData, this.filePathBuilder.toString());
            if ( comparandFile.canRead() == false ) {
                throw new SAXException("Invalid file "+this.filePathBuilder+" in manifest");
            }
            
            // Only care about 4 bits: 
            //  short-title, juxta-doc-reference, parseTemplate and acceptedRevisions
            SourceInfo sourceInfo = new SourceInfo();
            BufferedReader rdr = null;
            try {
                rdr = new BufferedReader( new FileReader(comparandFile) );
                while (true) {
                    String line = rdr.readLine();
                    if ( line == null ) {
                        break;
                    } else {
                        if (line.contains("<short-title>")) {
                            sourceInfo.title =  extractValue(line, "<short-title>", '<');
                        } else if (line.contains("<juxta-doc-reference")) {
                            String val = extractValue(line, "filename=\"", '"');
                            if ( val == null ) {
                                rdr.close();
                                throw new SAXException(this.filePathBuilder+" is missing filename");
                            }
                            sourceInfo.srcFile = new File(comparandFile.getParentFile(), val);
                        } else if ( line.contains("parseTemplate") ) {
                            String val = extractValue(line, "<parseTemplate>", '<');
                            if ( val == null ) {
                                rdr.close();
                                throw new SAXException(this.filePathBuilder+" is missing parse template");
                            }
                            sourceInfo.templateGuid = val;
                        } else if (line.contains("<acceptedRevisions>")) {
                            sourceInfo.revisions =  extractValue(line, "<acceptedRevisions>", '<');
                        }
                    }
                }
                this.sources.add(sourceInfo);
            } catch (IOException e) {
                
            } finally {
                IOUtils.closeQuietly(rdr);
            }
        }
    }
    
    private final String extractValue(final String data, final String key, final char ender ) {
        int pos = data.indexOf(key);
        if ( pos > -1 ) {
            pos = pos + key.length();
            int end = data.indexOf(ender, pos);
            if ( end > -1 ) {
                String out = data.substring(pos, end).trim();
                return out;
            }
        }
        return null;
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {

        if (this.filePathBuilder != null) {
            this.filePathBuilder.append(ch, start, length);
        }
    }

    
    /**
     * Information collected about a source during JXT parsing
     */
    public static class SourceInfo {
        private File srcFile;
        private String templateGuid;
        private String title;
        private String revisions;
        
        public final File getSrcFile() {
            return srcFile;
        }
        public final String getTitle() {
            if ( this.title == null ) {
                return this.srcFile.getName();
            }
            return this.title;
        }
        public void setTitle( final String newTitle ) {
            this.title = newTitle;
        }
        public final String getTemplateGuid() {
            return templateGuid;
        } 
        public boolean hasRevisions() {
            return (this.revisions != null && this.revisions.length()>0);
        }
        public List<Integer> getAcceptedRevsions() {
            List<Integer> revs = new ArrayList<Integer>();
            StringTokenizer st = new StringTokenizer(this.revisions, ",");
            while (st.hasMoreTokens() ) {
                revs.add( Integer.parseInt(st.nextToken()));
            }
            return revs;
        }
        public final boolean hasTemplate() {
            return (this.templateGuid.length() > 0);
        }
        public boolean isMatch( final String name ) {
            return this.srcFile.getName().equals(name);
        }
    }
}
