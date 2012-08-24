package org.juxtasoftware.model;

import java.util.HashSet;
import java.util.Set;

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
    private Set<Range> ranges = new HashSet<Range>();
    private QNameFilter filter = null;
    private boolean includeText = false;
    private final Long textId;
    private final Long setId;
    
    public AnnotationConstraint( final Long setId, final Text text ) {
        this.textId = ((RelationalText)text).getId();
        this.setId = setId;
    }
    public AnnotationConstraint( final Long setId, final Witness witness) {
        this.textId = new Long(((RelationalText)witness.getText()).getId());
        this.setId = setId;
    }
    public final Long getTextId() {
        return textId;
    }
    public final Set<Range> getRanges() {
        return ranges;
    }
    
    public Long getSetId() {
        return this.setId;
    }
    
    /**
     * Get annotations over a limited range of a witness
     * @param range
     */
    public final void addRange(Range range) {
        if ( range != null ) {
            this.ranges.add( range );
        }
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
    
}
