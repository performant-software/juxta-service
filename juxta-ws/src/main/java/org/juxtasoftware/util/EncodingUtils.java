package org.juxtasoftware.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import org.apache.commons.io.IOUtils;
import org.mozilla.intl.chardet.nsDetector;
import org.mozilla.intl.chardet.nsICharsetDetectionObserver;
import org.mozilla.universalchardet.UniversalDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EncodingUtils {

    private static final Logger LOG = LoggerFactory.getLogger( "EncodingUtils" );
    
    /**
     * Normalize the content to UTF-8 and strip any tags that say otherwise
     * @return A file containing the UTF-8 contents
     * @throws IOException 
     */
    public static File fixEncoding( InputStream source, boolean isXml ) throws IOException {
        File tmpSrc = File.createTempFile("src", "dat");
        FileOutputStream fos =  new FileOutputStream(tmpSrc);
        IOUtils.copy(source, fos);
        IOUtils.closeQuietly(fos);
        
        String encoding = detectEncoding(tmpSrc);
        if ( encoding.equalsIgnoreCase("UTF-8") == false) {
            LOG.info("Converting from "+encoding+" to UTF-8");
            
            // read from original encoding into 16-bit unicode
            FileInputStream fis =  new FileInputStream(tmpSrc);
            String nonUtf8Txt =  IOUtils.toString(fis,encoding);
            IOUtils.closeQuietly(fis);
            tmpSrc.delete();
            
            // setup encoders to translate the data. IF bad chars
            // are encountered, replace them with 0xFFFD (unkown utf-8 symbol)
            Charset utf8cs = Charset.availableCharsets().get("UTF-8");
            CharsetEncoder utf8en = utf8cs.newEncoder();
            utf8en.onMalformedInput(CodingErrorAction.REPLACE);
            utf8en.onUnmappableCharacter(CodingErrorAction.REPLACE);
            
            // encode the 16-bit unicode to UTF-8 and write out the bytes
            ByteBuffer utf8Buffer = utf8en.encode(CharBuffer.wrap(nonUtf8Txt));

            fos = new FileOutputStream(tmpSrc);
            WritableByteChannel channel = Channels.newChannel(fos);
            channel.write(utf8Buffer);
            fos.close();
            
            // lastly, if this is xml, be sure encoding says "utf-8" (if one is present)
            if ( isXml ) {
                updateXmlEncodingDeclaration(tmpSrc);
            }
        }

        return tmpSrc;
    }
    
    private static void updateXmlEncodingDeclaration(File tmpSrc) throws IOException {
        BufferedReader r = new BufferedReader( new FileReader(tmpSrc ));
        File out = File.createTempFile("fix", "dat");
        FileOutputStream fos = new FileOutputStream(out);
        boolean pastHeader = false;
        while (true) {
            String line = r.readLine();
            if ( line != null ) {
                if ( pastHeader == false && line.contains("<?xml") ) {
                    pastHeader = true;
                    int pos = line.indexOf("encoding");
                    if ( pos > -1 ) {
                        pos = line.indexOf("\"", pos);
                        int end = line.indexOf("\"", pos+1);
                        String old = line.substring(pos+1,end);
                        line = line.replace(old, "utf-8");
                    }
                }
                line += "\n";
                fos.write(line.getBytes());
            } else {
                break;
            }
        }
        IOUtils.closeQuietly(fos);
        IOUtils.copy(new FileInputStream(out), new FileOutputStream(tmpSrc));
    }

    private static String detectEncoding(File srcFile) throws IOException {
        
        // feed chunks of data to the detector until it is done
        UniversalDetector detector = new UniversalDetector(null);
        byte[] buf = new byte[4096];
        int nread;
        String encoding = "utf-8";
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(srcFile);
            while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, nread);
            }
            detector.dataEnd();
            IOUtils.closeQuietly(fis);
            encoding = detector.getDetectedCharset();
            if (encoding == null) {
                encoding =  alternateEncodeDetect(srcFile);
                if ( encoding == null ){
                    LOG.error("Unable to detect encoding, default to utf-8");
                    encoding = "utf-8";
                }
            }   
        } catch (IOException e ) {
            LOG.error("Encoding detection failed", e);
            throw new IOException("Error detecting encoding", e);
        } finally {
            IOUtils.closeQuietly(fis);
        }
        return encoding;
    }
    
    private static String alternateEncodeDetect(File testFile) throws IOException {
        nsDetector det = new nsDetector();
        DetectListener listener = new DetectListener();
        det.Init( listener );

        BufferedInputStream imp = new BufferedInputStream(new FileInputStream(testFile));
        byte[] buf = new byte[1024];
        int len;
        boolean done = false;
        boolean isAscii = true;
        while ((len = imp.read(buf, 0, buf.length)) != -1) {
            if (isAscii) {
                isAscii = det.isAscii(buf, len);
            }
            if (!isAscii && !done) {
                done = det.DoIt(buf, len, false);
            }
        }
        det.DataEnd();
        imp.close();
        return listener.getEncoding();
    }
    
    private static class DetectListener implements nsICharsetDetectionObserver {
        private String encoding;
        public String getEncoding() {
            return this.encoding;
        }
        
        public void Notify(String charset) {
            this.encoding = charset;
        }
        
    }
}
