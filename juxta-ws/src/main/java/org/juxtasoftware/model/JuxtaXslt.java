package org.juxtasoftware.model;

import com.google.common.base.Objects;

public class JuxtaXslt extends WorkspaceMember {
    private String name;
    private String xslt;
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getXslt() {
        return xslt;
    }
    public void setXslt(String xslt) {
        this.xslt = xslt;
    }
    
    @Override
    public String toString() {
        return "JuxtaXslt [name=" + name + ", id=" + id + "]";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this.id != 0 && obj != null && obj instanceof JuxtaXslt) {
            return this.id == ((JuxtaXslt) obj).id;
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return this.id == 0 ? super.hashCode() : Objects.hashCode(this.id);
    }
}
