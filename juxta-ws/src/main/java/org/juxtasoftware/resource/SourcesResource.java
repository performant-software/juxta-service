package org.juxtasoftware.resource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadBase.FileSizeLimitExceededException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.util.EncodingUtils;
import org.juxtasoftware.util.HtmlUtils;
import org.juxtasoftware.util.MetricsHelper;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.fileupload.RestletFileUpload;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SourcesResource extends BaseResource {
    @Autowired private SourceDao sourceDao;
    @Autowired private Long maxSourceSize;
    @Autowired private MetricsHelper metrics;

    /**
     * Get Json representation of all available sources
     * @return
     */
    @Get("json")
    public Representation toJson() {
        List<Source> docs = this.sourceDao.list( this.workspace );
        Gson gson = new GsonBuilder()
            .registerTypeAdapter(Source.class, new SourcesSerializer())
            .create();
        return toJsonRepresentation( gson.toJson(docs) );
    }
    
    /**
     * Get HTML representation of all available sources
     * @return
     */
    @Get("html")
    public Representation toHtml() {
        List<Source> docs = this.sourceDao.list(this.workspace );
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("docs", docs);
        map.put("page", "source");
        map.put("title", "Juxta Sources");
        return toHtmlRepresentation("sources.ftl", map);
    }

   /**
    * Accept posts to create sources. Two types are supported; one is a 
    * multipart/form post consisting of a json header and a file stream. The 
    * header must contain two members: sourceName and contentType.
    * 
    * The other is a json array. Each entry is a json object with the following data:
    * name, type and data. Supported types are txt, xml and url. For txt and xml,
    * the data element contains the raw text or xml data. For url, the data contains
    * a url that will be scraped for source content.
    * 
    * These two types can be used together. In this case, the multipart/form would
    * contain 3 parts; jsonHeader, file stream, and a json array.
    * 
    * @param entity
    */
   @Post
   public Representation create( Representation entity  ) throws ResourceException {
       if ( entity == null ) {
           setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
           return toTextRepresentation("Missing source payload");
       }
       
       if (MediaType.MULTIPART_FORM_DATA.equals(entity.getMediaType(),true)) {
           return handleMutipartPost( entity);
       } else if ( MediaType.APPLICATION_JSON.equals(entity.getMediaType(),true)) {
           return handleJsonPost( entity );
       }
       
       setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
       return toTextRepresentation("Unsupported content type in post");
   }
   
    private Representation handleJsonPost(Representation entity)  {
        JsonParser parser = new JsonParser();
        try {
            Gson gson = new Gson();
            JsonArray jsonData = parser.parse( entity.getText()).getAsJsonArray();
            List<Long> idList = createSources(jsonData);
            return toJsonRepresentation( gson.toJson(idList) );
        } catch (Exception e) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation( e.getMessage() );
        }
    }

    private Representation handleMutipartPost(Representation entity) {
        String sourceName = null;
        String contentType = null;
        InputStream srcInputStream = null;
        try {
            // pull the list of items in this multipart request
            DiskFileItemFactory factory = new DiskFileItemFactory();
            factory.setSizeThreshold(1000240);
            RestletFileUpload upload = new RestletFileUpload(factory);
            List<FileItem> items = upload.parseRequest(getRequest());
            for ( FileItem item : items ) {
                if ( item.getFieldName().equals("sourceName")) {
                    sourceName = item.getString();
                } else if ( item.getFieldName().equals("contentType")) {
                    contentType = item.getString();
                } else if ( item.getFieldName().equals("sourceFile")) {
                    srcInputStream = item.getInputStream();
                } 
            }
            
            // validate that everything needed is present
            if ( srcInputStream == null  ) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Missing file data in post");
            }
            if ( sourceName == null || contentType == null ) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Missing name and/or content type information");
            }
        } catch (Exception e) {
            LOG.error("Unable to parse multipart data", e);
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST );
            return toTextRepresentation("File upload failed");
        }

        // prevent duplicate file names
        if (this.sourceDao.exists(this.workspace, sourceName)) {
            setStatus(Status.CLIENT_ERROR_CONFLICT);
            return toTextRepresentation("Source '" + sourceName + "' already exists in workspace '"
                + this.workspace.getName() + "'");
        }

        List<Long> idList = new ArrayList<Long>();
        try {
            // create the source
            Long id = createSource(sourceName, MediaType.valueOf(contentType), srcInputStream);
            idList.add( id );
        } catch ( FileSizeLimitExceededException e ) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            final String out = "Source size is "+e.getActualSize()/1024+
                "K.\nThis exceeds the Juxta size limit of "+this.maxSourceSize/1024+"K.\n\n"+
                "Try breaking the source into smaller segments and re-submitting.";
            return toTextRepresentation(out);
        } catch (IOException e) {
            setStatus(Status.SERVER_ERROR_INTERNAL);
            return toTextRepresentation("Unable to import source: " + e.getMessage());
        } catch (XMLStreamException e) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            String msg = e.getMessage();
            int pos = msg.indexOf(":");
            if (pos > -1) {
                msg = msg.substring(pos + 1).trim();
            }
            return toTextRepresentation("This document contains malformed xml - " + msg);
        }
         
        Gson gson = new Gson();
        return toJsonRepresentation( gson.toJson(idList) );
    }
    
    private List<Long> createSources(JsonArray jsonArray) throws Exception {
        List<Long> ids = new ArrayList<Long>();
        for (Iterator<JsonElement> itr = jsonArray.iterator(); itr.hasNext(); ) {
            JsonObject jsonObj = itr.next().getAsJsonObject();
            if ( jsonObj.has("type") == false) {
                throw new Exception("Missing information: type");
            }
            if ( jsonObj.has("contentType") == false) {
                throw new Exception("Missing information: contentType");
            }
            if ( jsonObj.has("name") == false) {
                throw new Exception("Missing information: name");
            }
            if ( jsonObj.has("data") == false) {
                throw new Exception("Missing information: data");
            }
            String type = jsonObj.get("type").getAsString();
            Source.Type contentType = Source.Type.valueOf( jsonObj.get("contentType").getAsString().toUpperCase() );
            String name = jsonObj.get("name").getAsString();
            String data = jsonObj.get("data").getAsString();
            
            if ( this.sourceDao.exists(this.workspace, name) ) {
                throw new Exception("Source \""+name+"\" already exists");
            }
            
            if ( contentType == null ) {
                throw new Exception("Unsupported content type");
            }
            
            if ( type.equalsIgnoreCase("url")) {
                ids.add( scrapeExternalUrl( name, data, contentType ) );
            } else if ( type.equalsIgnoreCase("raw") ) {
                ids.add( createSourceFromRawData( name, data, contentType) );
            }
        }
        return ids;  
    }
    
    private Long createSourceFromRawData(final String name, final String data, final Source.Type contentType) throws Exception {
        if ( this.maxSourceSize > 0 && data.length() > this.maxSourceSize ) {
            String err = "Source size is "+data.length()/1024+
                "K.\nThis exceeds the Juxta size limit of "+this.maxSourceSize/1024+"K.\n\n"+
                "Try breaking the source into smaller segments and re-submitting.";
            throw new Exception(err);
        }
        File fixed = EncodingUtils.fixEncoding( new ByteArrayInputStream(data.getBytes()) );        
        if ( contentType.equals(Source.Type.HTML) ) {
            HtmlUtils.strip(fixed);
        }
        return writeSourceData(fixed, name, contentType);
    }
    
    private Long writeSourceData( File srcFile, final String name, final Source.Type type )  throws IOException, XMLStreamException {
        FileInputStream fis = new FileInputStream(srcFile);
        InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
        Long id = this.sourceDao.create(this.workspace, name, type, isr);
        IOUtils.closeQuietly(isr);
        srcFile.delete();
        
        this.metrics.sourceAdded(this.workspace, this.sourceDao.find(this.workspace.getId(), id));
        
        return id;
    }

    private Long scrapeExternalUrl(final String name, final String url, final Source.Type contentType) throws Exception {
        HttpClient httpClient = newHttpClient();
        GetMethod get = new GetMethod(url);
        try {
            int result = httpClient.executeMethod(get);
            if (result != 200) {
                throw new IOException(result + " code returned for URL: " + url);
            }
            
            File srcFile = null;
            Source.Type finalType = contentType;
            if ( contentType.equals(Source.Type.PDF)) {
                finalType = Source.Type.TXT;
                srcFile = extractPdfText(name,  get.getResponseBodyAsStream());
            } else {
                srcFile = EncodingUtils.fixEncoding( get.getResponseBodyAsStream() );
                if ( contentType.equals(Source.Type.HTML)) {
                    HtmlUtils.strip(srcFile);
                }
            }
            
            if ( this.maxSourceSize > 0 && srcFile.length() > this.maxSourceSize ) {
                String err = "Source size is "+srcFile.length()/1024+
                    "K.\nThis exceeds the Juxta size limit of "+this.maxSourceSize/1024+"K.\n\n"+
                    "Try breaking the source into smaller segments and re-submitting.";
                throw new Exception(err);
            }
            return writeSourceData(srcFile, name, finalType);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            get.releaseConnection();
        } 
    }

    private HttpClient newHttpClient() {
        final int REQUEST_TIMEOUT = 2 * 60 * 1000;   // 2 secs
        HttpClient httpClient = new HttpClient();
        httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(
            REQUEST_TIMEOUT);
        httpClient.getHttpConnectionManager().getParams().setIntParameter(
            HttpMethodParams.BUFFER_WARN_TRIGGER_LIMIT, 10000 * 1024); 
        return httpClient;
    }


    private Long createSource(final String sourceName, final MediaType mediaType, InputStream srcInputStream) throws IOException, XMLStreamException, FileSizeLimitExceededException {
        File srcFile = null;
        Source.Type contentType = Source.Type.TXT;
        if ( MediaType.APPLICATION_PDF.isCompatible(mediaType)) {
            srcFile = extractPdfText(sourceName, srcInputStream);
        } else {
            srcFile = EncodingUtils.fixEncoding(srcInputStream);
            if ( MediaType.TEXT_XML.isCompatible( mediaType ) ) {
                contentType = Source.Type.XML;
            } else if ( MediaType.TEXT_HTML.isCompatible( mediaType ) ) {
                contentType = Source.Type.HTML;
                HtmlUtils.strip(srcFile);
            } else {
                if ( sourceName.endsWith(".wiki")) {
                    contentType = Source.Type.WIKI;
                }
            }
        }
    
        if ( this.maxSourceSize > 0 && srcFile.length() > this.maxSourceSize ) {
            throw new FileSizeLimitExceededException(sourceName+" too big",srcFile.length(), this.maxSourceSize );
        }
        
        return writeSourceData(srcFile, sourceName, contentType);
    }

    private File extractPdfText(final String sourceName, InputStream srcInputStream) throws IOException {
        OutputStreamWriter osw = null;
        PDDocument pdfDoc = null;
        File srcFile = null;
        try {
            LOG.info("Beginning extraction of PDF text content for "+sourceName+"...");
            srcFile = File.createTempFile("pdf", "dat");
            srcFile.deleteOnExit();
            osw = new OutputStreamWriter(new FileOutputStream( srcFile ), "UTF-8" );
            pdfDoc = PDDocument.load(srcInputStream);
            PDFTextStripper pdfStrip = new PDFTextStripper("UTF-8");
            pdfStrip.writeText(pdfDoc, osw);
            LOG.info("PDF text content for "+sourceName+" EXTRACTED");
        } finally {
            IOUtils.closeQuietly(osw);
            if ( pdfDoc != null ) {
                pdfDoc.close();
            }
        }
        return srcFile;
    }

    private class SourcesSerializer implements JsonSerializer<Source> {
        private final DateFormat format = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        
        @Override
        public JsonElement serialize(Source src, Type typeOfSrc, JsonSerializationContext context) {
            
            JsonObject  obj = new JsonObject();
            obj.add("id", new JsonPrimitive(src.getId()) );
            obj.add("name", new JsonPrimitive(src.getName()));
            obj.add("type", new JsonPrimitive(src.getType().toString()));
            obj.add("length", new JsonPrimitive(src.getText().getLength()));
            obj.add("created", new JsonPrimitive( this.format.format(src.getCreated())));
            return obj;
        }

    }
}
