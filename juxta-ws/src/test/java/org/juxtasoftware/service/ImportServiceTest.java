package org.juxtasoftware.service;

import java.io.InputStream;
import java.io.StringReader;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Workspace;
import org.juxtasoftware.service.importer.jxt.JxtImportServiceImpl;
import org.juxtasoftware.service.importer.ps.ParallelSegmentationImportImpl;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.io.Closeables;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class ImportServiceTest extends AbstractTest {

    @Autowired private JxtImportServiceImpl jxtImportService;
    @Autowired private ParallelSegmentationImportImpl psImportService;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private WorkspaceDao workspaceDao;
    @Autowired private SourceDao sourceDao;
   

    @Before
    public void setup() throws Exception {
        System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");
        Workspace ws = this.workspaceDao.getPublic();
        if ( ws == null ) {
            ws = new Workspace();
            ws.setName("public");
            ws.setDescription("Default public workspace");
            Long id = this.workspaceDao.create(ws);
            ws.setId( id );
        }
    }

    
    @Test
    public void importFile() throws Exception {
        final Workspace ws = this.workspaceDao.getPublic();
        ComparisonSet set = new ComparisonSet();
        set.setName("jxt-unit-test-set");
        set.setWorkspaceId( ws.getId() );
        Long id = this.setDao.create(set);
        set.setId(id);
        
        InputStream data = null;
        try {
            BackgroundTaskStatus s= new BackgroundTaskStatus( "test1");
            data = getClass().getResourceAsStream("/old.jxt");
            jxtImportService.doImport(set, data, s );
            throw new Exception("Invalid accept of old file");
        } catch (Exception e) {
            // no-op, this is expected here
        } finally {
            Closeables.close(data, false);
        }
        
        try {
            BackgroundTaskStatus s= new BackgroundTaskStatus( "test2");
            data = getClass().getResourceAsStream("/welcome.jxt");
            jxtImportService.doImport(set, data, s);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            Closeables.close(data, false);
        }
    }
    
    
    @Test
    public void importTei() throws Exception {
       
        final Workspace ws = this.workspaceDao.getPublic();
        ComparisonSet set = new ComparisonSet();
        set.setName("tei-ps-unit-test-set");
        set.setWorkspaceId( ws.getId() );
        Long id = this.setDao.create(set);
        set.setId(id);
        
        InputStream is = null;
        try {
            BackgroundTaskStatus s= new BackgroundTaskStatus( "test1");
            is = getClass().getResourceAsStream("/autumn.xml");
            String data = IOUtils.toString(is);
            Long srcId = this.sourceDao.create(ws, "tei-ps-unit-test-autumn", Source.Type.XML, new StringReader(data));
            Source teiSrc = this.sourceDao.find(ws.getId(), srcId);
            
            psImportService.doImport(set, teiSrc, s );
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            Closeables.close(is, false);
        }
    }
}
