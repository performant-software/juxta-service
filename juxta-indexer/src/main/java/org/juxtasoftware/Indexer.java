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
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
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
            
            System.out.println("Indexing SOURCES...");
            int srcCnt = indexDocuments("source", indexWriter, conn);
            System.out.println("\nIndexing WITNESSES...");
            int witCnt = indexDocuments("witness", indexWriter, conn);
            
            System.out.println("\n=============================");
            System.out.println("  FINISHED");
            System.out.println("=============================");
            System.out.println("  Sources indexed : "+srcCnt );
            System.out.println("Witnesses indexed : "+witCnt );
            System.out.println(" TOTAL index size : "+indexWriter.numDocs() );
            indexWriter.close();      

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        } finally {
            // cleanup
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
    
    public static int indexDocuments( final String type, IndexWriter indexWriter, Connection conn ) throws SQLException, CorruptIndexException, IOException {
        final int startDocs = indexWriter.numDocs();
        String txtIdCol = "content_id";
        String sql = "";
        if ( type == "witness" ) {
            sql = "select juxta_witness.id, juxta_witness.name, text_id, ws.name from juxta_witness " +
            	  " inner join juxta_workspace as ws on ws.id = workspace_id";
            txtIdCol = "text_id";
        } else {
            sql = "select juxta_source.id, juxta_source.name, content_id, ws.name from juxta_source " +
                  " inner join juxta_workspace as ws on ws.id = workspace_id";
        }
        Statement stmt = conn.createStatement();
        Statement stmt2 = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
        while ( rs.next() ) {
            final Long textId = rs.getLong(txtIdCol);
            final Long libraryItemId = rs.getLong("id");
            final String name = rs.getString("name");
            final String ws = rs.getString("ws.name");
            System.out.println("    indexing "+ws+":"+name);
            String sql2 = "select id, content from text_content where id="+textId;
            ResultSet rs2 = stmt2.executeQuery(sql2);
            if (rs2.next()) {
                Document doc = new Document();
                doc.add(new Field("id", rs2.getString("id"), Field.Store.YES, Field.Index.NOT_ANALYZED));
                doc.add(new Field("workspace", ws, Field.Store.YES, Field.Index.NOT_ANALYZED));
                doc.add(new Field("type", type, Field.Store.YES, Field.Index.NOT_ANALYZED));
                doc.add(new Field("itemId", libraryItemId.toString(), Field.Store.YES, Field.Index.NOT_ANALYZED));
                doc.add(new Field("name", name, Field.Store.YES, Field.Index.NOT_ANALYZED));
                Field f = new Field("content", rs2.getString("content"), Field.Store.NO, 
                    Field.Index.ANALYZED, Field.TermVector.WITH_POSITIONS_OFFSETS);
                doc.add( f );  
                indexWriter.addDocument(doc);
            } 
        }
        stmt2.close();
        stmt.close();
        indexWriter.commit();
        return (indexWriter.numDocs()-startDocs);
    }
}
