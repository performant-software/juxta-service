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
public class BreakInjector implements StreamInjector<PageMark> {

    private List<PageMark> breaks;
    private Iterator<PageMark> breakItr;
    private PageMark currBreak;
    private final String BREAK_MARKER = "&nbsp;|&nbsp;";
    
    @Override
    public void initialize( List<PageMark> data) {
        this.breaks = data;
        this.breakItr = this.breaks.iterator();
        if ( this.breakItr.hasNext() ) {
            this.currBreak = this.breakItr.next();
        }
    }
    
    @Override
    public List<PageMark> getData() {
        return this.breaks;
    }
    
    @Override
    public boolean hasContent(long pos) {
        if ( this.currBreak == null) {
            return false;
        }
        
        return ( this.currBreak.getOffset()== pos);
    }
    
    @Override
    public void injectContentStart(StringBuilder line, final long currPositon) {
        if ( this.currBreak != null ) {
            if ( this.currBreak.getOffset() == currPositon) { // x25ae x25c6
                line.append( "<span title=\"").append(currBreak.getLabel());
                line.append("\" class=\"page-break\" id=\"break-");
                line.append(this.currBreak.getId()).append("\">");
                line.append(BREAK_MARKER).append("</span>" );
                this.currBreak = null;
                if ( this.breakItr.hasNext() ) {
                    this.currBreak = this.breakItr.next();
                }
            } 
        }
    }
    
    @Override
    public void injectContentEnd(StringBuilder line, final long currPosition) {
        // PB content atomic (all injected at start). No need
        // for anything to be injected to end it.
    }

}
