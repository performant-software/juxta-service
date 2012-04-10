package org.juxtasoftware.model;

/**
 * Class to trac
 * @author loufoster
 *
 */
public class Usage {
    public enum Type { SOURCE, WITNESS, COMPARISON_SET};
    private final Type type;
    private final Long id;
    private final String name;
    
    public Usage( final Type t, final Long id, final String name) {
        this.type = t;
        this.id = id;
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public Long getId() {
        return id;
    }
    
    public String getName() {
        return this.name;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
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
        Usage other = (Usage) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (type != other.type)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Usage [type=" + type + ", id=" + id + "]";
    }
}
