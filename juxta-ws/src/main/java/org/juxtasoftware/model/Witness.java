package org.juxtasoftware.model;

import java.util.Date;

import org.json.simple.JSONObject;
import org.juxtasoftware.diff.Comparand;

import com.google.common.base.Objects;
import com.google.gson.annotations.Expose;

import eu.interedition.text.Range;
import eu.interedition.text.Text;

public class Witness extends WorkspaceMember implements Comparand {

    @Expose private String name;
    @Expose private Long xsltId;
    @Expose private Long sourceId;
    @Expose private Date created;
    @Expose private Date updated;
    private Text text;   
    private Range fragmentRange = Range.NULL;

    public Witness(Witness that) {
        this.id = that.id;
        this.workspaceId = that.workspaceId;
        this.name = that.name;
        this.xsltId = that.xsltId;
        this.sourceId = that.sourceId;
        this.text = that.text;
        this.fragmentRange = new Range(that.fragmentRange);
    }
    
    public Witness() {
    }
    
    public String getName() {
        return name;
    }
    public String getJsonName() {
        return JSONObject.escape(this.name);
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long id) {
        this.sourceId = id;
    }

    public Text getText() {
        return text;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    @Override
    public Range getTextRange() {
        return (fragmentRange.equals(Range.NULL) ? new Range(0, text.getLength()) : fragmentRange);
    }

    public void setText(Text text) {
        this.text = text;
    }

    public final Long getXsltId() {
        return xsltId;
    }

    public final void setXsltId(Long xsltId) {
        this.xsltId = xsltId;
    }

    @Override
    public boolean equals(Object obj) {
        if (id != 0 && obj != null && obj instanceof Witness) {
            return id.equals( ((Witness) obj).id );
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return id == 0 ? super.hashCode() : Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return getName();
    }
}
