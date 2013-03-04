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
        boolean strippingTag = false;
        String endMarker = "";
        String startMarker = "";
        int depth = 0;
        boolean extractingQuote = false;
        boolean discardQuoteData = false;
        
        while (true) {
            String line = r.readLine();
            if ( line == null ) {
                break;
            } else {
                line = line.trim();
                if ( strippingTag ) {
                    StringBuilder buf= new StringBuilder();
                    for (int i=0; i<line.length(); i++) {
                        buf.append( line.charAt(i));
                        if ( buf.indexOf(startMarker) > -1 ) {
                            depth++;
                            buf = new StringBuilder();
                        } else if ( buf.indexOf(endMarker) > -1 ) {
                            depth--;
                            if ( depth == 0) {
                                line = line.substring(i+1);
                                strippingTag = false;
                                break;
                            } else {
                                buf = new StringBuilder();
                            }
                        }
                    }
                    if ( strippingTag ) {
                        continue;
                    }
                }
                
                if (line.length() == 0) {
                    continue;
                }
                
                line = stripCitationNeeded(line);
                
                StripResult sr = stripTag("{{", "cite", "}}", line);
                if ( sr.depth != 0 ) {
                    depth = sr.depth;
                    endMarker = "}}";
                    startMarker = "{{";
                    strippingTag = true;
                    continue;
                } else {
                    line = sr.strippedLine;
                }
                
                sr = stripTag("[[", "File:", "]]", line);
                if ( sr.depth != 0 ) {
                    depth = sr.depth;
                    endMarker = "]]";
                    startMarker = "[[";
                    strippingTag = true;
                    continue;
                } else {
                    line = sr.strippedLine;
                }
                
                sr = stripTag("[[", "Image:", "]]", line);
                if ( sr.depth != 0 ) {
                    depth = sr.depth;
                    endMarker = "]]";
                    startMarker = "[[";
                    strippingTag = true;
                    continue;
                } else {
                    line = sr.strippedLine;
                }
                
                // cquotes
                if ( discardQuoteData ) {
                    if ( line.indexOf("}}") > -1 ) {
                        int pos = line.indexOf("}}");
                        discardQuoteData = false;
                        line = line.substring(pos+2);
                    }
                } else if ( extractingQuote ) {
                    if ( line.indexOf("|") > -1 )  {
                        extractingQuote = false;
                        int p = line.indexOf("|");
                        String before = line.substring(0, p);
                        String after = line.substring(p+1);
                        if ( after.indexOf("}}") > -1 ) {
                            line = after.substring(after.indexOf("}}")+2);
                        } else {
                            discardQuoteData = true;
                            line = before;
                        }
                    } else if ( line.indexOf("}}") > -1 ) {
                        int pos = line.indexOf("}}");
                        extractingQuote = false;
                        line = line.substring(0,pos)+line.substring(pos+2);
                    }
                } else {
                    String quoteType = hasQuote(line);
                    if ( quoteType != null) {
                        int pos = line.indexOf(quoteType);
                        int p2 = line.indexOf("|", pos);
                        String front = line.substring(0, pos);
                        String back = line.substring(p2+1);
                        if ( quoteType.equals("{{rquote")) {
                            p2 = back.indexOf("|");
                            if ( p2 > -1 ) {
                                back = back.substring(p2+1);
                            }
                        }

                        if ( back.indexOf("}}") > -1 ) {
                            line = front+"\n\n"+ cleanQuoteText(back)+"\n\n";
                        } else {
                            extractingQuote = true;
                            line = front+"\n\n"+back;
                        }
                    }
                }

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
                        line = line.replaceAll("<br\\/>", "\n");
                        line = line.replaceAll("<br \\/>", "\n");
                        line = line.replaceAll("\\{\\{.*\\}\\}","");
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
        
        // Next, turn the html into plain text
        HtmlUtils.strip(html);
        File txtFile = HtmlUtils.toTxt( new FileInputStream(html) );
        
        // Last, strip junk
        return stripStrayJunk(txtFile);
    }

    private static String cleanQuoteText(String back) {
        String clean = back.substring(0, back.indexOf("}}"));
        if ( clean.indexOf("|") > -1) {
            return clean.substring(0, clean.indexOf("|"));
        }
        return clean;
    }

    private static String hasQuote(String line) {
        String[] quotes = {"{{cquote", "{{rquote", "{{quote"};
        for ( int i=0; i<quotes.length; i++) {
            if ( line.contains(quotes[i])) {
                return quotes[i];
            }
        }
        return null;
    }

    private static File stripStrayJunk(File txtFile) throws IOException {
        
        File out = File.createTempFile("cleaned", "dat");
        FileOutputStream fos = new FileOutputStream(out);
        final  OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
        FileInputStream fis = new FileInputStream(txtFile);
        InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
        BufferedReader r = new BufferedReader( isr );
        while (true) {
            String line = r.readLine();
            if ( line == null ) {
                break;
            } else {
                line = line.replaceAll("\\[.*\\|\\[", "");
                line = line.replaceAll("\\[\\[", "");
                line = line.replaceAll("\\]\\]", "");
                line = line.replaceAll("<\\/ref>", "");
                line = line.replaceAll("}}", "");
                line += "\n";
                osw.write(line);
            }
        }
        IOUtils.closeQuietly( r );
        IOUtils.closeQuietly( osw );
        
        return out;
    }

    private static StripResult stripTag(final String tagStart, final String tag, final String tagEnd, String line) {
        final String fullStart = tagStart+tag;
        while ( line.contains(fullStart) ) {
            int start = line.indexOf(fullStart);
            int depth = 1;
            StringBuilder buf= new StringBuilder();
            for (int i=start+7; i<line.length(); i++) {
                buf.append(line.charAt(i));
                if  (buf.indexOf(tagStart) > -1) {
                    depth++;
                    buf = new StringBuilder();
                } else if ( buf.indexOf(tagEnd) > -1 ) {
                    depth--;
                    if ( depth == 0) {
                        line = line.substring(0, start) + line.substring(i+1);
                        break;
                    } else {
                        buf = new StringBuilder();
                    }
                }
            }
            if ( depth > 0 ) {
                return new StripResult(line.substring(0, start), depth);
            }
        }
        return new StripResult(line,0);
    }
    
    static final class StripResult {
        public final String strippedLine;
        public final int depth;
        public StripResult(String line, int depth) {
            this.strippedLine = line;
            this.depth = depth;
        }
    }

    private static String stripCitationNeeded(String line) {
        while ( line.contains("{{Citation needed")) {
            int start = line.indexOf("{{Citation needed");
            int end = line.indexOf("}}", start);
            if ( end == -1 ) {
                break;
            } else {
                line = line.substring(0, start) + line.substring(end+2);
            }
        }
        return line;
    }

}
