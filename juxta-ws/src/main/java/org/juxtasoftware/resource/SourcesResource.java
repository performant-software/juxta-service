package org.juxtasoftware.resource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.util.ConversionUtils;
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
    @Autowired
    private SourceDao sourceDao;
    @Autowired
    private Long maxSourceSize;
    @Autowired
    private MetricsHelper metrics;

    /**
     * Get Json representation of all available sources
     * @return
     */
    @Get("json")
    public Representation toJson() {
        List<Source> docs = this.sourceDao.list(this.workspace);
        Gson gson = new GsonBuilder().registerTypeAdapter(Source.class, new SourcesSerializer()).create();
        return toJsonRepresentation(gson.toJson(docs));
    }

    /**
     * Get HTML representation of all available sources
     * @return
     */
    @Get("html")
    public Representation toHtml() {
        List<Source> docs = this.sourceDao.list(this.workspace);
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
    public Representation create(Representation entity) throws ResourceException {
        if (entity == null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Missing source payload");
        }

        if (MediaType.MULTIPART_FORM_DATA.equals(entity.getMediaType(), true)) {
            return handleMutipartPost(entity);
        } else if (MediaType.APPLICATION_JSON.equals(entity.getMediaType(), true)) {
            return handleJsonPost(entity);
        }

        setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
        return toTextRepresentation("Unsupported content type in post");
    }

    /**
     * Create sources based on a POST of JSON data. This post can either contain the raw
     * source text or a URL that points to the source to be added
     * 
     * @param entity
     * @return
     */
    private Representation handleJsonPost(Representation entity) {
        // parse request into a JSON array
        JsonParser parser = new JsonParser();
        Gson gson = new Gson();
        JsonArray jsonArray = null;
        try {
            jsonArray = parser.parse(entity.getText()).getAsJsonArray();
        } catch (Exception e) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("Invalid JSON data in request");
        } 
        
        List<Long> ids = new ArrayList<Long>();
        for (Iterator<JsonElement> itr = jsonArray.iterator(); itr.hasNext();) {
            JsonObject jsonObj = itr.next().getAsJsonObject();
            
            // make sure all necessary data is present
            if (jsonObj.has("type") == false) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Missing required information: type");
            }
            if (jsonObj.has("name") == false) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Missing required information: name");
            }
            if (jsonObj.has("data") == false) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Missing required information: data");
            }
            
            String type = jsonObj.get("type").getAsString();
            String name = jsonObj.get("name").getAsString();
            String data = jsonObj.get("data").getAsString();

            try {
                if (type.equalsIgnoreCase("url")) {
                    // pull content from the URL. Type will be determined from
                    // the HTTP response
                    ids.add( scrapeExternalUrl(name, data) );
                } else if (type.equalsIgnoreCase("raw")) {
                    if (jsonObj.has("contentType") == false) {
                        setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                        return toTextRepresentation("Missing required information: contentType");
                    }
                    Source.Type contentType = Source.Type.valueOf(jsonObj.get("contentType").getAsString().toUpperCase());
                    ids.add( createSourceFromRawData(name, data, contentType) );
                }
            } catch (IOException e) {
                setStatus(Status.SERVER_ERROR_INTERNAL);
                return toTextRepresentation("Unable to create source "+name+": "+e.toString());
            } catch (XMLStreamException e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Source "+name+" contains invalid XML: "+e.toString());
            } catch (DuplicateSourceException e) {
                setStatus(Status.CLIENT_ERROR_CONFLICT);
                return toTextRepresentation("Source '" + name + "' already exists in workspace '"
                    + this.workspace.getName() + "'");
            }
        }
        return toJsonRepresentation(gson.toJson(ids)); 
    }

    /**
     * Create sources from a multipart POST
     * @param entity
     * @return
     */
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
            for (FileItem item : items) {
                if (item.getFieldName().equals("sourceName")) {
                    sourceName = item.getString();
                } else if (item.getFieldName().equals("contentType")) {
                    contentType = item.getString();
                } else if (item.getFieldName().equals("sourceFile")) {
                    srcInputStream = item.getInputStream();
                }
            }

            // validate that everything needed is present
            if (srcInputStream == null) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Missing file data in post");
            }
            if (sourceName == null || contentType == null) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Missing name and/or content type information");
            }
        } catch (Exception e) {
            LOG.error("Unable to parse multipart data", e);
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            return toTextRepresentation("File upload failed");
        }

        List<Long> idList = new ArrayList<Long>();
        try {
            // create the source
            Long id = createSource(sourceName, MediaType.valueOf(contentType), srcInputStream);
            idList.add(id);
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
        } catch (DuplicateSourceException e) {
            setStatus(Status.CLIENT_ERROR_CONFLICT);
            return toTextRepresentation("Source '" + e.getSourceName() + "' already exists in workspace '"
                + this.workspace.getName() + "'");
        }

        Gson gson = new Gson();
        return toJsonRepresentation(gson.toJson(idList));
    }
    
    /**
     * Create a source from a multipart data stream
     * @param sourceName
     * @param mediaType
     * @param srcInputStream
     * @return
     * @throws IOException
     * @throws XMLStreamException
     * @throws FileSizeLimitExceededException
     * @throws DuplicateSourceException 
     */
    private Long createSource(final String sourceName, final MediaType mediaType, InputStream srcInputStream) throws IOException, XMLStreamException, DuplicateSourceException {
        File srcFile = null;
        Source.Type contentType = Source.Type.TXT;
        
        // Special handling for files that will not be auto transformed:
        // be sure they are UTF-8 and set type flags. Strip scary stuff out
        // of HTML files
        if ( mediaType.equals(MediaType.APPLICATION_XML) ||  
             mediaType.equals(MediaType.TEXT_XML) ||
             mediaType.equals(MediaType.TEXT_HTML) ||
             mediaType.equals(MediaType.TEXT_PLAIN) || 
             sourceName.endsWith(".wiki") ) {
            
            srcFile = EncodingUtils.fixEncoding(srcInputStream);
            if ( mediaType.equals(MediaType.APPLICATION_XML) ||  mediaType.equals(MediaType.TEXT_XML)) {
                contentType = Source.Type.XML;
            } else if ( mediaType.equals(MediaType.TEXT_HTML) ) {
                contentType = Source.Type.HTML;
                HtmlUtils.strip(srcFile);
            } else {
                if ( sourceName.endsWith(".wiki")) {
                    contentType = Source.Type.WIKI;
                }
            }
            
        }
        else if ( ConversionUtils.canConvert( mediaType )) {
            // General case: auto transform to TXT
            srcFile = ConversionUtils.convertToText(srcInputStream);
        } else {
            throw new IOException("Unsupported file type");   
        }
   
        return writeSourceData(srcFile, sourceName, contentType);
    }

    /**
     * Create a source from the string data passed along with the request. This can create sources that
     * are based on ascii text: HTML, TXT and XML.
     * 
     * @param name
     * @param data
     * @param contentType
     * @return
     * @throws DuplicateSourceException 
     * @throws XMLStreamException 
     * @throws IOException 
     * @throws Exception
     */
    private Long createSourceFromRawData(final String name, final String data, final Source.Type contentType) throws IOException, XMLStreamException, DuplicateSourceException {
        File fixed = EncodingUtils.fixEncoding(new ByteArrayInputStream(data.getBytes()));
        if (contentType.equals(Source.Type.HTML)) {
            HtmlUtils.strip(fixed);
        }
        return writeSourceData(fixed, name, contentType);
    }

    /**
     * Create a new source with the sprcified type and name. 
     * 
     * @param srcFile
     * @param name
     * @param type
     * @return
     * @throws IOException
     * @throws XMLStreamException
     * @throws DuplicateSourceException 
     */
    private Long writeSourceData(File srcFile, final String name, final Source.Type type) throws IOException,
        XMLStreamException, DuplicateSourceException {
        if (this.maxSourceSize > 0 && srcFile.length() > this.maxSourceSize) {
            String err = "Source size is " + srcFile.length() / 1024 + "K.\nThis exceeds the Juxta size limit of "
                + this.maxSourceSize / 1024 + "K.\n\n"
                + "Try breaking the source into smaller segments and re-submitting.";
            throw new IOException(err);
        }

        String finalName = appendExtension(name, type);
        
        // prevent duplicate file names
        if (this.sourceDao.exists(this.workspace, finalName)) {
            throw new DuplicateSourceException(finalName);
        }
        
        FileInputStream fis = new FileInputStream(srcFile);
        InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
        Long id = this.sourceDao.create(this.workspace, finalName, type, isr);
        IOUtils.closeQuietly(isr);
        srcFile.delete();

        this.metrics.sourceAdded(this.workspace, this.sourceDao.find(this.workspace.getId(), id));

        return id;
    }

    private Long scrapeExternalUrl(final String name, final String url) throws IOException, XMLStreamException, DuplicateSourceException {
        
        // get the contents of the URL and store them in rawFile
        HttpClient httpClient = newHttpClient();
        GetMethod get = new GetMethod(url);
        File rawFile = null;
        FileOutputStream fos = null;
        try {
            int result = httpClient.executeMethod(get);
            if (result != 200) {
                throw new IOException(result + " code returned for URL: " + url);
            }

            rawFile = File.createTempFile("url", "dat");
            rawFile.deleteOnExit();
            fos = new FileOutputStream(rawFile);
            IOUtils.copy(get.getResponseBodyAsStream(), fos);
            
        } catch (IOException e) {
            throw new IOException("Unable to retriece content of URL", e);
        } finally {
            IOUtils.closeQuietly(fos);
            get.releaseConnection();
        }

        // prepare source for addition to library
        File srcFile = null;
        MediaType mediaType = ConversionUtils.determineMediaType(rawFile);
        if (MediaType.TEXT_XML.isCompatible(mediaType) || 
            MediaType.APPLICATION_XML.isCompatible(mediaType) || 
            MediaType.TEXT_HTML.isCompatible(mediaType) || 
            MediaType.TEXT_PLAIN.isCompatible(mediaType)) {
            
            srcFile = EncodingUtils.fixEncoding(new FileInputStream(rawFile));
            if (MediaType.TEXT_HTML.isCompatible(mediaType)) {
                HtmlUtils.strip(srcFile);
            }
        } else {
            mediaType = MediaType.TEXT_PLAIN;
            srcFile = ConversionUtils.convertToText(new FileInputStream(rawFile));
        }
        rawFile.delete();

        // Convert media type to Source Type
        Source.Type srcType = Source.Type.TXT;
        if (MediaType.TEXT_XML.isCompatible(mediaType)) {
            srcType = Source.Type.XML;
        } else if (MediaType.TEXT_HTML.isCompatible(mediaType)) {
            srcType = Source.Type.HTML;
        }

        // dump results and enforce limits / uniqueness
        return writeSourceData(srcFile, name, srcType);
    }

    private String appendExtension(String name, org.juxtasoftware.model.Source.Type srcType) {
        // make sure theres some kind of extension
        String finalName = name;
        String lcName = name.toLowerCase();
        if ( lcName.endsWith(".txt") == false && lcName.endsWith(".html") == false && lcName.endsWith(".htm") == false &&
             lcName.endsWith(".xml") == false && lcName.endsWith(".wiki") == false  ) {
            finalName = name + "." +srcType.toString().toLowerCase();
        }
        return finalName;
    }

    private HttpClient newHttpClient() {
        final int REQUEST_TIMEOUT = 2 * 60 * 1000; // 2 secs
        HttpClient httpClient = new HttpClient();
        httpClient.getHttpConnectionManager().getParams().setConnectionTimeout(REQUEST_TIMEOUT);
        httpClient.getHttpConnectionManager().getParams()
            .setIntParameter(HttpMethodParams.BUFFER_WARN_TRIGGER_LIMIT, 10000 * 1024);
        return httpClient;
    }

    private class SourcesSerializer implements JsonSerializer<Source> {
        private final DateFormat format = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

        @Override
        public JsonElement serialize(Source src, Type typeOfSrc, JsonSerializationContext context) {

            JsonObject obj = new JsonObject();
            obj.add("id", new JsonPrimitive(src.getId()));
            obj.add("name", new JsonPrimitive(src.getName()));
            obj.add("type", new JsonPrimitive(src.getType().toString()));
            obj.add("length", new JsonPrimitive(src.getText().getLength()));
            obj.add("created", new JsonPrimitive(this.format.format(src.getCreated())));
            return obj;
        }

    }
    
    private static class DuplicateSourceException extends Exception {
        private static final long serialVersionUID = 8890164370720970377L;
        private final String sourceName;
        public DuplicateSourceException(String name) {
            super();
            this.sourceName = name;
        }
        public String getSourceName() {
            return this.sourceName;
        }
    }
}
