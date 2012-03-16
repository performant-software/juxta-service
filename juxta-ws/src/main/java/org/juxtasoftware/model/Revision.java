package org.juxtasoftware.model;

import eu.interedition.text.Range;

/**
 * A simplified union of witness, text_annotation and juxta_accepted_revsion.
 * It tracks the data necessary to drive the revsion managment ui/tagging
 *  
 * @author loufoster
 *
 */
public final class Revision  {
    public enum Type {ADD, DELETE}
    private Long annotationId;
    private Long witnessId;
    private Range range;
    private Type type;
    private boolean accepted = false;
    
    public Long getAnnotationId() {
        return annotationId;
    }
    
    public void setAnnotationId(final Long annotationId) {
        this.annotationId = annotationId;
    }
    
    public Long getWitnessId() {
        return witnessId;
    }
    
    public void setWitnessId(final Long witnessId) {
        this.witnessId = witnessId;
    }
    
    public Range getRange() {
        return range;
    }
    
    public void setRange(final Range range) {
        this.range = range;
    }
    
    public Type getType() {
        return type;
    }
    
    public void setType(final String localQname) {
        if  (localQname.contains("add")) {
            this.type = Type.ADD;
        } else if  (localQname.contains("del")) {
            this.type = Type.DELETE;
        } else {
            throw new RuntimeException("Invalid revision QName: "+localQname);
        }
    }
    
    public boolean isAccepted() {
        return accepted;
    }
    
    public void setAccepted(final boolean accepted) {
        this.accepted = accepted;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (accepted ? 1231 : 1237);
        result = prime * result + ((annotationId == null) ? 0 : annotationId.hashCode());
        result = prime * result + ((range == null) ? 0 : range.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((witnessId == null) ? 0 : witnessId.hashCode());
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
        Revision other = (Revision) obj;
        if (accepted != other.accepted) {
            return false;
        }
        if (annotationId == null) {
            if (other.annotationId != null) {
                return false;
            }
        } else if (!annotationId.equals(other.annotationId)) {
            return false;
        }
        if (range == null) {
            if (other.range != null) {
                return false;
            }
        } else if (!range.equals(other.range)) {
            return false;
        }
        if (type != other.type) {
            return false;
        }
        if (witnessId == null) {
            if (other.witnessId != null) {
                return false;
            }
        } else if (!witnessId.equals(other.witnessId)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Revision [annotationId=" + annotationId + ", witnessId=" + witnessId + ", range=" + range + ", type="
            + type + ", accepted=" + accepted + "]";
    }

    
}
