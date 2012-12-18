package org.juxtasoftware.model;

import com.google.gson.annotations.Expose;

/**
 * Base class for any model that is a member of a workspace.
 * 
 * @author loufoster
 *
 */
public abstract class WorkspaceMember {
    @Expose protected Long id;
    @Expose protected Long workspaceId;
    
    public final Long getId() {
        return id;
    }

    public final void setId(Long id) {
        this.id = id;
    }

    public Long getWorkspaceId() {
        return this.workspaceId;
    }
    
    public void setWorkspaceId(Long id) {
        this.workspaceId = id;
    }
    
    public boolean isMemberOf( final Workspace ws ) {
        return (this.workspaceId.equals(ws.getId()) );
    }
}
