package org.juxtasoftware.util.ftl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import freemarker.core.Environment;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

/**
 * Custom FTL directive to stream a file into the body. That file
 * must be set as the src param of the directive call. Optionally, it will
 * look for <code>srcRangeStart</code> and <code>srcRangeEnd</code>.
 * If both are set, the data read from the source will be constrained
 * to this range.
 * 
 * @author loufoster
 *
 */
public class FileDirective implements TemplateDirectiveModel {
    
    private FileDirectiveListener listener;
    
    public void setListener( FileDirectiveListener listener ) {
        this.listener = listener;
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] model, TemplateDirectiveBody body)
        throws TemplateException, IOException {
        
        // Error check and collect necessary data:
        if (params.containsKey("src") == false ) {
            throw new TemplateModelException(
                    "This directive requires a src paramater");
        }
        
        // OPTIONAL startRange
        TemplateModel startVal = env.getVariable("srcRangeStart");
        Integer start = null;
        if (startVal != null ) {
            start = Integer.parseInt(startVal.toString());
        }
        
        // OPTIONAL endRange
        TemplateModel endVal = env.getVariable("srcRangeEnd");
        Integer end = null;
        if (endVal != null ) {
            end = Integer.parseInt(endVal.toString());
        }
        
        if ((start == null && end != null) || 
            (start != null && end == null) ) {
            throw new TemplateModelException(
                "Incomplete range: Only one of srcRangeStart/srcRangeEnd pair specified");
        }
        
        // Stream in the data
        InputStreamReader isr = null;
        final SimpleScalar scalarValue = (SimpleScalar)params.get("src");
        final String srcFile = scalarValue.getAsString();
        File src = new File( srcFile );
        try {
            Writer out = env.getOut();
            FileInputStream fis = new FileInputStream(src);
            isr = new InputStreamReader(fis, "UTF-8");
            int pos = 0;
            while (true) {
                int data = isr.read();
                if (data == -1 ) {
                    break;
                } else {
                    // is a range defined?
                    if ( start != null && end != null ) {
                        // only write out data in range
                        if (  pos >= start && pos <= end ) {
                            out.write( data );
                        }
                    } else {
                        // write evertything
                        out.write( data );
                    }
                    pos++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally  {
            IOUtils.closeQuietly(isr);
        }
        
        // notify others that we are done with the file
        if ( this.listener != null ) {
            this.listener.fileReadComplete(src);
        }
    }
    
}