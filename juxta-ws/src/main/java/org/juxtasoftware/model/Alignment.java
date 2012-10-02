package org.juxtasoftware.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.interedition.text.Name;
import eu.interedition.text.Range;

/**
 * Link together a set of two witness annotations. 
 * @author loufoster
 *
 */
public class Alignment {
    private Long id;
    private Long setId;
    private Name name;
    private int editDistance;
    private int group;
    private boolean manual = false;
    
    private Map<Long, AlignedAnnotation> annotations = new HashMap<Long, Alignment.AlignedAnnotation>();
    
    public Alignment() {
    }
    
    public Alignment(Long setId, int group, Name name, JuxtaAnnotation a1, JuxtaAnnotation a2, int editDist) {
        this.setId = setId;
        this.group = group;
        this.name = name;
        this.editDistance = editDist;
        addAnnotation( new AlignedAnnotation(a1) );
        addAnnotation( new AlignedAnnotation(a2) );
    }
    
    public void setManual() {
        this.manual = true;
    }
    
    public boolean isManual() {
        return this.manual;
    }
    
    public final Long getId() {
        return id;
    }
    
    public final void setId(Long id) {
        this.id = id;
    }
    
    public final Name getName() {
        return name;
    }
    
    public int getGroup() {
        return this.group;
    }
    public void setGroup(int num) {
        this.group = num;
    }
    
    public Long getComparisonSetId() {
        return this.setId;
    }
    public void setComparisonSetId(Long id) {
        this.setId = id;
    }
    
    public final void setName(Name name) {
        this.name = name;
    }
    
    public final List<AlignedAnnotation> getAnnotations() {
        return new ArrayList<AlignedAnnotation>(annotations.values());
    }
    
    public boolean hasWitnessAnnotation(final Long witnesId) {
        return this.annotations.containsKey(witnesId);
    }
    
    public AlignedAnnotation getWitnessAnnotation( final Long witnessId ) {
        return this.annotations.get(witnessId);
    }
    
    public final void addAnnotation( AlignedAnnotation annotation) {
        this.annotations.put(annotation.getWitnessId(), annotation);
    }
    
    
    public final int getEditDistance() {
        return editDistance;
    }
    public final void setEditDistance(int editDistance) {
        this.editDistance = editDistance;
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
        Alignment other = (Alignment) obj;
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
        return "Alignment [id=" + id + "] " + this.name + " "+ this.annotations;
    }
    
    /**
     * Simplified annotation that just contains id references
     * to the actual anno and witness. It also contans a fragment
     * @author loufoster
     *
     */
    public static final class AlignedAnnotation {
        private final Name qname;
        private final Long witnessId;
        private final Long annotationId;
        private final Range range;
        private String fragment;

		public AlignedAnnotation(Name name, Long witnessId, Long annoId, Range range) {
            this.qname = name;
		    this.witnessId = witnessId;
            this.annotationId = annoId;
            this.range = range;
        }
        public AlignedAnnotation( JuxtaAnnotation a ) {
            this.witnessId = a.getWitnessId();
            this.annotationId = a.getId();
            this.range = a.getRange();
            this.qname = a.getName();
        }  
        
        public Name getQName() {
            return qname;
        }
        public String getFragment() {
            return fragment;
        }

        public void setFragment(String fragment) {
            this.fragment = fragment;
        }
        
        public Long getWitnessId() {
            return this.witnessId;
        }
        
        public Long getId() {
            return this.annotationId;
        }
        
        public Range getRange() {
            return new Range( this.range );
        }
        @Override
        public String toString() {
            return "AlignedAnnotation [qname=" + qname + ", witnessId=" + witnessId + ", annotationId=" + annotationId
                + ", range=" + range + "]";
        }
    }
}
