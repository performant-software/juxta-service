package org.juxtasoftware.service.importer.ps;

import java.util.List;

import org.juxtasoftware.model.Note;
import org.juxtasoftware.model.PageBreak;
import org.juxtasoftware.model.Template;
import org.juxtasoftware.model.Template.TagAction;
import org.juxtasoftware.model.Template.WildcardQName;
import org.juxtasoftware.service.importer.ps.WitnessParser.WitnessInfo;
import org.juxtasoftware.util.xml.module.NoteCollectingXMLParserModule;
import org.juxtasoftware.util.xml.module.PageBreakXmlParserModule;

import com.google.common.collect.Lists;

import eu.interedition.text.xml.XMLEntity;
import eu.interedition.text.xml.XMLParserConfiguration;
import eu.interedition.text.xml.XMLParserModule;
import eu.interedition.text.xml.module.LineElementXMLParserModule;
import eu.interedition.text.xml.module.NotableCharacterXMLParserModule;

class PsXmlParserConfig implements XMLParserConfiguration {
    
    private final Template template;
    private final char notableCharacter = '\u25CA';
    private final boolean compressingWhitespace = true;
    private final int textBufferSize = 102400;
    private final boolean removeLeadingWhitespace = true;
    private NoteCollectingXMLParserModule noteModule;
    private PageBreakXmlParserModule pbModule;
    private PsXmlParserModule textModule;

    private List<XMLParserModule> modules = Lists.<XMLParserModule>newArrayList(
            new LineElementXMLParserModule(),
            new NotableCharacterXMLParserModule()
    );

    public PsXmlParserConfig( final Template template, final WitnessInfo info ) {
        this.template = template;
        this.textModule = new PsXmlParserModule( info );
        this.modules.add( this.textModule );
                    
        // specialized tag handling. If a user has opted to EXCLUDE
        // notes, be sure that the note parser module is NOT added to the
        // module list. If notes are INCLUDED, add the filter and 
        // EXCLUDE them from the main template.
        boolean notesExcluded = false;
        boolean pbExcluded = false;
        for ( TagAction tagAct : this.template.getTagActions() ) {
            WildcardQName tagName = tagAct.getTag();
            if ( tagName.equals("*:*:note") &&
                tagAct.getAction().equalsIgnoreCase("exclude")) {
                    notesExcluded = true;
            }
            if (tagAct.getTag().equals("*:*:pb") &&
                tagAct.getAction().equalsIgnoreCase("exclude")) {
                    pbExcluded = true;
            }
        }
        
        if (pbExcluded == false ) {
            this.pbModule = new PageBreakXmlParserModule();
            this.modules.add(this.pbModule);
        }
        if (notesExcluded == false) {
            this.noteModule = new NoteCollectingXMLParserModule();
            this.modules.add(this.noteModule);
            this.template.exclude("note"); 
        }
    }
     
    public List<Note> getNotes() {
        return this.noteModule.getNotes();
    }
    
    public List<PageBreak> getPageBreaks() {
        return this.pbModule.getPageBreaks();
    }
    
    public final boolean notesIncluded() {
        return (this.noteModule != null);
    }
    public final boolean pageBreaksIncluded() {
        return (this.pbModule != null);
    }

    /**
     * implementation of XMLParserConfiguration interface
     */
    @Override
    public boolean included(XMLEntity entity) {
        return hasTemplateMatch( TagAction.Action.INCLUDE, entity );
    }

    @Override
    public boolean excluded(XMLEntity entity) {
        return hasTemplateMatch( TagAction.Action.EXCLUDE, entity );
    }

    @Override
    public boolean isLineElement(XMLEntity entity) {
        return hasTemplateMatch( TagAction.Action.NEW_LINE, entity );
    }

    @Override
    public boolean isNotable(XMLEntity entity) {
        return hasTemplateMatch( TagAction.Action.NOTABLE, entity );
    }

    private boolean hasTemplateMatch( final TagAction.Action action, XMLEntity entity ) {
        String uri = "";
        if ( entity.getName().getNamespace() != null ) {
            uri = entity.getName().getNamespace().toString();
        }
        final String prefix = entity.getPrefix();
        final String localName = entity.getName().getLocalName();

        for (TagAction act : template.getTagActions() ) {
            // quick test; make sure actions match
            if ( act.getActionAsEnum().equals( action ) == false ) {
                continue;
            }

            // harder test; make sure names match with wildcards
            if ( isWildCardStringMatch(act.getTag().getNamespaceUri(), uri) &&
                 isWildCardStringMatch(act.getTag().getNamespacePrefix(), prefix) &&
                 isWildCardStringMatch(act.getTag().getLocalName(), localName) ) {
                return true;
            }
        }
        return false;
    }

    private boolean isWildCardStringMatch(final String src, final String tgt) {
        return (src.equals("*") || src.equals(tgt));
    }

    @Override
    public boolean isContainerElement(XMLEntity entity) {
        return false;
    }

    @Override
    public List<XMLParserModule> getModules() {
        return this.modules;
    }

    @Override
    public char getNotableCharacter() {
        return this.notableCharacter;
    }

    @Override
    public int getTextBufferSize() {
        return this.textBufferSize;
    }

    @Override
    public boolean isCompressingWhitespace() {
        return this.compressingWhitespace;
    }

    @Override
    public boolean isRemoveLeadingWhitespace() {
        return this.removeLeadingWhitespace;
    }

}
