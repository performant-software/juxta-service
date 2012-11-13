package org.juxtasoftware.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import net.java.textilej.parser.MarkupParser;
import net.java.textilej.parser.builder.HtmlDocumentBuilder;
import net.java.textilej.parser.markup.mediawiki.MediaWikiDialect;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.FileWriterWithEncoding;

public class WikiTextUtils {
    
    public static final File toTxt( InputStream wikiStream ) throws IOException {
        
        // first strip <ref tags
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

                
                if ( strippingRef ) {
                    if ( line.contains("</ref>") ) {
                        int end = line.indexOf("</ref>");
                        line = line.substring(end+6);
                        strippingRef = false;
                    }
                } 
                
                if ( strippingRef == false) {
                    
                    stripCitationNeeded(line);
                    stripFile(line);
                    
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
                }
                
                if ( line.trim().length() > 0 ) {
                    line += "\n";
                    osw.write(line);
                }
            }
        }
        IOUtils.closeQuietly(osw);
        
        
        // to html
        File html = File.createTempFile("html", "dat");
        FileWriterWithEncoding fw = new FileWriterWithEncoding(html, "UTF-8");
        HtmlDocumentBuilder builder = new HtmlDocumentBuilder(fw);
        builder.setEmitAsDocument(false);
        MarkupParser parser = new MarkupParser(new MediaWikiDialect());
        parser.setBuilder(builder);
        InputStream fis = new FileInputStream( stripped );
        parser.parse( new InputStreamReader(fis) );
        IOUtils.closeQuietly(fw);
        stripped.delete();
        
        // to txt
        HtmlUtils.strip(html);
        return HtmlUtils.toTxt( new FileInputStream(html) );

    }

    private static void stripFile(String line) {
        if ( line.contains("[[File:") == false) {
            return;
        }
        
    }

    private static void stripCitationNeeded(String line) {
        while ( line.contains("{{Citation needed")) {
            int start = line.indexOf("{{Citation needed");
            int end = line.indexOf("}}", start);
            line = line.substring(0, start) + line.substring(end+2);
        }
    }

}
