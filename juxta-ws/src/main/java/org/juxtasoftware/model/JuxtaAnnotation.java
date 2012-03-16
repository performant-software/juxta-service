package org.juxtasoftware.model;

import java.util.Map;

import eu.interedition.text.Name;
import eu.interedition.text.Range;
import eu.interedition.text.Text;
import eu.interedition.text.rdbms.RelationalAnnotation;

/**
 * An extension of the basic annotaion that includes
 * witness ID and may include text content
 * 
 * @author loufoster
 *
 */
public class JuxtaAnnotation extends RelationalAnnotation {
    private String content;
    private final Long witnessId;
    
    public JuxtaAnnotation( JuxtaAnnotation other ) {
        this( other.getWitnessId(), other.getText(), other.getName(), other.getRange(), other.getData(), other.getId() );
    }
    
    public JuxtaAnnotation(Witness witness, Name name, Range range ) {
        this(witness.getId(), witness.getText(), name, range, null, 0);
    }

    public JuxtaAnnotation(Witness witness, Name name, Range range, Map<Name, String> data, long id) {
        this(witness.getId(), witness.getText(), name, range, data, id);
    }
    
    public JuxtaAnnotation(Long witnessId, Text text, Name name, Range range, Map<Name, String> data, long id) {
        super(text, name, range, data, id);
        this.witnessId = witnessId;
    }
    
    public JuxtaAnnotation(Long witnessId, Text text, Name name, Range range, long id) {
        super(text, name, range, null, id);
        this.witnessId = witnessId;
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
}
