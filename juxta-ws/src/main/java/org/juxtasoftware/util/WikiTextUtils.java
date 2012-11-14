package org.juxtasoftware.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.FileWriterWithEncoding;
import org.eclipse.mylyn.wikitext.core.parser.MarkupParser;
import org.eclipse.mylyn.wikitext.core.parser.builder.HtmlDocumentBuilder;
import org.eclipse.mylyn.wikitext.mediawiki.core.MediaWikiLanguage;
//import org.eclipse.mylyn.wikitext.core.parser.MarkupParser;
//import org.eclipse.mylyn.wikitext.core.parser.builder.HtmlDocumentBuilder;
//import org.eclipse.mylyn.wikitext.mediawiki.core.MediaWikiLanguage;
//import org.sweble.wikitext.engine.utils.SimpleWikiConfiguration;

public class WikiTextUtils {
    
    public static final File toTxt( InputStream wikiStream ) throws IOException {
        
        // first strip markup that does not translate correctly into html or plain text, and make
        // the output difficult to read/understand. Bad markup:
        //    <ref></ref>, <ref/>
        //    [[Image: ... ]]
        //    [[File: ... ]]
        //    {{Citation needed}}
        InputStreamReader isr = new InputStreamReader(wikiStream, "UTF-8");
        BufferedReader r = new BufferedReader( isr );
        File stripped = File.createTempFile("stripped", "dat");
        FileOutputStream fos = new FileOutputStream(stripped);
        OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
        
        boolean strippingRef = false;
        while (true) {
            String line = r.readLine();
            if ( line == null ) {
                break;
            } else {
                
                line = stripCitationNeeded(line);
                line = stripTag("[[File:", line);
                line = stripTag("[[Image:", line);

                if ( strippingRef ) {
                    if ( line.contains("</ref>") ) {
                        int end = line.indexOf("</ref>");
                        line = line.substring(end+6);
                        strippingRef = false;
                    }
                } 
                
                if ( strippingRef == false) {
                    // From [[Category:: on the file just sets up
                    // links at the page footer. Doen't translate correctly to text
                    // so stop here
                    if ( line.contains("[[Category:") || line.contains("[[ar:") || line.contains("{{DEFAULTSORT:") ) {
                        break;
                    }
                    while ( line.contains("<ref") ) {
                        
                        int start = line.indexOf("<ref");
                        int end = line.indexOf("</ref>", start);
                        int singleEnd = line.indexOf("/>", start);
                        int tagEnd  = -1;
                        int endOffset = 0;
                        if ( singleEnd > -1 ) {
                            endOffset = 2;
                            tagEnd = singleEnd;
                            if ( end > -1 ) {
                                if ( end < singleEnd ) {
                                    tagEnd = end;
                                    endOffset = 6;
                                }
                            } 
                        } else if ( end > -1 ) {
                            tagEnd = end;
                            endOffset = 6;
                        }
                        
                        if (tagEnd > -1 ) {
                            String endBit = line.substring(tagEnd+endOffset);
                            line = line.substring(0,start) + endBit;
                        } else {
                            line = line.substring(0,start);
                            strippingRef = true;
                            break;
                        }
                    }
                    
                    if ( line.trim().length() > 0 ) {
                        line += "\n";
                        osw.write(line);
                    }
                }
            }
        }
        IOUtils.closeQuietly(osw);
        
        // Next, turn this to html using textile-j (this one does the best job of those I tried out)
        File html = File.createTempFile("html", "dat");
        FileWriterWithEncoding fw = new FileWriterWithEncoding(html, "UTF-8");
        HtmlDocumentBuilder builder = new HtmlDocumentBuilder(fw);
        builder.setEmitAsDocument(false);
        MarkupParser parser = new MarkupParser(new MediaWikiLanguage());
        parser.setBuilder(builder);
        InputStream fis = new FileInputStream( stripped );
        parser.parse( new InputStreamReader(fis) );
        IOUtils.closeQuietly(fw);
        stripped.delete();
        
        // Finally, turn the html into plain text
        HtmlUtils.strip(html);
        return HtmlUtils.toTxt( new FileInputStream(html) );

    }

    private static String stripTag(final String tagStart, String line) {
        if ( line.contains(tagStart) == false) {
            return line;
        }
        
        int start = line.indexOf(tagStart);
        int depth = 1;
        StringBuilder buf= new StringBuilder();
        for (int i=start+7; i<line.length(); i++) {
            buf.append(line.charAt(i));
            if  (buf.indexOf("[[") > -1) {
                depth++;
                buf = new StringBuilder();
            } else if ( buf.indexOf("]]") > -1 ) {
                depth--;
                if ( depth == 0) {
                    line = line.substring(0, start) + line.substring(i+1);
                    break;
                } else {
                    buf = new StringBuilder();
                }
            }
        }
        return line;
    }

    private static String stripCitationNeeded(String line) {
        while ( line.contains("{{Citation needed")) {
            int start = line.indexOf("{{Citation needed");
            int end = line.indexOf("}}", start);
            line = line.substring(0, start) + line.substring(end+2);
        }
        return line;
    }

}
