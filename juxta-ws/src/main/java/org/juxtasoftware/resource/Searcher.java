package org.juxtasoftware.resource;

import java.io.IOException;
import java.io.Reader;
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
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Witness;
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
    @Autowired private SourceDao sourceDao;
    @Autowired private WitnessDao witnessDao;


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
            Map<HitItem, List<HitDetail> > sourceHits = new HashMap<HitItem, List<HitDetail> >();
            Map<HitItem, List<HitDetail> > witnessHits = new HashMap<HitItem, List<HitDetail> >();
            
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
                        String name = doc.get("name");
                        if ( doc.get("type").equals("source")) {
                            addHit(sourceHits, itemId, name, tvoffsetinfo[j]);
                        } else {
                            addHit(witnessHits, itemId, name, tvoffsetinfo[j]);   
                        }

                    }  
                } 
            }  
            
            System.out.println("Merge sources");
            mergeHits(sourceHits);
            getSourceFragments(sourceHits);
            System.out.println("Merge witnesses");
            mergeHits(witnessHits);
            getWitnessFragments(witnessHits);
            
            JsonObject json = new JsonObject();
            Gson gson = new Gson();
            JsonArray jsonSrcs = hitsToJson( sourceHits, gson );
            JsonArray jsonWits = hitsToJson( witnessHits, gson );
            json.add("sources", jsonSrcs);
            json.add("witnesses", jsonWits);
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

    private void getSourceFragments(Map<HitItem, List<HitDetail>> hits) {
        final int fragChars = 10;
        for (  Entry<HitItem, List<HitDetail>> ent : hits.entrySet()  ) {
            List<HitDetail> ranges = ent.getValue();
            
            Long srcId = Long.parseLong(ent.getKey().id);
            Source src = this.sourceDao.find(this.workspace.getId(), srcId); 
            
            for ( Iterator<HitDetail> itr = ranges.iterator(); itr.hasNext();) {
                Reader srcReader = this.sourceDao.getContentReader(src);
                HitDetail detail = itr.next();
                float p = (float)detail.getStartOffset() / (float)src.getText().getLength();
                detail.percent = Math.round( p * 100.0f);
                int start = detail.getStartOffset()-fragChars;
                start = Math.max(0, start);
                int end =  detail.getEndOffset()+fragChars;
                end = Math.min(end, (int)src.getText().getLength());
                char[] buf = new char[end-start];
                try {
                    srcReader.skip(start);
                    srcReader.read(buf, 0, buf.length);
                    detail.fragment = new String( buf ).trim();
                    if ( start > 0 ) {
                        detail.fragment = "..."+detail.fragment;
                    }
                    if ( end < src.getText().getLength() ) {
                        detail.fragment += "...";
                    }
                } catch (IOException e) {
                    LOG.error("Unable to get fragment for "+src+" range: "+start+", "+end);
                }
            }
        }
    }
    
    private void getWitnessFragments(Map<HitItem, List<HitDetail>> hits) {
        final int fragChars = 25;
        for (  Entry<HitItem, List<HitDetail>> ent : hits.entrySet()  ) {
            List<HitDetail> ranges = ent.getValue();
            
            Long witId = Long.parseLong(ent.getKey().id);
            Witness wit = this.witnessDao.find(witId);
            
            for ( Iterator<HitDetail> itr = ranges.iterator(); itr.hasNext();) {
                Reader rdr = this.witnessDao.getContentStream(wit);
                HitDetail detail = itr.next();
                float p = (float)detail.getStartOffset() / (float)wit.getText().getLength();
                detail.percent = Math.round( p * 100.0f);
                int start = detail.getStartOffset()-fragChars;
                start = Math.max(0, start);
                int end =  detail.getEndOffset()+fragChars;
                end = Math.min(end, (int)wit.getText().getLength());
                char[] buf = new char[end-start];
                try {
                    rdr.skip(start);
                    rdr.read(buf, 0, buf.length);
                    detail.fragment = new String( buf ).trim();
                    if ( start > 0 ) {
                        detail.fragment = "..."+detail.fragment;
                    }
                    if ( end < wit.getText().getLength() ) {
                        detail.fragment += "...";
                    }
                } catch (IOException e) {
                    LOG.error("Unable to get fragment for "+wit+" range: "+start+", "+end);
                }
            }
        }
    }

    private void mergeHits(Map<HitItem, List<HitDetail>> hits ) {
        for (  Entry<HitItem, List<HitDetail>> ent : hits.entrySet()  ) {
            List<HitDetail> ranges = ent.getValue();
            
            Collections.sort(ranges, new Comparator<HitDetail>() {
                @Override
                public int compare(HitDetail a, HitDetail b) {
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
            for ( Iterator<HitDetail> itr = ranges.iterator(); itr.hasNext();) {
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
            for ( Iterator<HitDetail> itr = ranges.iterator(); itr.hasNext();) {
                TermVectorOffsetInfo currRange = itr.next();
                int len = currRange.getEndOffset() - currRange.getStartOffset();
                if ( len != this.searchString.length() ) {
                    itr.remove();
                }
            }
        }
    }

    private JsonArray hitsToJson(Map<HitItem, List<HitDetail>> hits, Gson gson) {
        JsonArray jsonArray = new JsonArray();
        for (Entry<HitItem, List<HitDetail>> ent : hits.entrySet() ) {
            HitItem info = ent.getKey();
            JsonObject obj = new JsonObject();
            obj.addProperty("id", info.id);
            obj.addProperty("name", info.name);
            JsonArray jsonHits = new JsonArray();
            for (HitDetail detail : ent.getValue() ) {
                JsonObject ob = new JsonObject();
                ob.addProperty("start", Integer.toString(detail.getStartOffset()));
                ob.addProperty("end", Integer.toString(detail.getEndOffset()));
                ob.addProperty("percent", detail.percent);
                ob.addProperty("fragment", detail.fragment);
                jsonHits.add(ob);
            }
            obj.add("hits", jsonHits);
            jsonArray.add(obj);
        }
        return jsonArray;
    }

    private void addHit(Map<HitItem, List<HitDetail>> hitMap, String itemId, String name, TermVectorOffsetInfo range) {
        HitItem hit = new HitItem(itemId, name);
        if ( hitMap.containsKey(hit) == false) {
            hitMap.put(hit, new ArrayList<HitDetail>() );
        }
        hitMap.get(hit).add( new HitDetail(range) );
    }
    
    @SuppressWarnings("serial")
    private static class HitDetail extends TermVectorOffsetInfo {
        public String fragment;
        public int percent;
        public HitDetail ( TermVectorOffsetInfo inf ) {
            super();
            this.setEndOffset(inf.getEndOffset());
            this.setStartOffset(inf.getStartOffset());
            this.fragment = "";
            this.percent = 0;
        }
    }
    
    private static class HitItem {
        public final String name;
        public final String id;
        public HitItem( String id, String name) {
            this.id = id;
            this.name = name;
        }
        @Override
        public String toString() {
            return "HitInfo ["+this.id+" "+this.name+"]";
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            HitItem other = (HitItem) obj;
            if (id == null) {
                if (other.id != null)
                    return false;
            } else if (!id.equals(other.id))
                return false;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            return true;
        }
    }
}
