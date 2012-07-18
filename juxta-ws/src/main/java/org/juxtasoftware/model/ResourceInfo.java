package org.juxtasoftware.model;

import java.util.Date;

public class ResourceInfo {
    private final Long id;
    private final String workspace;
    private final String name;
    private final Date dateCreated;
    private final Date dateModified;
    
    public ResourceInfo(final Long id, final String ws, final String name, final Date create, final Date mod) {
        this.id = id;
        this.workspace = ws;
        this.name = name;
        this.dateCreated = create;
        this.dateModified = mod;
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
    public Date getDateCreated() {
        return dateCreated;
    }
    public Date getDateModified() {
        return dateModified;
    }
    @Override
    public String toString() {
        return "ResourceInfo [id=" + id + ", name=" + name + ", dateCreated=" + dateCreated + ", dateModified="
            + dateModified + "]";
    }
    
    
}
