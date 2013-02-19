package org.juxtasoftware.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.Iterator;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.docx4j.convert.in.xhtml.XHTMLImporter;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart;
import org.juxtasoftware.Constants;
import org.juxtasoftware.model.PageMark;
import org.restlet.data.MediaType;
import org.restlet.engine.header.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import eu.interedition.text.Range;


/**
 * Wrapper around Apache Tika. Used to extract main body text for files of lots
 * of different types. Sopported:
 * doc, docx, rtf, html, pdf, odt, epub
 * 
 * @author loufoster
 *
 */
public class ConversionUtils {
    protected static final Logger LOG = LoggerFactory.getLogger( Constants.WS_LOGGER_NAME );
    
    /**
     * Convert the plain text witness stream to HTML, including markup for page breaks and line numbers
     * @param reader
     * @param range
     * @param marks
     * @return
     * @throws IOException
     */
    public static File witnessToHtml(Reader reader, Range range, List<PageMark> marks) throws IOException {
        File out = File.createTempFile("wit", "dat");
        out.deleteOnExit();
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(out), "UTF-8");
        
        if ( range == null ) {
            range = new Range(0, Integer.MAX_VALUE);
        }

        // get the page number marks and select the first one that is
        // within the requested range. 
        Iterator<PageMark> markItr = marks.iterator();
        PageMark currMark = null;
        if (markItr.hasNext()) {
            currMark = markItr.next();
            while (currMark.getOffset() < range.getStart()) {
                if (markItr.hasNext()) {
                    currMark = markItr.next();
                }
            }
        }

        // stream witness text from db into file incuding line num/page break markup
        long pos = 0;
        StringBuilder line = new StringBuilder();
        while (pos <= range.getEnd()) {
            int data = reader.read();
            if (data == -1) {
                break;
            } else {
                if (pos >= range.getStart() && (pos + 1) <= range.getEnd()) {
                    if (currMark != null && currMark.getOffset() == pos) {
                        line.append(currMark.toHtml());
                        currMark = null;
                        if (markItr.hasNext()) {
                            currMark = markItr.next();
                        }
                    }

                    if (data == '\n') {
                        line.append("<br/>");
                        osw.write(line.toString());
                        line = new StringBuilder();
                    } else {
                        line.append(StringEscapeUtils.escapeHtml(Character.toString((char) data)));
                    }
                }
                pos++;
            }
        }

        IOUtils.closeQuietly(osw);
        IOUtils.closeQuietly(reader);
        return out;
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
    
    public static File convertHtmlToDocx( Reader htmlReader ) throws IOException, JAXBException, Docx4JException {
        File outFile = File.createTempFile("edition", "docx");
        outFile.deleteOnExit();
        
        WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.createPackage();
        NumberingDefinitionsPart ndp = new NumberingDefinitionsPart();
        wordMLPackage.getMainDocumentPart().addTargetPart(ndp);
        ndp.unmarshalDefaultNumbering();
        
        // Convert the XHTML, and add it into the empty docx we made
        wordMLPackage.getMainDocumentPart().getContent().addAll( 
                XHTMLImporter.convert(htmlReader, null, wordMLPackage) );
        
        wordMLPackage.save( outFile );
        
        return outFile;
    }
    
    
    /**
     * Autodetect the content of the input stream, and extract the main content as TXT.
     * Results will be streamed into a temporary file.
     * 
     * @param srcInoutStream
     * @return
     * @throws IOException 
     * @throws TikaException 
     * @throws SAXException 
     */
    public static File convertToText( InputStream srcInputStream ) throws IOException, SAXException, TikaException {
        
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

        }  finally {
            IOUtils.closeQuietly(osw);
        }
               
        try {
            EncodingUtils.stripUnknownUTF8(txtFile);
        } catch (IOException e) {
            LOG.warn("Unable to strip unknown UTF8 characters from auto-transformed source",e);
        }
        
        return txtFile; 
    }
}
