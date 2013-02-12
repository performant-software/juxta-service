package org.juxtasoftware.model;

import java.util.HashSet;
import java.util.Set;

import eu.interedition.text.Range;

public class UserAnnotation {
    private Long id;
    private Long setId;
    private Long baseId;
    private Set<Data> notes = new HashSet<Data>();
    private Range baseRange;
    private String fragment;
    
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
    public String getBaseFragment() {
        return this.fragment;
    }
    public void setBaseFragment(String f) {
        this.fragment = f;
    }
    public Set<Data> getNotes() {
        return notes;
    }
    public void addNote(Long witnessId, String note) {
        this.notes.add( new Data(witnessId, note ) );
    }
    public void removeNote(Long witnessId) {
        for ( Data d : this.notes ) {
            if ( d.getWitnessId().equals(witnessId)) {
                this.notes.remove(d);
                break;
            }
        }
    }
    public Range getBaseRange() {
        return baseRange;
    }
    public void setBaseRange(Range baseRange) {
        this.baseRange = baseRange;
    }
    public boolean matches( UserAnnotation other ) {
        return ( this.baseId.equals(other.baseId) &&
                 this.baseRange.equals(other.baseRange) && 
                 this.setId.equals(other.setId));
    }
    

    public void updateNotes(Set<Data> otherNotes) {
        for ( Data other : otherNotes ) {
            boolean found = false;
            for ( Data mine : this.notes ) {
                if ( mine.getWitnessId().equals(other.getWitnessId())) {
                    mine.note = other.note;
                    found = true;
                    break;
                }
            }
            if ( found == false ) {
                addNote(other.witnessId, other.note);
            }
        }
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
        return "UserAnnotation [id=" + id + ", baseId=" + baseId + ", baseRange="
            + baseRange + ", witnessNotes=" + notes + "]";
    }
    
    public static class Data {
        private Long witnessId;
        private String witnessName;
        private String note;
        public Data( final Long id, final String note ) {
            this.witnessId = id;
            this.note = note;
        }
        public String getNote() {
            return this.note;
        }
        public Long getWitnessId() {
            return this.witnessId;
        }
        public String getWitnessName() {
            return witnessName;
        }
        public void setWitnessName(String witnessName) {
            this.witnessName = witnessName;
        }
        @Override
        public String toString() {
            return "[witnessId=" + witnessId + ", note=" + note + "]";
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((witnessId == null) ? 0 : witnessId.hashCode());
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
            Data other = (Data) obj;
            if (witnessId == null) {
                if (other.witnessId != null)
                    return false;
            } else if (!witnessId.equals(other.witnessId))
                return false;
            return true;
        }
    }
}
