package org.juxtasoftware;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.index.TermVectorMapper;
import org.apache.lucene.index.TermVectorOffsetInfo;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * Utility to generate a lucene search index for the text content
 * of the juxta web service
 * 
 * @author loufoster
 */
public class Indexer {
    public static void main(String[] args) {
        
        if ( args.length == 0 ) {
            System.err.println("Uasge: Indexer [db_user] [db_pass] (optional) ");
            System.exit(0);
        }
        String user = args[0];;
        String pass = null;
        if ( args.length == 2 ) {
            pass = args[1];
        }
        
        Connection conn = null;
        IndexWriter indexWriter = null;

        try {
            // Init DB connection
            System.out.println("Connecting to JuxtaWS database");
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            conn = DriverManager.getConnection("jdbc:mysql://127.0.0.1/juxta_ws", user, pass);

            // Init lucene index
            System.out.println("Initialize Lucene");
            File indexDir = new File("lucene-index/");
            if (indexDir.exists() == false ) {
                indexDir.mkdir();
            }
            Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_36);
            Directory directory = FSDirectory.open( indexDir );
            IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_36, analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            indexWriter = new IndexWriter(directory, config);
            indexWriter.deleteAll();
            indexWriter.commit();
            
            
            // Index the text_contents one document at a time
            Statement stmt = conn.createStatement();
            int start = 0;
            //String lastId = "";
            while ( true ) {
                String sql = "select id, content from text_content limit "+start+",1";
                ResultSet rs = stmt.executeQuery(sql);
                if (rs.next()) {
                    Document doc = new Document();
                    doc.add(new Field("id", rs.getString("id"), Field.Store.YES, Field.Index.NOT_ANALYZED));
                    Field f = new Field("content", rs.getString("content"), Field.Store.NO, 
                        Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS);
                    f.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
                    doc.add( f );  
                    indexWriter.addDocument(doc);
                    start++;
                    //lastId = rs.getString("id");
                } else{
                    break;
                }
            }
            stmt.close();
            System.out.println("Number of documents indexed: "+indexWriter.numDocs());
            indexWriter.commit();
            indexWriter.close();
            
//            // testing: search for something that is not there
//            // verify it is not found. Update existing doc with
//            // content to match then search again. versif hit
            searchTest(directory, analyzer);
//            updateTest(directory, analyzer, lastId);
//            searchTest(directory, analyzer);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        } finally {
            // cleanup
            System.out.println("Finished; cleaning up.");
            if ( indexWriter != null ) {
                try {
                    indexWriter.close();
                } catch (Exception e) {
                    e.printStackTrace();
                } 
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
               
    }
    
    public static void updateTest(Directory directory, Analyzer analyzer, String id ) {
        System.out.println("TEST: replacing content of "+id);
        IndexWriter indexWriter = null;
        try {
            // create a new writer for delete
            IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_36, analyzer);
            indexWriter = new IndexWriter(directory,config);
            
            // delete item 17
            Term t = new Term("id", id);
            indexWriter.deleteDocuments(t);
            Document doc = new Document();
            doc.add(new Field("id", id, Field.Store.YES, Field.Index.NOT_ANALYZED));
            doc.add(new Field("content", "LIZARD", Field.Store.NO, Field.Index.ANALYZED));  
            indexWriter.addDocument(doc);
            indexWriter.commit();
        }catch (Exception e) {
            e.printStackTrace();
        } finally {
            if ( indexWriter != null ) {
                try {
                    indexWriter.close();
                } catch (Exception e) {
                    e.printStackTrace();
                } 
            }
        }
    }
    
    public static void searchTest(Directory directory, Analyzer analyzer ) throws CorruptIndexException, IOException, ParseException {
        IndexReader ireader = IndexReader.open(directory);
        IndexSearcher isearcher = new IndexSearcher(ireader);
        QueryParser parser = new QueryParser(Version.LUCENE_36, "content", analyzer);
        Query query = parser.parse("pistol");
        ScoreDoc[] hits = isearcher.search(query, null, 1000).scoreDocs;
        TermFreqVector f = ireader.getTermFreqVector(hits[0].doc, "content");
        TermPositionVector tpvector = (TermPositionVector)f;  
        TermVectorMapper tvm = new TermVectorMapper() {
            
            @Override
            public void setExpectations(String arg0, int arg1, boolean arg2, boolean arg3) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void map(String arg0, int arg1, TermVectorOffsetInfo[] arg2, int[] arg3) {
                // TODO Auto-generated method stub
                
            }
        };
        ireader.getTermFreqVector(hits[0].doc, "content", tvm);
        System.out.println("Search Hits length: "+ hits.length);
    }
}
