package org.juxtasoftware.model;

import eu.interedition.text.Range;
import eu.interedition.text.Text;
import eu.interedition.text.rdbms.RelationalText;

/**
 * Constraints to control the amount and content of annotations to be 
 * returned by the annotation DAO
 * @author loufoster
 *
 */
public class AnnotationConstraint {
    private Range range = null;
    private QNameFilter filter = null;
    private boolean includeText = false;
    private final Long textId;
    private Long limit = null;
    
    public AnnotationConstraint( final Text text ) {
        this.textId = ((RelationalText)text).getId();
    }
    public AnnotationConstraint( final Witness witness) {
        this.textId = new Long(((RelationalText)witness.getText()).getId());
    }
    public final Long getTextId() {
        return textId;
    }
    public final Range getRange() {
        return range;
    }
    
    /**
     * Get annotations over a limited range of a witness
     * @param range
     */
    public final void setRange(Range range) {
        this.range = range;
    }
    public final QNameFilter getFilter() {
        return filter;
    }
    
    /**
     * Add a QName filter for the types of annotation returned
     * @param filter
     */
    public final void setFilter(QNameFilter filter) {
        this.filter = filter;
    }
    public final boolean isIncludeText() {
        return includeText;
    }
    
    /**
     * Flag the inclusion of annotation content
     * @param includeText
     */
    public final void setIncludeText(boolean includeText) {
        this.includeText = includeText;
    }
    
   
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((filter == null) ? 0 : filter.hashCode());
        result = prime * result + (includeText ? 1231 : 1237);
        result = prime * result + ((limit == null) ? 0 : limit.hashCode());
        result = prime * result + ((range == null) ? 0 : range.hashCode());
        result = prime * result + ((textId == null) ? 0 : textId.hashCode());
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
        AnnotationConstraint other = (AnnotationConstraint) obj;
        if (filter == null) {
            if (other.filter != null) {
                return false;
            }
        } else if (!filter.equals(other.filter)) {
            return false;
        }
        if (includeText != other.includeText) {
            return false;
        }
        if (limit == null) {
            if (other.limit != null) {
                return false;
            }
        } else if (!limit.equals(other.limit)) {
            return false;
        }
        if (range == null) {
            if (other.range != null) {
                return false;
            }
        } else if (!range.equals(other.range)) {
            return false;
        }
        if (textId == null) {
            if (other.textId != null) {
                return false;
            }
        } else if (!textId.equals(other.textId)) {
            return false;
        }
        return true;
    }
    
}
