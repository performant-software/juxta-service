package org.juxtasoftware.model;

import java.util.Map;

import eu.interedition.text.Annotation;
import eu.interedition.text.Name;
import eu.interedition.text.Range;
import eu.interedition.text.Text;

/**
 * An extension of the basic annotaion that includes
 * witness ID and may include text content
 * 
 * @author loufoster
 *
 */
public class JuxtaAnnotation implements Annotation {
    private Long id;
    private final Long setId;
    private final Long witnessId;
    private final Text text;
    private final Name qName;
    private final Range range;
    private String content;
    private boolean manual = false;

    public JuxtaAnnotation(Long setId, Witness witness, Name qname, Range range ) {
        this(null,setId, witness.getId(), witness.getText(), qname, range);
    }
    
    public JuxtaAnnotation( final Long setId, final Long witnessId, Annotation other) {
        this(null,setId, witnessId, other.getText(), other.getName(), other.getRange());  
    }
    
    public JuxtaAnnotation( JuxtaAnnotation other ) {
        this( other.getId(), other.getSetId(), other.getWitnessId(), other.getText(), other.getName(), other.getRange() );
    }
    
    public JuxtaAnnotation(Long id, Long setId, Long witnessId, Text text, Name qname, Range range ) {
        this.id = id;
        this.setId = setId;
        this.witnessId = witnessId;
        this.qName = qname;
        this.text = text;
        this.range = new Range(range);
        this.content = "";
    }
    
    public void setManual() {
        this.manual = true;
    }
    
    public boolean isManual() {
        return this.manual;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public void setContent( String content ) {
        this.content = content;
    }
    
    public String getContent() {
        return this.content;
    }
    
    public Long getWitnessId() {
        return this.witnessId;
    }
    
    public Long getId() {
        return this.id;
    }
    
    public Long getSetId() {
        return this.setId;
    }

    @Override
    public int compareTo(Annotation other) {
        return getRange().compareTo(other.getRange());
    }

    @Override
    public Text getText() {
        return this.text;
    }

    @Override
    public Name getName() {
        return this.qName;
    }

    @Override
    public Range getRange() {
        return new Range(this.range);
    }

    @Override
    public Map<Name, String> getData() {
        return null;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((qName == null) ? 0 : qName.getNamespace().hashCode());
        result = prime * result + ((qName == null) ? 0 : qName.getLocalName().hashCode());
        result = prime * result + ((range == null) ? 0 : range.hashCode());
        result = prime * result + ((setId == null) ? 0 : setId.hashCode());
        result = prime * result + ((witnessId == null) ? 0 : witnessId.hashCode());
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
        JuxtaAnnotation other = (JuxtaAnnotation) obj;
        if (qName == null) {
            if (other.qName != null)
                return false;
        } else if (!qName.equals(other.qName))
            return false;
        if (range == null) {
            if (other.range != null)
                return false;
        } else if (!range.equals(other.range))
            return false;
        if (setId == null) {
            if (other.setId != null)
                return false;
        } else if (!setId.equals(other.setId))
            return false;
        if (witnessId == null) {
            if (other.witnessId != null)
                return false;
        } else if (!witnessId.equals(other.witnessId))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "JuxtaAnnotation [id=" + id + ", text=" + text + ", range=" + range + "]";
    }
    
}
