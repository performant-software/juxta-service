package org.juxtasoftware.util.xml.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import eu.interedition.text.Annotation;
import eu.interedition.text.AnnotationRepository;
import eu.interedition.text.Name;
import eu.interedition.text.Range;
import eu.interedition.text.mem.SimpleAnnotation;
import eu.interedition.text.xml.XMLEntity;
import eu.interedition.text.xml.XMLParserState;
import eu.interedition.text.xml.module.TextXMLParserModule;

public class JuxtaTextXmlParserModule extends TextXMLParserModule {
    
    private AnnotationRepository annotationRepo;
    private final List<Integer> acceptedRevisions;
    private int revisionIndex = 0;
    private Stack<Long> revisionStartOffsetStack;
    private Stack<Map<Name, String>> revisionAttribStack;
    private List<Annotation> batch;
    private int batchLimit;

    public JuxtaTextXmlParserModule(AnnotationRepository annotationRepository, List<Integer> revisions) {
        super();
        
        this.annotationRepo = annotationRepository;
        this.acceptedRevisions = revisions;
        this.revisionStartOffsetStack = new Stack<Long>();
        this.revisionAttribStack = new Stack<Map<Name, String>>();
        this.batch = new ArrayList<Annotation>();
    }

    @Override
    public void start(XMLEntity entity, XMLParserState state) {

        // save attrib and start range info for revision tags
        if (state.getInclusionContext().peek()) {
            if ( isAdd(entity) || isDelete(entity) ) {
                this.revisionStartOffsetStack.push(state.getTextOffset());
                this.revisionAttribStack.push(entity.getAttributes());
            }
        }

        boolean handled = false;
        if (isAdd(entity) || isDelete(entity)) {

            if (this.acceptedRevisions.contains(this.revisionIndex)) {
                if (isDelete(entity)) {
                    state.getInclusionContext().push(false);
                    handled = true;
                }
            } else {
                if (isAdd(entity)) {
                    state.getInclusionContext().push(false);
                    handled = true;
                }
            }
            this.revisionIndex++;
        } 
        
        if ( handled == false ){
            super.start(entity, state);
        }
    }
    
    @Override
    public void end(XMLEntity entity, XMLParserState state) {

        // dump revision tags out as annotations
        if (state.getInclusionContext().peek()) {
            if ( isAdd(entity) || isDelete(entity) ) {
                final Range range = new Range(this.revisionStartOffsetStack.pop(), 
                    state.getTextOffset());
                this.batch.add(new SimpleAnnotation(state.getTarget(), entity.getName(), 
                    range, this.revisionAttribStack.pop()));
                if ( this.batch.size() >= this.batchLimit ) {
                      this.annotationRepo.create(this.batch);
                      this.batch.clear();
                }
            }
        }
        
        super.end(entity, state);
    }
    
    private boolean isAdd(XMLEntity entity) {
        return ( entity.getName().getLocalName().equals("add") ||
                 entity.getName().getLocalName().equals("addSpan") );
    }
    
    private boolean isDelete(XMLEntity entity) {
        return ( entity.getName().getLocalName().equals("del") ||
                 entity.getName().getLocalName().equals("delSpan") );
    }
}