package org.juxtasoftware.resource.heatmap;

import java.util.Iterator;
import java.util.List;

import org.juxtasoftware.model.PageMark;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Used to inject page break tagging into the html of
 * the current line in the heatmap
 * 
 * @author loufoster
 *
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class PageMarkInjector implements StreamInjector<PageMark> {

    private List<PageMark> marks;
    private Iterator<PageMark> markItr;
    private PageMark currMark;
    private final String BREAK_MARKER = "&nbsp;|&nbsp;";
    
    @Override
    public void initialize( List<PageMark> data) {
        this.marks = data;
        this.markItr = this.marks.iterator();
        if ( this.markItr.hasNext() ) {
            this.currMark = this.markItr.next();
        }
    }
    
    @Override
    public List<PageMark> getData() {
        return this.marks;
    }
    
    @Override
    public boolean hasContent(long pos) {
        if ( this.currMark == null) {
            return false;
        }
        
        return ( this.currMark.getOffset()== pos);
    }
    
    @Override
    public void injectContentStart(StringBuilder line, final long currPositon) {
        if ( this.currMark != null ) {
            if ( this.currMark.getOffset() == currPositon) { // x25ae x25c6
                if ( this.currMark.getType().equals(PageMark.Type.PAGE_BREAK)) {
                    line.append( "<span title=\"").append(this.currMark.getLabel());
                    line.append("\" class=\"page-break\" id=\"break-");
                    line.append(this.currMark.getId()).append("\">");
                    line.append(BREAK_MARKER).append("</span>" );
                } else if ( this.currMark.getType().equals(PageMark.Type.LINE_NUMBER)) {
                    line.append( "<span class=\"line-number\" id=\"line-num-");
                    line.append(this.currMark.getId()).append("\">");
                    line.append( this.currMark.getLabel() ).append("</span>" );
                }
                this.currMark = null;
                if ( this.markItr.hasNext() ) {
                    this.currMark = this.markItr.next();
                }
            } 
        }
    }
    
    @Override
    public void injectContentEnd(StringBuilder line, final long currPosition) {
        // Page mark content atomic (all injected at start). No need
        // for anything to be injected to end it.
    }

}
