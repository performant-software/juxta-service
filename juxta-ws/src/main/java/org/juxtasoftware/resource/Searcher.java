package org.juxtasoftware.resource;

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.index.TermVectorOffsetInfo;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * Resource to search documents in a workspace for occurrences of text
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class Searcher extends BaseResource {
    private String searchTerm;
    
    @Autowired private IndexSearcher searcher;
    @Autowired private QueryParser queryParser;
    @Autowired private IndexReader indexReader;


    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        
        this.searchTerm = getQueryValue("term");
        if ( this.searchTerm == null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing search term");
        }
    }
    
    @Get("json")
    public Representation search() {
        try {
            Query query = this.queryParser.parse(this.searchTerm);
            ScoreDoc[] hits = this.searcher.search(query, 100).scoreDocs;
            
//            for(int i=0;i<hits.length;++i) {  
//
//                int docId = hits[i].doc;  
//                TermFreqVector tfvector = this.indexReader.getTermFreqVector(docId, "content");  
//                TermPositionVector tpvector = (TermPositionVector)tfvector;  
//                // this part works only if there is one term in the query string,  
//                // otherwise you will have to iterate this section over the query terms.  
//                int termidx = tfvector.indexOf(querystr);  
//                int[] termposx = tpvector.getTermPositions(termidx);  
//                TermVectorOffsetInfo[] tvoffsetinfo = tpvector.getOffsets(termidx);  
//
//                for (int j=0;j<termposx.length;j++) {  
//                    System.out.println("termpos : "+termposx[j]);  
//                }  
//                for (int j=0;j<tvoffsetinfo.length;j++) {  
//                    int offsetStart = tvoffsetinfo[j].getStartOffset();  
//                    int offsetEnd = tvoffsetinfo[j].getEndOffset();  
//                    System.out.println("offsets : "+offsetStart+" "+offsetEnd);  
//                }  
//
//                // print some info about where the hit was found...  
//                Document d = searcher.doc(docId);  
//                System.out.println((i + 1) + ". " + d.get("path"));  
//            }  
            
            
            return toTextRepresentation("Got "+hits.length+" matches");
        } catch (ParseException e) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            LOG.error("Unable to parse search term", e);
            return toTextRepresentation("Invalid search term");
        } catch (IOException e) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            LOG.error("Search failed", e);
            return toTextRepresentation("Search Failed");
        } finally {
            try {
                this.searcher.close();
            } catch (IOException e) {}
        }
    }
}
