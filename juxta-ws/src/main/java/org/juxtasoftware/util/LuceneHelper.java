package org.juxtasoftware.util;

import java.io.IOException;
import java.io.Reader;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LuceneHelper {
    @Autowired private IndexWriter indexWriter;
    
    public void addDocument( final Long id, final Reader reader ) throws CorruptIndexException, IOException {
        Document doc = new Document();
        doc.add(new Field("id", id.toString(), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field("content", reader));
        this.indexWriter.addDocument(doc);
        this.indexWriter.commit();
    }
    
    public void deleteDocument( final Long id ) throws CorruptIndexException, IOException {
        Term term = new Term("id", id.toString());
        this.indexWriter.deleteDocuments(term);
        this.indexWriter.commit();
    }
}
