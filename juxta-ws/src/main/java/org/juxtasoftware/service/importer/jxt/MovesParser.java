package org.juxtasoftware.service.importer.jxt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.juxtasoftware.model.ComparisonSet;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import eu.interedition.text.Range;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class MovesParser {

    public List<JxtMoveInfo> parse( final ComparisonSet set, final File movesFile ) throws IOException {
        List<JxtMoveInfo> moves = new ArrayList<JxtMoveInfo>();
        
        BufferedReader br = null;
        try {
            br = new BufferedReader( new FileReader(movesFile));
            while ( true ) {
                String line = br.readLine();
                if ( line == null ) {
                    break;
                }
                
                // only care about the move definition of this xml
                // file. these lines look like: '<move .../>'
                if ( line.contains("<move ") ) {
                    JxtMoveInfo info = new JxtMoveInfo();
                    for (int i=1; i<=2; i++) {
                        String title = extractValue(line, "doc"+i); 
                        String start = extractValue(line, "start"+i);
                        String end = extractValue(line, "end"+i);
                        Range range = new Range(Long.parseLong(start), Long.parseLong(end));
                        info.addWitnessRange( title, range);
                    }
                    moves.add( info );
                }
            }
        } finally {
            IOUtils.closeQuietly(br);
        }
        
        return moves;
    }
    
    private String extractValue(final String line, final String attribName) {
        int pos = line.indexOf(attribName);
        int quoteStartPos = line.indexOf('"', pos);
        int quoteEndPos = line.indexOf('"', quoteStartPos+1);
        return line.substring(quoteStartPos+1, quoteEndPos);
    }
    
    /**
     * Information collected about TRANSPOSITIONS during 
     * the parse of the moves.xml.
     */
    public static class JxtMoveInfo {
        private Map<String, Range> witnessRangeMap = new HashMap<String, Range>();
        
        public void addWitnessRange( final String witnessTitle, final Range r) {
            this.witnessRangeMap.put(witnessTitle, r);
        }
        
        public boolean hasWitnessRange( final String title ) {
            return this.witnessRangeMap.containsKey(title);
        }
        
        public Set<String> getWitnessTitles() {
            return this.witnessRangeMap.keySet();
        }
        
        public Range getWitnessRange( final String title) {
            return this.witnessRangeMap.get(title);
        }
    }
}
