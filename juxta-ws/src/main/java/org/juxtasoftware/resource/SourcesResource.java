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
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadBase.FileSizeLimitExceededException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpProtocolParams;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.service.SourceRemover;
import org.juxtasoftware.util.ConversionUtils;
import org.juxtasoftware.util.EncodingUtils;
import org.juxtasoftware.util.HtmlUtils;
import org.juxtasoftware.util.MetricsHelper;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.fileupload.RestletFileUpload;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
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
    @Autowired private SourceRemover remover;
    private boolean batchDelete;
    
    @Override
    protected void doInit() throws ResourceException { 
        super.doInit();
        String lastSeg  = getRequest().getResourceRef().getLastSegment().toLowerCase();
        this.batchDelete =  lastSeg.equals("delete");
    }
    
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
    
    @Delete("json")
    public Representation batchDelete( final String jsonContent) {
        LOG.info("Batch delete sources "+jsonContent);
        JsonParser parser = new JsonParser();
        JsonArray jsonArray = parser.parse(jsonContent).getAsJsonArray();
        Set<Usage> usage = new HashSet<Usage>();
        for ( Iterator<JsonElement>  itr = jsonArray.iterator(); itr.hasNext(); ) {
            JsonElement ele = itr.next();
            Long id = ele.getAsLong();
            Source s = this.sourceDao.find(this.workspace.getId(), id);
            if ( s != null ) {
                try {
                    usage.addAll( this.remover.removeSource(this.workspace, s));
                } catch ( ResourceException e ) {
                    LOG.warn(e.toString());
                }
            } else {
                LOG.warn("Source ID "+id+" is not a valid source for this workspace");
            }
        }
        Gson gson = new Gson();
        return toJsonRepresentation( gson.toJson(usage) );
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
        if ( this.batchDelete ) {
            try {
                return batchDelete(entity.getText());
            } catch (IOException e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("Invalid delete data");
            }
        }
        
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
                    if ( UrlValidator.getInstance().isValid(data)) {
                        // pull content from the URL. Type will be determined from
                        // the HTTP response
                        ids.add( scrapeExternalUrl(name, data) );
                    } else {
                        setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                        return toTextRepresentation("Malformed source URL");
                    }
                } else if (type.equalsIgnoreCase("raw")) {
                    if (jsonObj.has("contentType") == false) {
                        setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                        return toTextRepresentation("Missing required information: contentType");
                    }
                    Source.Type contentType = Source.Type.valueOf(jsonObj.get("contentType").getAsString().toUpperCase());
                    ids.add( createSourceFromRawData(name, data, contentType) );
                }
            } catch (HttpResponseException e) {
                LOG.error("Link to source "+data+" failed", e);
                setStatus( Status.valueOf(e.getStatusCode()));
                if ( e.getStatusCode() == 403 ) {
                    String msg = "The target web site is not allowing Juxta to access its content.\n"
                        +"To work around this, download the content to your local system,\nthen upload it using option #1.";
                    return toTextRepresentation(msg);
                } else {
                    return toTextRepresentation("Link to "+data+" failed: "+e.getMessage());
                }
            } catch (UnknownHostException e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return toTextRepresentation("The URL contains an unknown host");
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
        else {
            // General case: auto transform to TXT
            try {
                srcFile = ConversionUtils.convertToText(srcInputStream);
            } catch (Exception e) {
                LOG.error("Unable to convert "+sourceName+" "+mediaType+" to text", e);
                throw new IOException(e.getMessage());
            }
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
    private Long writeSourceData(File srcFile, final String name, final Source.Type type) throws DuplicateSourceException, IOException, XMLStreamException {
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

    private Long scrapeExternalUrl(final String name, final String url) throws HttpResponseException, IOException, XMLStreamException, DuplicateSourceException {
        
        // get the contents of the URL and store them in rawFile
        HttpClient httpClient = new DefaultHttpClient();
        HttpProtocolParams.setUserAgent(httpClient.getParams(), "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_5) AppleWebKit/537.17 (KHTML, like Gecko) Chrome/24.0.1312.57 Safari/537.17");
        HttpGet get = new HttpGet(url);
        ResponseHandler<String> responseHandler = new BasicResponseHandler();
        String response = httpClient.execute(get, responseHandler);
        File rawFile = File.createTempFile("url", "dat");
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(rawFile), "UTF-8");
        IOUtils.write(response, osw);
        IOUtils.closeQuietly(osw);

        // prepare source for addition to library
        File srcFile = null;
        MediaType mediaType = ConversionUtils.determineMediaType(rawFile);
        if (MediaType.TEXT_XML.isCompatible(mediaType) || 
            MediaType.APPLICATION_XML.isCompatible(mediaType) || 
            MediaType.TEXT_HTML.isCompatible(mediaType) || 
            MediaType.TEXT_PLAIN.isCompatible(mediaType)) {
            
            try {
                srcFile = EncodingUtils.fixEncoding(new FileInputStream(rawFile));
                if (MediaType.TEXT_HTML.isCompatible(mediaType)) {
                    HtmlUtils.strip(srcFile);
                }
            } finally {
                rawFile.delete();
            }
        } else {
            mediaType = MediaType.TEXT_PLAIN;
            try {
                srcFile = ConversionUtils.convertToText(new FileInputStream(rawFile));
            } catch (Exception e) {
                throw new IOException(e.getMessage());
            } finally {
                rawFile.delete();
            }
        }

        // Convert media type to Source Type
        Source.Type srcType = Source.Type.TXT;
        if (MediaType.TEXT_XML.isCompatible(mediaType) ||
            MediaType.APPLICATION_XML.isCompatible(mediaType) ) {
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
