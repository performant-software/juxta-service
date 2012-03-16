package org.juxtasoftware.util;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

import com.google.common.io.CharStreams;

import eu.interedition.text.Range;

/**
 * Reader that can be constrained to the supplied range
 * 
 * @author loufoster
 *
 */
public final class RangedTextReader {
    private StringWriter sourceContentWriter = new StringWriter();
    
    public void read(Reader content) throws IOException {
        read(content, null);
    }
    public void read(Reader content, Range range) throws IOException {
        if ( range == null) {
            CharStreams.copy(content, this.sourceContentWriter);
        } else {
            long pos = 0;
            while ( pos <= range.getEnd() ) {
                int data = content.read();
                if ( data == -1 ) {
                    break;
                } else {
                    if ( pos >= range.getStart() && (pos+1) <= range.getEnd()) {
                        sourceContentWriter.append( (char)data );
                    }
                    pos++;
                }
            }
        }
    }
    
    @Override
    public String toString() {
        return this.sourceContentWriter.toString();
    }
}