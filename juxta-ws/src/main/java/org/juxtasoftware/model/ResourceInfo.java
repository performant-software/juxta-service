package org.juxtasoftware.model;

import java.util.Date;

public class ResourceInfo {
    private final Long id;
    private final String name;
    private final Date dateCreated;
    private final Date dateModified;
    
    public ResourceInfo(final Long id, final String name, final Date create, final Date mod) {
        this.id = id;
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
