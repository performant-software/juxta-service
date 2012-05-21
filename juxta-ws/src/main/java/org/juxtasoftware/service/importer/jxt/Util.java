package org.juxtasoftware.service.importer.jxt;

import com.google.common.base.Throwables;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class Util {
    private static SAXParserFactory parserFactory;

    static {
        parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(false);
        parserFactory.setValidating(false);
    }

    public static SAXParser saxParser() throws SAXException {
        try {
            return parserFactory.newSAXParser();
        } catch (ParserConfigurationException e) {
            throw Throwables.propagate(e);
        }
    }

    static long defaultLong(String str, long defaultValue) {
        if (str == null || str.trim().length() == 0) {
            return defaultValue;
        }
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    static boolean defaultBoolean(String str, boolean defaultValue) {
        if (str == null || str.trim().length() == 0) {
            return defaultValue;
        }
        return Boolean.parseBoolean(str);
    }

    static boolean isContainedIn(File base, File file) {
        File parent = file.getParentFile();
        while (parent != null) {
            if (base.equals(parent)) {
                return true;
            }
            parent = parent.getParentFile();
        }
        return false;
    }

    static String defaultString(String str, String defaultValue) {
        if (str == null || str.trim().length() == 0) {
            return defaultValue;
        }
        return str;
    }

}
