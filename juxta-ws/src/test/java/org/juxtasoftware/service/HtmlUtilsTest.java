package org.juxtasoftware.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.juxtasoftware.util.HtmlUtils;

public class HtmlUtilsTest {
    
    @Test
    public void simpleCase() throws Exception {
        InputStream is = getClass().getResourceAsStream("/simple.html");
        File tmp = copyToTempFile(is);
        
        HtmlUtils.strip(tmp);
        
        FileInputStream fis = new FileInputStream(tmp);
        final String content = IOUtils.toString(fis).toLowerCase();
        IOUtils.closeQuietly(fis);
        
        Assert.assertFalse(content.contains("<head>"));
        Assert.assertFalse(content.contains("<meta"));
        Assert.assertFalse(content.contains("</head>"));
        Assert.assertTrue(content.contains("<body>"));
        Assert.assertTrue(content.contains("<h1>test</h1>"));
        Assert.assertTrue(content.contains("</html>"));
    }
    
    @Test
    public void stripJs() throws Exception {
        InputStream is = getClass().getResourceAsStream("/js.html");
        File tmp = copyToTempFile(is);
        
        HtmlUtils.strip(tmp);
        
        FileInputStream fis = new FileInputStream(tmp);
        final String content = IOUtils.toString(fis).toLowerCase();
        IOUtils.closeQuietly(fis);
        
        Assert.assertFalse(content.contains("<script>"));
        Assert.assertFalse(content.contains("</script>"));
        Assert.assertTrue(content.contains("<body>"));
        Assert.assertTrue(content.contains("<h1>test</h1>"));
        Assert.assertTrue(content.contains("</html>"));
    }
    
    @Test
    public void stripScopedCss() throws Exception {
        InputStream is = getClass().getResourceAsStream("/css.html");
        File tmp = copyToTempFile(is);
        
        HtmlUtils.strip(tmp);
        
        FileInputStream fis = new FileInputStream(tmp);
        final String content = IOUtils.toString(fis).toLowerCase();
        IOUtils.closeQuietly(fis);
        
        Assert.assertFalse(content.contains("<style>"));
        Assert.assertFalse(content.contains("text-decoration:"));
        Assert.assertFalse(content.contains("</style>"));
        Assert.assertTrue(content.contains("<body>"));
        Assert.assertTrue(content.contains("<h1>test</h1>"));
        Assert.assertTrue(content.contains("</html>"));
    }
    
    private File copyToTempFile( InputStream is ) throws IOException {
        File tmpSrc = File.createTempFile("src", "dat");
        FileOutputStream fos =  new FileOutputStream(tmpSrc);
        IOUtils.copyLarge(is, fos);
        IOUtils.closeQuietly(fos);
        return tmpSrc;
    }
}
