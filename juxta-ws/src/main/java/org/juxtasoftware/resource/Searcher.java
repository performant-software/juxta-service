package org.juxtasoftware.resource;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
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
    @Autowired private Integer fragSize;
    @Autowired private Integer phraseSlop;
    @Autowired private QueryParser queryParser;

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
            
            LOG.info("Search for '"+this.searchString+"'");
            
            // build a phrase quuery to match exact phrase entered
            TermQuery wsQuery = new TermQuery( new Term("workspace", this.workspace.getName()) );
            TermQuery srcQuery = new TermQuery( new Term("type", "source") );
            TermQuery witQuery = new TermQuery( new Term("type", "witness") );
            Query phraseQ = this.queryParser.parse("\""+this.searchString.trim()+"\"");
            
            BooleanQuery query = new BooleanQuery();
            query.add(wsQuery, Occur.MUST);
            query.add(phraseQ, Occur.MUST);
            query.add(srcQuery, Occur.MUST);
            
            // do 2 searches, one in source one in witness. this makes sure
            // that they are treated equally wrt the top docs score; ie the top
            // x docs in both source and witness are returned
            // pick the top hits in sources
            TopScoreDocCollector collector = TopScoreDocCollector.create(this.hitsPerPage, true);
            this.searcher.search(query, collector);
            ScoreDoc[] scoreDocs = collector.topDocs(0, this.hitsPerPage).scoreDocs;
            List<ScoreDoc> hits = new ArrayList<ScoreDoc>(Arrays.asList(scoreDocs));
            
            // now witnesses
            collector = TopScoreDocCollector.create(this.hitsPerPage, true);
            query = new BooleanQuery();
            query.add(wsQuery, Occur.MUST);
            query.add(phraseQ, Occur.MUST);
            query.add(witQuery, Occur.MUST);
            this.searcher.search(query, collector);
            scoreDocs = collector.topDocs(0, this.hitsPerPage).scoreDocs;
            hits.addAll(Arrays.asList(scoreDocs));
            LOG.info("Search for '"+this.searchString+"' yields "+hits.size()+" raw hits");
            
            WeightedTerm[] terms = QueryTermExtractor.getTerms(phraseQ);
            for(ScoreDoc scoreDoc : hits) {  
                Document doc = this.searcher.doc(scoreDoc.doc);           
                TermFreqVector tfvector = this.indexReader.getTermFreqVector(scoreDoc.doc, "content");  
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
            
            mergeHits(sourceHits);
            getSourceFragments(sourceHits);
            mergeHits(witnessHits);
            getWitnessFragments(witnessHits);
            LOG.info("Search for '"+this.searchString+"' end result: "+sourceHits.size()+" source hits, "+witnessHits.size()+" witness hits");
            
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
        } catch (ParseException e) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            LOG.error("Invalid search query specified");
            return toTextRepresentation("Invalid search query specified");
        } finally {
            try {
                this.searcher.close();
            } catch (IOException e) {}
        }
    }

    private void getSourceFragments(Map<HitItem, List<HitDetail>> hits) {
        List<HitItem> deadHit = new ArrayList<Searcher.HitItem>();
        for (  Entry<HitItem, List<HitDetail>> ent : hits.entrySet()  ) {
            List<HitDetail> ranges = ent.getValue();
            
            Long srcId = Long.parseLong(ent.getKey().id);
            Source src = this.sourceDao.find(this.workspace.getId(), srcId); 
            if ( src == null) {
                LOG.warn("Source "+srcId+" no longer exists");
                deadHit.add(ent.getKey());
                continue;
            }
            
            for ( Iterator<HitDetail> itr = ranges.iterator(); itr.hasNext();) {
                Reader srcReader = this.sourceDao.getContentReader(src);
                HitDetail detail = itr.next();
                float p = (float)detail.getStartOffset() / (float)src.getText().getLength();
                detail.percent = Math.round( p * 100.0f);
                int start = detail.getStartOffset()-this.fragSize;
                start = Math.max(0, start);
                int end =  detail.getEndOffset()+this.fragSize;
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
        
        // clean out dead stuff from search results
        for ( HitItem hi : deadHit) {
            hits.remove(hi);
        }
    }
    
    private void getWitnessFragments(Map<HitItem, List<HitDetail>> hits) {
        List<HitItem> deadHit = new ArrayList<Searcher.HitItem>();
        for (  Entry<HitItem, List<HitDetail>> ent : hits.entrySet()  ) {
            List<HitDetail> ranges = ent.getValue();
            
            Long witId = Long.parseLong(ent.getKey().id);
            Witness wit = this.witnessDao.find(witId);
            if ( wit == null ) {
                LOG.warn("Witness "+witId+" no longer exists");
                deadHit.add(ent.getKey());
                continue;
            }
            
            for ( Iterator<HitDetail> itr = ranges.iterator(); itr.hasNext();) {
                Reader rdr = this.witnessDao.getContentStream(wit);
                HitDetail detail = itr.next();
                float p = (float)detail.getStartOffset() / (float)wit.getText().getLength();
                detail.percent = Math.round( p * 100.0f);
                int start = detail.getStartOffset()-this.fragSize;
                start = Math.max(0, start);
                int end =  detail.getEndOffset()+this.fragSize;
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
        
        // clean out dead stuff from search results
        for ( HitItem hi : deadHit) {
            hits.remove(hi);
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
                    if ( lastRange.getEndOffset()+this.phraseSlop >= currRange.getStartOffset() ) {
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
                if ( len < this.searchString.length() ) {
                    itr.remove();
                }
            }
        }
        
        for ( Iterator<Entry<HitItem, List<HitDetail>>> itr = hits.entrySet().iterator(); itr.hasNext();) {
            Entry<HitItem, List<HitDetail>> ent = itr.next();
            if ( ent.getValue().size() == 0) {
                itr.remove();
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

    private int addHit(Map<HitItem, List<HitDetail>> hitMap, String itemId, String name, TermVectorOffsetInfo range) {
        HitItem hit = new HitItem(itemId, name);
        if ( hitMap.containsKey(hit) == false) {
            hitMap.put(hit, new ArrayList<HitDetail>() );
        }
        hitMap.get(hit).add( new HitDetail(range) );
        return hitMap.get(hit).size();
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
