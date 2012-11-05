package org.juxtasoftware;

import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.apache.log4j.PropertyConfigurator;
import org.juxtasoftware.util.MetricsHelper;
import org.juxtasoftware.util.QNameFilters;
import org.restlet.Component;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class JuxtaWS {

    public static ClassPathXmlApplicationContext context;

    public static void main(String[] args) throws Exception {
        
        // be sure to use the saxon parser
        System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");

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
        
        // init all common filters and public workspace
        ((QNameFilters)context.getBean(QNameFilters.class)).initialize();   
        ((MetricsHelper)context.getBean(MetricsHelper.class)).init();  
        LoggerFactory.getLogger(Constants.WS_LOGGER_NAME).info("Juxta Web service started");
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
