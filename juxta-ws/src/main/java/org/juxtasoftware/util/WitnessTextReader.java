package org.juxtasoftware.util;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;

import org.juxtasoftware.model.RevisionInfo;
import org.juxtasoftware.model.RevisionInfo.Type;

import eu.interedition.text.Range;

public final class WitnessTextReader {
    private StringWriter sourceContentWriter = new StringWriter();
    
    public void read(Reader content, Range range, List<RevisionInfo> revs) throws IOException {
        if ( range!= null ) {
            // toss all dels (they will be seen) and revisions that are outside of specified range
            // leaving us with adds. all add ranges will be skipped
            for (Iterator<RevisionInfo> itr=revs.iterator(); itr.hasNext(); ) {
                RevisionInfo inf = itr.next();
                if ( inf.getType().equals(Type.DELETE)) {
                    itr.remove();
                } else {
                    if ( inf.getRange().getStart() < range.getStart() ) {
                        itr.remove();
                    }
                }
            }
        }
        
        long pos = 0;
        Iterator<RevisionInfo> revItr = revs.iterator();
        RevisionInfo currRev = null;
        if ( revItr.hasNext() ) {
            currRev = revItr.next();
        }
        Character lastWrite = null;
        while ( pos <= range.getEnd() ) {
            int data = content.read();
            if ( data == -1 ) {
                break;
            } else {
                if ( pos >= range.getStart() && (pos+1) <= range.getEnd()) {
                    if ( currRev != null && pos >= currRev.getRange().getStart() ) {
                        if ( currRev.getRange().getEnd() == (pos+1) ) {
                            currRev = null;
                            if (revItr.hasNext()) {
                                currRev = revItr.next();
                            }
                        }
                    } else {
                        if ( lastWrite != null ) {
                            char curr = (char)data;
                            if ( !(Character.isWhitespace(lastWrite) && Character.isWhitespace(curr) && lastWrite == curr) ) {
                                sourceContentWriter.append( (char)data );
                                lastWrite = (char)data;    
                            }
                        } else {
                            sourceContentWriter.append( (char)data );
                            lastWrite = (char)data;   
                        }
                    }
                }
                pos++;
            }
        }
    }
    
    @Override
    public String toString() {
        return this.sourceContentWriter.toString();
    }
}