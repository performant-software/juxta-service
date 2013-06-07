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
        
        String authUser = (String)context.getBean("authenticatorUser");
        String authPass = (String)context.getBean("authenticatorPass");
        if ( (Boolean)context.getBean("useAuthenticator") == true && (authPass.length()==0 || authUser.length()==0)) {
            String msg = "Juxta WS is running in AUTHENTICATED mode, but credientials are not set";
            LoggerFactory.getLogger(Constants.WS_LOGGER_NAME).error(msg);
            System.exit(0);
        }
        
        // init all common filters and public workspace
        ((QNameFilters)context.getBean(QNameFilters.class)).initialize(); 
        if ( (Boolean)context.getBean("captureMetrics") ) {
            ((MetricsHelper)context.getBean(MetricsHelper.class)).init();
        }
        LoggerFactory.getLogger(Constants.WS_LOGGER_NAME).info("Juxta Web service started");
        
        if ( (Boolean)context.getBean("useAuthenticator") == false ) {
            String msg = "*** Juxta WS is running in NON-AUTHENTICATED mode, and is viewable/editabe by anyone ***";
            LoggerFactory.getLogger(Constants.WS_LOGGER_NAME).info(msg);
            System.out.println(msg);
        }
        
        if ( (Boolean)context.getBean("captureMetrics") == false ) {
            String msg = "*** Juxta WS not capturing usage metrics ***";
            LoggerFactory.getLogger(Constants.WS_LOGGER_NAME).info(msg);
            System.out.println(msg);
        }
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
