package org.juxtasoftware.model;

import java.util.Date;

import com.google.common.base.Objects;

/**
 * A juxta comparison set
 * 
 * @author loufoster
 *
 */
public class ComparisonSet extends WorkspaceMember {
    public enum Status {NOT_COLLATED, TOKENIZING, TOKENIZED, COLLATING, COLLATED, ERROR, DELETED};
    private String name;
    private Status status = Status.NOT_COLLATED;
    private Date created;
    private Date updated;
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public boolean isCollated() {
        return (this.status.equals(Status.COLLATED));
    }

    public void setStatus(Status collStatus) {
        this.status = collStatus;
    }
    public void setStatus(String strStatus) {
        this.status = Status.valueOf(strStatus);
    }
    
    public Status getStatus() {
        return this.status;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (id ^ (id >>> 32));
        result = prime * result + ((name == null) ? 0 : name.hashCode());
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
        ComparisonSet other = (ComparisonSet) obj;
        if (id != other.id) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("name", name).toString();
    }
}
