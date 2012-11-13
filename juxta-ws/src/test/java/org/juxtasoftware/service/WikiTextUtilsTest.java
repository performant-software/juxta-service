package org.juxtasoftware.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.juxtasoftware.util.WikiTextUtils;

public class WikiTextUtilsTest {
    
    @Test
    public void simpleTextTransform() throws Exception {
        InputStream is = getClass().getResourceAsStream("/simple.wiki");
        
        File txt = WikiTextUtils.toTxt(  is );
        
        FileInputStream fis = new FileInputStream(txt);
        final String content = IOUtils.toString(fis);
        IOUtils.closeQuietly(fis);
        System.out.println(content);
        txt.delete();
        
        Assert.assertEquals("This is a Hello World example", content);
    }
    
    @Test
    public void wikipediaTextTransform() throws Exception {
        InputStream is = getClass().getResourceAsStream("/complex.wiki");
        
        File txt = WikiTextUtils.toTxt(  is );
        
        FileInputStream fis = new FileInputStream(txt);
        final String content = IOUtils.toString(fis);
        IOUtils.closeQuietly(fis);
        System.out.println(content);
        txt.delete();
    }
    
    @Test
    public void wikipediaText2Transform() throws Exception {
        InputStream is = getClass().getResourceAsStream("/wikipedia.wiki");
        
        File txt = WikiTextUtils.toTxt(  is );
        
        FileInputStream fis = new FileInputStream(txt);
        final String content = IOUtils.toString(fis);
        IOUtils.closeQuietly(fis);
        System.out.println(content);
        txt.delete();
    }

}
