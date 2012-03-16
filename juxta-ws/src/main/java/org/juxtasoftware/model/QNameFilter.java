package org.juxtasoftware.model;

import com.google.gson.annotations.Expose;
import eu.interedition.text.Name;
import eu.interedition.text.rdbms.RelationalName;

import java.util.HashSet;
import java.util.Set;

/**
 * Defines a set of qnames that will be used to filter results
 * returned from GET annotations and alignments requests
 * 
 * @author loufoster
 *
 */
public final class QNameFilter extends WorkspaceMember {
    @Expose private String name;
    private Set<Name> qnames = new HashSet<Name>();
        
    public String getName() {
        return name;
    }
    
    public void setName(final String name) {
        this.name = name;
    }
    
    public Set<Name> getQNames() {
        return qnames;
    }
    
    /**
     * get a comma separated list of qname IDs that are part
     * of this filter
     * @return
     */
    public String getQNameIdListAsString() {
        StringBuffer sb = new StringBuffer();
        for ( Name qname : getQNames() ) {
            if ( sb.length() > 0 ) {
                sb.append(",");
            }
            sb.append( ((RelationalName)qname).getId() );
        }
        return sb.toString();
    }
    
    public void setQNames(Set<Name> annotations) {
        this.qnames = annotations;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
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
        QNameFilter other = (QNameFilter) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
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
        return "QName Filter [id=" + id + ", name=" + name + "]";
    }
}
