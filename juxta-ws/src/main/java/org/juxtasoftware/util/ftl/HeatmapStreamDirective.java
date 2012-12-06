package org.juxtasoftware.util.ftl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

import org.juxtasoftware.dao.CacheDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import freemarker.core.Environment;
import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

/**
 * A custom freemarker directive used to stream content from the
 * web service heatmap database to the template
 * 
 * @author loufoster
 *
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class HeatmapStreamDirective implements TemplateDirectiveModel {
    @Autowired private CacheDao cacheDao;
    
    @SuppressWarnings("rawtypes")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] model, TemplateDirectiveBody body)
        throws TemplateException, IOException {
        
        // REQUIRED set id
        TemplateModel val = env.getVariable("setId");
        if ( val == null ) {
            throw new TemplateModelException("Missing required setId variable");
        }
        Long setId = Long.parseLong( val.toString());
        
        // REQUIRED visualization key
        val = env.getVariable("visualizationKey");
        if ( val == null ) {
            throw new TemplateModelException("Missing required visualizationKey variable");
        }
        Long key = Long.parseLong( val.toString());
        
        // REQUIRED condensed flag
        val = env.getVariable("condensed");
        if ( val == null ) {
            throw new TemplateModelException("Missing required condensed variable");
        }
        TemplateBooleanModel boolModel = (TemplateBooleanModel)val;
        boolean condensed = boolModel.getAsBoolean();
        
        // OPTIONAL starting line
        TemplateModel startVal = env.getVariable("startLine");
        Integer start = null;
        if (startVal != null ) {
            start = Integer.parseInt(startVal.toString());
        }
        
        // OPTIONAL end line
        TemplateModel endVal = env.getVariable("endLine");
        Integer end = null;
        if (endVal != null ) {
            end = Integer.parseInt(endVal.toString());
        }
        
        if ((start == null && end != null) || 
            (start != null && end == null) ) {
            throw new TemplateModelException(
                "Incomplete range: Only one of startLine/endLine pair specified");
        }
        
        Writer out = env.getOut();
        Reader reader = this.cacheDao.getHeatmap(setId, key, condensed);
        int currLine = 0;
        while ( true ) {
            int data = reader.read();
            if ( data == -1 ) {
                break;
            } else {
                if ( start != null ) {
                    if ( currLine >= start && currLine <= end ) {
                        out.write(data);
                    }
                } else {
                    out.write(data);
                }
                if ( data == '\n' ) {
                    currLine++;
                }
            }
        }
    }
    
}
