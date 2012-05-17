package org.juxtasoftware;

import java.io.InputStream;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.PropertyConfigurator;
import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.dao.impl.JuxtaXsltDaoImpl;
import org.juxtasoftware.dao.impl.WorkspaceDaoImpl;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Workspace;
import org.juxtasoftware.util.QNameFilters;
import org.restlet.Component;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class JuxtaWS {

    public static ClassPathXmlApplicationContext context;

    public static void main(String[] args) throws Exception {

        // initialize application context
        initApplicationContext();
        
        // kill the console loggers and rely on logging from 
        // these classes only
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        rootLogger.removeHandler(handlers[0]);
        
        // Start the Restlet component
        Component component = (Component) context.getBean("top");
        component.start();
        
        // init all common filters
        ((QNameFilters)context.getBean(QNameFilters.class)).initialize();
        
        // create public workspace
        WorkspaceDao wsDao = context.getBean(WorkspaceDaoImpl.class);
        Workspace publicWs = wsDao.getPublic();
        
        // add thetag stripper xslt
        JuxtaXsltDao xsltDao =  context.getBean(JuxtaXsltDaoImpl.class);
        JuxtaXslt stripper = xsltDao.getTagStripper();
        if ( stripper == null ) {
            stripper = new JuxtaXslt();
            stripper.setName("Generic Tag Stripper");
            stripper.setWorkspaceId(publicWs.getId());
            InputStream is = ClassLoader.getSystemResourceAsStream("tag_stripper.xslt");
            stripper.setXslt( IOUtils.toString(is, "utf-8"));
            xsltDao.create(stripper);
            
        }
        
        LoggerFactory.getLogger("").info("Juxta Web service started");
    }

    private static void initApplicationContext() {
        PropertyConfigurator.configure("config/log4j.properties");
        
        JuxtaWS.context = new ClassPathXmlApplicationContext(new String[]{
            "applicationContext-dataSource.xml", 
            "/applicationContext-service.xml", 
            "/applicationContext-restlet.xml"});
        JuxtaWS.context.registerShutdownHook();
    }
}
