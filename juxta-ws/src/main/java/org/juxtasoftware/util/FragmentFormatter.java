package org.juxtasoftware.util;

import org.apache.commons.lang.StringEscapeUtils;

import eu.interedition.text.Range;

public final class FragmentFormatter {
    private FragmentFormatter(){}
    
    public static String format(final String srcFrag, Range origRange, Range contextRange, long maxLen) {
        
        // NOTES:
        // There are cases (like huck fin example) where text like </image blah> is inline
        // in the plain text witness. The fragment needs to be escaped so this shows,
        // but the tags for the change itself must NOT be escaped. So... stick in a 
        // odd char string (that wont be esscaped) to represent tag locations in original fragment, then escape it.
        // This ensures the tags are placed correctly. Next, replace the special strings with
        // actual tags. Kinda ugly, but it works (until the doc has the odd char sequences in its fragment)
        
        // slap in some tags to show where the change is
        final String omit = " <span class='del'>&#10006;</span>";
        final String omitMarker = "|{x}|";
        final String change = "<span class='change'>";
        final String startMarker = "|{s}|";
        final String endTag = "</span>";
        final String endMarker = "|{e}|";
        long startOffset = origRange.getStart() - contextRange.getStart();
        long endOffset = startOffset+origRange.length();
        StringBuilder frag = new StringBuilder(srcFrag);
        if ( origRange.length() == 0 ) {
            frag.insert((int) startOffset, omitMarker);
        } else {
            // find the first whitespace on after the end offset.
            long orig = endOffset;
            while ( true ) {
                // if this is the last pos in the fragment, we're done
                if ( (int)endOffset == frag.length() ) {
                    break;
                } else if ( Character.isWhitespace( frag.charAt((int)endOffset)) == false ) {
                    endOffset++;
                    if ( endOffset >= frag.length()) {
                        endOffset = orig;
                        break;
                    }
                } else {
                    break;
                }
                    
            }
            frag.insert((int) endOffset, endMarker);
            
            // find first whitespace on or before start
            orig = startOffset;
            long prior = -1;
            while ( true ) {
                if ( Character.isWhitespace( frag.charAt((int)startOffset)) == false ) {
                    prior = startOffset;
                    startOffset--;
                    if ( startOffset <= 0) {
                        startOffset = orig;
                        break;
                    }
                } else {
                    if ( prior != -1) {
                        startOffset = prior;
                    }
                    break;
                }
                    
            }
            frag.insert((int) startOffset, startMarker);
        }
        
        // convert it to a string so the extra spaces can be stripped
        // Do this AFTER above so positions will be correct, but
        // before word broundary stuff so the bondaries are correct
        String out = frag.toString();
        out = out.trim();
        out = out.replaceAll("\\n ", "\n");
        out = out.replaceAll("\\n+", "\n");
        out = out.replaceAll("\\n", " / ");
        out = StringEscapeUtils.escapeHtml(out);
        out = out.replace(startMarker, change);
        out = out.replace(endMarker, endTag);
        out = out.replace(omitMarker, omit);
               
        // trim frag so it starts/ends on word boundaries
        int lastTagPos = out.lastIndexOf('>')+1;
        if ( lastTagPos < out.length() ) {
            int endPos =  out.lastIndexOf(' ');
            if ( endPos != -1 ) {
                out = out.substring(0, (int)Math.max(endPos, lastTagPos));
            }
        } 
        
        int firstTagPos = out.indexOf('<');
        if ( firstTagPos > 0 ) {
            int startPos =  out.indexOf(' ');
            if ( startPos != -1) {
                 out  =  out.substring(Math.min(startPos, firstTagPos));
            }
        }
        
       
        // append lead/trail ellipses as needed
        if (contextRange.getStart() > 0) {
             out = "..." + out;
        }
        if ( contextRange.getEnd() < maxLen) {
             out =  out + "...";
        }
        
        return out;
    }
}
