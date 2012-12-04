package org.juxtasoftware.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;

import org.apache.commons.io.IOUtils;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.restlet.data.MediaType;
import org.restlet.engine.header.ContentType;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


/**
 * Wrapper around Apache Tika. Used to extract main body text for files of lots
 * of different types. Sopported:
 * doc, docx, rtf, html, pdf, odt, epub
 * 
 * @author loufoster
 *
 */
public class ConversionUtils {

    /**
     * Check if this is one of the file types Juxta will handle
     * @param type
     * @return
     */
    public static boolean canConvert(MediaType type ) {
    
        return ( type.equals(MediaType.TEXT_HTML) || 
                 type.equals(MediaType.APPLICATION_PDF) ||
                 type.equals(MediaType.APPLICATION_MSOFFICE_DOCX) ||
                 type.equals(MediaType.APPLICATION_WORD) ||
                 type.getName().equalsIgnoreCase("application/epub+zip") ||
                 type.getName().equalsIgnoreCase("text/rtf") ||
                 type.equals(MediaType.APPLICATION_RTF) ||
                 type.equals(MediaType.APPLICATION_OPENOFFICE_ODT) );
    }
    
    public static MediaType determineMediaType( File f ) {
        TikaInputStream tis = null;
        String mimeType = null;
        try {
            tis = TikaInputStream.get(f);
            Metadata md = new Metadata();
            ParseContext context = new ParseContext();
            DefaultDetector detector = new DefaultDetector();
            Parser parser = new AutoDetectParser(detector);
            context.set(Parser.class, parser);
            parser.parse(tis, new DefaultHandler(), md, context);
            mimeType = md.get(HttpHeaders.CONTENT_TYPE);
        } catch (Exception e) {
            
        } finally {
            IOUtils.closeQuietly(tis);
        }
        MediaType mt = ContentType.readMediaType(mimeType);
        return mt;
    }
    
    
    /**
     * Autodetect the content of the input stream, and extract the main content as TXT.
     * Results will be streamed into a temporary file.
     * 
     * @param srcInoutStream
     * @return
     */
    public static File convertToText( InputStream srcInputStream ) throws IOException {
        
        File txtFile = null;
        OutputStreamWriter osw = null;
        
        try {
            // create the UTF-8 temp file for holding the extracted text content
            txtFile = File.createTempFile("txt", "dat");
            txtFile.deleteOnExit();
            osw = new OutputStreamWriter(new FileOutputStream( txtFile ), "UTF-8" );
            
            // Set up Tika
            Metadata md = new Metadata();
            ParseContext context = new ParseContext();
            DefaultDetector detector = new DefaultDetector();
            Parser parser = new AutoDetectParser(detector);
            context.set(Parser.class, parser);
            BodyContentHandler handler = new BodyContentHandler(osw);
            
            // convert input to Tika-friendly input and strip the text
            // results will wind up in the temp file
            TikaInputStream tis = TikaInputStream.get(srcInputStream);
            parser.parse(tis, handler, md, context);

        } catch (SAXException e) {
            throw new IOException( e );
        } catch (TikaException e) {
            throw new IOException( e );
        } finally {
            IOUtils.closeQuietly(osw);
        }
        
        return txtFile; 
    }
}
