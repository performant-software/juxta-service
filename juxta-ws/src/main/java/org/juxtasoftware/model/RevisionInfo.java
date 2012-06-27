package org.juxtasoftware.model;

import eu.interedition.text.Range;

public class RevisionInfo {
    public enum Type {ADD, DELETE};
    private final Long id;
    private Long witnessId;
    private final Range range;
    private final boolean isIncluded;
    private final String text;
    private final Type type;
    
    public RevisionInfo( final Long id, final Long witId, final Type t, final Range r, final String txt, boolean included ) {
        this.id = id;
        this.witnessId = witId;
        this.type = t;
        this.range = new Range(r);
        this.text = txt;
        this.isIncluded = included;
    }
    
    public RevisionInfo( final String qName, final Range r, final String txt, boolean included ) {
        if ( qName.contains("add") ) {
            this.type = Type.ADD;
        } else if ( qName.contains("del") ) {
            this.type = Type.DELETE;
        } else {
            throw new RuntimeException("Illegal revision tag name: " + qName);
        }
        this.id = null;
        this.range = new Range(r);
        this.text = txt;
        this.isIncluded = included;
    }

    public Long getId() {
        return this.id;
    }

    public Long getWitnessId() {
        return this.witnessId;
    }
    
    public void setWitnessId( final Long witId ) {
        this.witnessId = witId;
    }
    
    public boolean isIncluded() {
        return this.isIncluded;
    }
    public String getText() {
        return this.text;
    }
    public Type getType() {
        return this.type;
    }
   
    public Range getRange() {
        return this.range;
    }

    @Override
    public String toString() {
        return "RevisionInfo [type=" + type + ", range=" + range;
    }
}
