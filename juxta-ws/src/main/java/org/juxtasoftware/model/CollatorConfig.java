package org.juxtasoftware.model;


public class CollatorConfig {
    public enum HyphenationFilter {INCLUDE_ALL, FILTER_LINEBREAK, FILTER_ALL}
    
    private Long id;
    private boolean filterWhitespace = true;
    private boolean filterPunctuation = false;
    private boolean filterCase = false;
    private HyphenationFilter hyphenationFilter = HyphenationFilter.INCLUDE_ALL;

    public CollatorConfig() {
        this(true,false,false);
    }

    public CollatorConfig(boolean filterWhitespace, boolean filterPunctuation, boolean filterCase) {
        this.filterWhitespace = filterWhitespace;
        this.filterPunctuation = filterPunctuation;
        this.filterCase = filterCase;
    }
    
    public final Long getId() {
        return id;
    }

    public final void setId(Long id) {
        this.id = id;
    }

    public boolean isFilterWhitespace() {
        return filterWhitespace;
    }

    public void setFilterWhitespace(boolean filterWhitespace) {
        this.filterWhitespace = filterWhitespace;
    }

    public boolean isFilterPunctuation() {
        return filterPunctuation;
    }

    public void setFilterPunctuation(boolean filterPunctuation) {
        this.filterPunctuation = filterPunctuation;
    }

    public boolean isFilterCase() {
        return filterCase;
    }

    public void setFilterCase(boolean filterCase) {
        this.filterCase = filterCase;
    }

    public HyphenationFilter getHyphenationFilter() {
        return hyphenationFilter;
    }

    public void setHyphenationFilter(HyphenationFilter hyphenationFilter) {
        this.hyphenationFilter = hyphenationFilter;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        CollatorConfig other = (CollatorConfig) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        return true;
    }
    
}
