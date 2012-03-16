package org.juxtasoftware.model;

/**
 * Data describing a TEI PB (page break) tag
 * 
 * @author loufoster
 *
 */
public class PageBreak {
    private Long id;
    private Long witnessId;
    private long offset;
    private String label;
    
    public final Long getId() {
        return id;
    }
    public final void setId(Long id) {
        this.id = id;
    }
    public final Long getWitnessId() {
        return witnessId;
    }
    public final void setWitnessId(Long witnessId) {
        this.witnessId = witnessId;
    }
    public final long getOffset() {
        return offset;
    }
    public final void setOffset(long offset) {
        this.offset = offset;
    }
    public final String getLabel() {
        return label;
    }
    public final void setLabel(String label) {
        this.label = label;
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
        PageBreak other = (PageBreak) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        return true;
    }
    @Override
    public String toString() {
        return "PageBreak [id=" + id + ", witnessId=" + witnessId + ", offset=" + offset + ", label=" + label + "]";
    }
    
    
}
