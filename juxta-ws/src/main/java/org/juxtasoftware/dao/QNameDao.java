package org.juxtasoftware.dao;

import java.util.List;

import eu.interedition.text.Name;

/**
 * Data Access object for QNames
 * @author loufoster
 *
 */
public interface QNameDao {
    /**
     * List all available QNames
     * @return
     */
    List<Name> list();
}
