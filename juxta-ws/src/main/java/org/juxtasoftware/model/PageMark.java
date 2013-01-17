package org.juxtasoftware.model;

/**
 * Data describing a marks on a page. These marks include TEI <pb> and <l> tags
 * 
 * @author loufoster
 *
 */
public class PageMark {
    public enum Type{PAGE_BREAK, LINE_NUMBER};
    
    private Long id;
    private Long witnessId;
    private long offset;
    private String label = "";
    private Type type;
    
    public final Long getId() {
        return id;
    }
    public final void setId(Long id) {
        this.id = id;
    }
    public final Long getWitnessId() {
        return witnessId;
    }
    public final void setWitnessId(Long witnessId) {
        this.witnessId = witnessId;
    }
    public final long getOffset() {
        return offset;
    }
    public final void setOffset(long offset) {
        this.offset = offset;
    }
    public final String getLabel() {
        return label;
    }
    public final void setLabel(String label) {
        this.label = label;
    }
    public Type getType() {
        return type;
    }
    public void setType(Type type) {
        this.type = type;
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
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PageMark other = (PageMark) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        return true;
    }
    @Override
    public String toString() {
        return "PageMark [id=" + id + ", type=" + type + ", witnessId=" + witnessId + ", offset=" + offset + ", label=" + label + "]";
    }
    
    public String toHtml() {
        StringBuilder out = new StringBuilder();
        if (this.type.equals(Type.LINE_NUMBER)) {
            out.append("<span class=\"line-number\" id=\"line-num-");
            out.append( getId()).append("\">");
            out.append( getLabel()).append("</span>");
        } else {
            out.append( "<span class=\"page-break\" title=\"").append(getLabel()).append("\" >");
            out.append("Page Break");
            out.append("</span>");
            out.append( "<div class=\"page-break\" ></div>");
        }
        return out.toString();
    }
    
    
}
