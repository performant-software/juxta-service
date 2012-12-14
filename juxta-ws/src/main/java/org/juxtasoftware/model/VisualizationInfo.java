package org.juxtasoftware.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Data used to generate the histogram
 * @author loufoster
 *
 */
public class VisualizationInfo {
    private final ComparisonSet set;        
    private final Witness base;
    private final Set<Long> witnesses = new HashSet<Long>();
    
    public VisualizationInfo(ComparisonSet set, Witness base, Set<Long> witnessFilterList ) {
        this.base = base;
        this.witnesses.addAll(witnessFilterList);
        this.set = set;
    }
    
    public long getKey() {
        return (long)hashCode();
    }

    public ComparisonSet getSet() {
        return this.set;
    }

    public Witness getBase() {
        return this.base;
    }

    public Set<Long> getWitnessFilter() {
        return this.witnesses;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((base == null) ? 0 : base.hashCode());
        result = prime * result + ((set == null) ? 0 : set.hashCode());
        result = prime * result + ((witnesses == null) ? 0 : witnesses.hashCode());
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
        VisualizationInfo other = (VisualizationInfo) obj;
        if (base == null) {
            if (other.base != null)
                return false;
        } else if (!base.equals(other.base))
            return false;
        if (set == null) {
            if (other.set != null)
                return false;
        } else if (!set.equals(other.set))
            return false;
        if (witnesses == null) {
            if (other.witnesses != null)
                return false;
        } else if (!witnesses.equals(other.witnesses))
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return "VisualizationInfo [set=" + set + ", base=" + base + ", witnesses=" + witnesses + "] = KEY: " +getKey();
    }
}
