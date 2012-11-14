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

        File txt = WikiTextUtils.toTxt(is);

        FileInputStream fis = new FileInputStream(txt);
        final String content = IOUtils.toString(fis).trim();
        IOUtils.closeQuietly(fis);
        System.out.println(content);
        txt.delete();
        Assert.assertEquals("This is a Hello World example", content);
    }

    @Test
    public void testImageStrip() throws Exception {
        InputStream is = getClass().getResourceAsStream("/image.wiki");
        File txt = WikiTextUtils.toTxt(is);
        FileInputStream fis = new FileInputStream(txt);
        final String content = IOUtils.toString(fis).trim();
        IOUtils.closeQuietly(fis);
        System.out.println(content);
        txt.delete();
        Assert.assertEquals("Title\ntext", content);
    }

    @Test
    public void testCitationStrip() throws Exception {
        InputStream is = getClass().getResourceAsStream("/citation.wiki");
        File txt = WikiTextUtils.toTxt(is);
        FileInputStream fis = new FileInputStream(txt);
        final String content = IOUtils.toString(fis).trim();
        IOUtils.closeQuietly(fis);
        System.out.println(content);
        txt.delete();
        Assert.assertEquals("This worked!", content);
    }

    @Test
    public void testRefStrip() throws Exception {
        InputStream is = getClass().getResourceAsStream("/ref.wiki");
        File txt = WikiTextUtils.toTxt(is);
        FileInputStream fis = new FileInputStream(txt);
        final String content = IOUtils.toString(fis).trim();
        IOUtils.closeQuietly(fis);
        System.out.println(content);
        txt.delete();
        Assert.assertFalse(content.contains("Zhang"));
        Assert.assertFalse(content.contains("first4"));
        Assert.assertTrue(content.contains("The end."));
        Assert.assertTrue(content.contains("cancer."));
    }

    @Test
    public void wikipediaText2Transform() throws Exception {
        InputStream is = getClass().getResourceAsStream("/wikipedia.wiki");
        File txt = WikiTextUtils.toTxt(is);
        FileInputStream fis = new FileInputStream(txt);
        final String content = IOUtils.toString(fis);
        IOUtils.closeQuietly(fis);
        System.out.println(content);
        txt.delete();
        Assert.assertFalse(content.contains("Bot generated title"));
        Assert.assertFalse(content.contains("Citation needed"));
        Assert.assertTrue(content.contains("Tea consumption has its legendary origins in China"));
        Assert.assertTrue(content.contains("Green Tea's cancer fighting potential"));
    }

    @Test
    public void wikipediaTextTransform() throws Exception {
        InputStream is = getClass().getResourceAsStream("/complex.wiki");
        File txt = WikiTextUtils.toTxt(is);
        FileInputStream fis = new FileInputStream(txt);
        final String content = IOUtils.toString(fis);
        IOUtils.closeQuietly(fis);
        System.out.println(content);
        txt.delete();
    }
}
