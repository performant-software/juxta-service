package org.juxtasoftware.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import org.apache.commons.io.IOUtils;

public class HtmlUtils {

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
