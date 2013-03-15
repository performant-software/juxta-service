package org.juxtasoftware.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import eu.interedition.text.Range;

public class UserAnnotation implements Comparable<UserAnnotation> {
    private Long id;
    private Long groupId;
    private Long setId;
    private Long baseId;
    private Set<Data> notes = new HashSet<Data>();
    private Range baseRange;
    private String fragment;
    
    public static Data createNote(final Long witId, final String note) {
        return new Data(witId, note);
    }
    public UserAnnotation() {
    }
    public Long getGroupId() {
        return groupId;
    }
    public void setGroupId(Long groupId) {
        this.groupId = groupId;
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
    public void addNote(UserAnnotation.Data note) {
        this.notes.add(note );
    }
    public void addNotes(Collection<Data> set) {
        this.notes.addAll(set);
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
    
    public boolean hasGroupAnnotation() {
        for ( Data note : this.notes) {
            if ( note.isGroup() ) {
                return true;
            }
        }
        return false;
    }
    
    public boolean hasWitnessAnnotation() {
        for ( Data note : this.notes) {
            if ( note.isGroup() == false ) {
                return true;
            }
        }
        return false;
    }
    

    public String getGroupNoteContent() {
        for ( Data note : this.notes) {
            if ( note.isGroup() ) {
                return note.getText();
            }
        }
        return "";
    }

    public void updateNotes(Set<Data> otherNotes) {
        for ( Data other : otherNotes ) {
            boolean found = false;
            for ( Data mine : this.notes ) {
                if ( mine.getWitnessId().equals(other.getWitnessId())) {
                    mine.text = other.text;
                    found = true;
                    break;
                }
            }
            if ( found == false ) {
                addNote(other);
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
    
    @Override
    public int compareTo(UserAnnotation that) {
        Range r1 = this.baseRange;
        Range r2 = that.baseRange;
        if ( r1.getStart() < r2.getStart() ) {
            return -1;
        } else if ( r1.getStart() > r2.getStart() ) {
            return 1;
        } else {
            if ( r1.getEnd() < r2.getEnd() ) {
                return -1;
            } else if ( r1.getEnd() > r2.getEnd() ) {
                return 1;
            } 
        }
        return 0;
    }
    
    /**
     * User note details
     */
    public static class Data {
        private Long id;
        private Long witnessId;
        private String witnessName;
        private String text;
        private boolean isGroup;
        
        public Data( final Long witId, final String text ) {
            this.witnessId = witId;
            this.text = text;
        }
        public Long getId() {
            return this.id;
        }
        public void setId(Long id) {
            this.id = id;
        }
        public boolean isGroup() {
            return this.isGroup;
        }
        public void setGroup(boolean isIt) {
            this.isGroup = isIt;
        }
        public String getText() {
            return this.text;
        }
        public Long getWitnessId() {
            return this.witnessId;
        }
        public String getWitnessName() {
            if (witnessName.length() == 0 || this.witnessId.equals(0L)) {
                return "All";
            }
            return witnessName;
        }
        public void setWitnessName(String witnessName) {
            this.witnessName = witnessName;
        }
        public void setText(String txt) {
            this.text =txt;
        }
        @Override
        public String toString() {
            return "[witnessId=" + witnessId + ", note=" + text + "]";
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result + ((text == null) ? 0 : text.hashCode());
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
            if (id == null) {
                if (other.id != null)
                    return false;
            } else if (!id.equals(other.id))
                return false;
            if (text == null) {
                if (other.text != null)
                    return false;
            } else if (!text.equals(other.text))
                return false;
            if (witnessId == null) {
                if (other.witnessId != null)
                    return false;
            } else if (!witnessId.equals(other.witnessId))
                return false;
            return true;
        }

        
    }
}
