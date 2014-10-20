package org.juxtasoftware.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

import org.apache.commons.io.IOUtils;
import org.juxtasoftware.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HtmlUtils {

    private static final Logger LOG = LoggerFactory.getLogger( Constants.WS_LOGGER_NAME );
    
    /**
     * Transform source html stream into a plain text file
     * @param htmlStream
     * @return
     */
    public static File toTxt( InputStream htmlStream ) throws IOException{
        
        File out = File.createTempFile("totxt", "dat");
        FileOutputStream fos = new FileOutputStream(out);
        final  OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
        HTMLEditorKit.ParserCallback callback = new HTMLEditorKit.ParserCallback() {
            
            @Override
            public void handleText(char[] data, int pos) {
                try {
                    osw.append(java.nio.CharBuffer.wrap(data) );
                } catch (IOException e) {
                    LOG.error("Error writing HTML text data", e);
                }
            }
            
            @Override
            public void handleEndTag(HTML.Tag t, int pos) {
                if ( HtmlUtils.isLinefeedTag(t)  ) {
                    try {
                        osw.append( "\n" );
                        if ( t.equals(HTML.Tag.P)) {
                            // double break p tags to get space between paragraphs
                            osw.append( "\n" );
                        }
                    } catch (IOException e) {
                        LOG.error("Error writing simple HTML tag linebreak", e);
                    }
                }
            }
            
            @Override
            public void handleSimpleTag(HTML.Tag t, MutableAttributeSet a, int pos)  {
                if ( t.equals(HTML.Tag.BR) || t.equals(HTML.Tag.HR)  ) {
                    try {
                        osw.append( "\n" );
                    } catch (IOException e) {
                        LOG.error("Error writing simple HTML tag linebreak", e);
                    }
                }
            }
        };
        new ParserDelegator().parse( new InputStreamReader(htmlStream, "UTF-8"), callback, false);
        IOUtils.closeQuietly(osw);
        return out;
    }
    
    private static boolean isLinefeedTag( HTML.Tag tag ) {
        final List<HTML.Tag> breakers = new ArrayList<HTML.Tag>();
        breakers.add(HTML.Tag.DIV);
        breakers.add(HTML.Tag.H1);
        breakers.add(HTML.Tag.H2);
        breakers.add(HTML.Tag.H3);
        breakers.add(HTML.Tag.H4);
        breakers.add(HTML.Tag.H5);
        breakers.add(HTML.Tag.LI);
        breakers.add(HTML.Tag.P);
        breakers.add(HTML.Tag.PRE);
        breakers.add(HTML.Tag.TH);
        breakers.add(HTML.Tag.TR);
        return breakers.contains(tag);
    }
    
    /**
     * Strip header, javascript and css from HTML source file
     * @param srcFile
     */
    public static void strip( File srcFile ) throws IOException {
        FileInputStream fis = new FileInputStream(srcFile);
        InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
        BufferedReader r = new BufferedReader( isr );
        File out = File.createTempFile("stripped", "dat");
        FileOutputStream fos = new FileOutputStream(out);
        OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
        boolean strippingHead = false;
        boolean strippingScript = false;
        boolean strippingCss = false;
        while (true) {
            String line = r.readLine();
            if ( line == null ) {
                break;
            } else {
                String lcLine = line.toLowerCase();
                
                if (strippingHead ) {
                    int endHead = lcLine.indexOf("</head>");
                    if ( endHead > -1 ) {
                        line = line.substring(endHead+7);
                        strippingHead = false;
                    } else {
                        line = "";
                    }
                } else if (strippingScript ) {
                    int end = lcLine.indexOf("</script>");
                    if ( end > -1 ) {
                        line = line.substring(end+9);
                        strippingScript = false;
                    } else {
                        line = "";
                    }
                } else if (strippingCss ) {
                    int end = lcLine.indexOf("</style>");
                    if ( end > -1 ) {
                        line = line.substring(end+8);
                        strippingCss = false;
                    } else {
                        line = "";
                    }
                }else {
                
                    if ( lcLine.contains("<head")) {
                        int headPos = lcLine.indexOf("<head");
                        int endHead = lcLine.indexOf("</head>");
                        if ( endHead > -1 ) {
                            line = line.substring(0,headPos) + line.substring(endHead+7);
                        } else {
                            line = line.substring(0, headPos);
                            strippingHead = true;
                        }
                    } else if ( lcLine.contains("<script")) {
                        int start = lcLine.indexOf("<script");
                        int end = lcLine.indexOf("</script>");
                        if ( end > -1 ) {
                            line = line.substring(0,start) + line.substring(end+9);
                        } else {
                            line = line.substring(0, start);
                            strippingScript = true;
                        }
                    } else if ( lcLine.contains("<style")) {
                        int start = lcLine.indexOf("<style");
                        int end = lcLine.indexOf("</style>");
                        if ( end > -1 ) {
                            line = line.substring(0,start) + line.substring(end+8);
                        } else {
                            line = line.substring(0, start);
                            strippingCss = true;
                        }
                    }
                }
                
                if ( line.trim().length() > 0 ) {
                    line += "\n";
                    osw.write(line);
                }
            }
        }
        IOUtils.closeQuietly( r );
        IOUtils.closeQuietly( osw );
        
        // copy the stripped file back over the original
        IOUtils.copy(new FileInputStream(out), new FileOutputStream(srcFile));
        out.delete();
        
    }
}
