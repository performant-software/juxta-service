package org.juxtasoftware.resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.index.TermVectorOffsetInfo;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.highlight.QueryTermExtractor;
import org.apache.lucene.search.highlight.WeightedTerm;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Resource to search documents in a workspace for occurrences of text
 * 
 * @author loufoster
 *
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class Searcher extends BaseResource {
    private String searchString;
    
    @Autowired private IndexSearcher searcher;
    @Autowired private IndexReader indexReader;
    @Autowired private Integer hitsPerPage;


    @Override
    protected void doInit() throws ResourceException {
        super.doInit();
        
        this.searchString = getQueryValue("q");
        if ( this.searchString == null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing search query");
        }
        this.searchString = this.searchString.trim().replaceAll("\\s+", " ");
    }
    
    @Get("json")
    public Representation search() {
        try {
            Map<String, List<TermVectorOffsetInfo> > sourceHits = new HashMap<String, List<TermVectorOffsetInfo> >();
            Map<String, List<TermVectorOffsetInfo> > witnessHits = new HashMap<String, List<TermVectorOffsetInfo> >();
            
            // build a phrase quuery to match exact phrase entered
            TermQuery wsQuery = new TermQuery( new Term("workspace", this.workspace.getName()) );
            PhraseQuery phraseQ = new PhraseQuery();
            String[] words = this.searchString.split(" ");
            for (String word : words) {
                phraseQ.add(new Term("content", word));
            }
            BooleanQuery query = new BooleanQuery();
            query.add(wsQuery, Occur.MUST);
            query.add(phraseQ, Occur.MUST);
            
            // pick the top hits
            TopScoreDocCollector collector = TopScoreDocCollector.create(this.hitsPerPage, true);
            this.searcher.search(query, collector);
            ScoreDoc[] hits = collector.topDocs().scoreDocs;
            WeightedTerm[] terms = QueryTermExtractor.getTerms(query);
            
            for(int i=0;i<hits.length;++i) {  

                int docId = hits[i].doc;  
                Document doc = this.searcher.doc(docId);
                                
                TermFreqVector tfvector = this.indexReader.getTermFreqVector(docId, "content");  
                TermPositionVector tpvector = (TermPositionVector)tfvector;  
                
                for ( int tid = 0; tid<terms.length; tid++) {
                    int termidx = tfvector.indexOf(terms[tid].getTerm());  
                    TermVectorOffsetInfo[] tvoffsetinfo = tpvector.getOffsets(termidx);  
   
                    for (int j=0;j<tvoffsetinfo.length;j++) {   
                        String itemId = doc.get("itemId");
                        if ( doc.get("type").equals("source")) {
                            addHit(sourceHits, itemId, tvoffsetinfo[j]);
                        } else {
                            addHit(witnessHits, itemId, tvoffsetinfo[j]);   
                        }

                    }  
                } 
            }  
            
            System.out.println("Merge sources");
            mergeHits(sourceHits);
            System.out.println("Merge witnesses");
            mergeHits(witnessHits);
            
            JsonObject json = new JsonObject();
            Gson gson = new Gson();
            JsonArray srcs = hitsToJson( sourceHits, gson );
            JsonArray wits = hitsToJson( witnessHits, gson );
            json.add("sourceHits", srcs);
            json.add("witnessHits", wits);
            return toTextRepresentation( json.toString() );
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

    private void mergeHits(Map<String, List<TermVectorOffsetInfo>> hits ) {
        for (  Entry<String, List<TermVectorOffsetInfo>> ent : hits.entrySet()  ) {
            List<TermVectorOffsetInfo> ranges = ent.getValue();
            
            Collections.sort(ranges, new Comparator<TermVectorOffsetInfo>() {
                @Override
                public int compare(TermVectorOffsetInfo a, TermVectorOffsetInfo b) {
                    if ( a.getStartOffset() < b.getStartOffset() ) {
                        return -1;
                    } else if ( a.getStartOffset() > b.getStartOffset() ) {
                        return 1;
                    } else {
                        if ( a.getEndOffset() < b.getEndOffset() ) {
                            return -1;
                        } else if ( a.getEndOffset() > b.getEndOffset() ) {
                            return 1;
                        } 
                    }
                    return 0;
                }
            });
            
            // merge adjacent into a single range
            TermVectorOffsetInfo lastRange = null;
            for ( Iterator<TermVectorOffsetInfo> itr = ranges.iterator(); itr.hasNext();) {
                TermVectorOffsetInfo currRange = itr.next();
                if ( lastRange != null ) {
                    if ( lastRange.getEndOffset()+1 == currRange.getStartOffset() ) {
                        lastRange.setEndOffset( currRange.getEndOffset() );
                        itr.remove();
                        continue;
                    }
                }
                lastRange = currRange;
            }
            
            // toss anything thats not the same len as the search str
            for ( Iterator<TermVectorOffsetInfo> itr = ranges.iterator(); itr.hasNext();) {
                TermVectorOffsetInfo currRange = itr.next();
                int len = currRange.getEndOffset() - currRange.getStartOffset();
                if ( len != this.searchString.length() ) {
                    itr.remove();
                }
            }
        }
    }

    private JsonArray hitsToJson(Map<String, List<TermVectorOffsetInfo>> hits, Gson gson) {
        JsonArray jsonArray = new JsonArray();
        for (Entry<String, List<TermVectorOffsetInfo>> ent : hits.entrySet() ) {
            String id = ent.getKey();
            JsonObject obj = new JsonObject();
            obj.addProperty("id", id);
            obj.add("hits", gson.toJsonTree(ent.getValue() ));
            jsonArray.add(obj);
        }
        return jsonArray;
    }

    private void addHit(Map<String, List<TermVectorOffsetInfo>> hitMap, String itemId, TermVectorOffsetInfo range) {
        if ( hitMap.containsKey(itemId) == false) {
            hitMap.put(itemId, new ArrayList<TermVectorOffsetInfo>() );
        }
        hitMap.get(itemId).add(range);
    }
}
