package org.juxtasoftware.service;

import org.juxtasoftware.model.CollatorConfig.HyphenationFilter;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public interface TokenizerConfiguration {
    boolean isFilterWhitespace();
    boolean isFilterPunctuation();
    boolean isFilterCase();
    HyphenationFilter getHyphenationFilter();

}
