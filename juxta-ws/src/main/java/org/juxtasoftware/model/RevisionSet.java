package org.juxtasoftware.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A named set of revision indexes to accept when transforming
 * a source into a witness
 * 
 * @author loufoster
 *
 */
public class RevisionSet {
    private Long id;
    private String name;
    private Long sourceId;
    private List<Integer> revisionIndexes = new ArrayList<Integer>();
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public List<Integer> getRevisionIndexes() {
        return revisionIndexes;
    }
    
    public String getRevisionIndexString() {
        StringBuilder sb = new StringBuilder();
        for ( Integer idx : revisionIndexes) {
            if ( sb.length() > 0) {
                sb.append(",");
            }
            sb.append(idx);
        }
        return sb.toString();
    }
    public void setRevisionIndexes(List<Integer> revisonIndexes) {
        this.revisionIndexes = revisonIndexes;
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
        RevisionSet other = (RevisionSet) obj;
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
        return "RevisionSet [id=" + id + ", name=" + name + ", revisonIndexes=" + revisionIndexes + "]";
    }
    
    
}
