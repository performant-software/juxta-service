package org.juxtasoftware.model;

import eu.interedition.text.Range;

public class UserAnnotation {
    private Long id;
    private Long setId;
    private Long baseId;
    private Long witnessId;
    private Range baseRange;
    private String note;
    
    public UserAnnotation() {
    }
    public Long getSetId() {
        return setId;
    }
    public void setSetId(Long setId) {
        this.setId = setId;
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Long getBaseId() {
        return baseId;
    }
    public void setBaseId(Long baseId) {
        this.baseId = baseId;
    }
    public Long getWitnessId() {
        return witnessId;
    }
    public void setWitnessId(Long witnessId) {
        this.witnessId = witnessId;
    }
    public Range getBaseRange() {
        return baseRange;
    }
    public void setBaseRange(Range baseRange) {
        this.baseRange = baseRange;
    }
    public String getNote() {
        return note;
    }
    public void setNote(String note) {
        this.note = note;
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
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        UserAnnotation other = (UserAnnotation) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "UserAnnotation [id=" + id + ", baseId=" + baseId + ", witnessId=" + witnessId + ", baseRange="
            + baseRange + ", note=" + note + "]";
    }
    
    
}
