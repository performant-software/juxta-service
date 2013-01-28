package org.juxtasoftware.model;

import java.sql.Timestamp;



public class ResourceInfo {
    private final Long id;
    private final String workspace;
    private final String name;
    private final Timestamp dateCreated;
    private final Timestamp dateModified;
    
    public ResourceInfo(final Long id, final String ws, final String name, final Timestamp timestamp, final Timestamp timestamp2) {
        this.id = id;
        this.workspace = ws;
        this.name = name;
        this.dateCreated = timestamp;
        this.dateModified = timestamp2;
    }
    public Long getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getWorkspace() {
        return workspace;
    }
    public Timestamp getDateCreated() {
        return dateCreated;
    }
    public Timestamp getDateModified() {
        return dateModified;
    }
    @Override
    public String toString() {
        return "ResourceInfo [id=" + id + ", name=" + name + ", dateCreated=" + dateCreated + ", dateModified="
            + dateModified + "]";
    }
    
    
}
