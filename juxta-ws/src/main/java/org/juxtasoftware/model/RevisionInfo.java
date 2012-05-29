package org.juxtasoftware.model;

import org.juxtasoftware.Constants;

import eu.interedition.text.Name;
import eu.interedition.text.Range;

public class RevisionInfo {
    private final Name name;
    private final Range range;
    private Long annotationId;
    
    public RevisionInfo( final String localName, final Range r) {
        if ( localName.equals("add")) {
            this.name = Constants.TEI_ADD;
        } else if ( localName.equals("addSpan")) {
            this.name = Constants.TEI_ADD_SPAN;
        } else if ( localName.equals("del")) {
            this.name = Constants.TEI_DEL;
        } else if ( localName.equals("delSpan")) {
            this.name = Constants.TEI_DEL_SPAN;
        } else {
            throw new RuntimeException("Illegal revision tag name: " + localName);
        }
        this.range = new Range(r);
    }
    
    public boolean isAdd() {
        return (this.name.getLocalName().equals("add") ||  this.name.getLocalName().equals("addSpan"));
    }
    public boolean isDelete() {
        return (this.name.getLocalName().equals("del") ||  this.name.getLocalName().equals("delSpan"));
    }
    
    public Long getAnnotationId() {
        return this.annotationId;
    }
    public void setAnnotationId( final Long id) {
        this.annotationId = id;
    }
    
    public Name getName() {
        return this.name;
    }
    
    public Range getRange() {
        return this.range;
    }

    @Override
    public String toString() {
        return "RevisionInfo [name=" + name + ", range=" + range + ", annotationId=" + annotationId + "]";
    }
}
