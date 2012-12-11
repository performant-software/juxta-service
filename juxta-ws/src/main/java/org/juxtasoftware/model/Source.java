package org.juxtasoftware.model;

import java.util.Date;
import com.google.common.base.Objects;
import eu.interedition.text.Text;

public class Source extends WorkspaceMember {

    public enum Type {TXT, XML, HTML, WIKI};
    private String name;
    private Text text;
    private Date created;
    private Source.Type type;
      
    public final String getName() {
        return name;
    }

    public final void setName(String name) {
        this.name = name;
    }
    
    public final Type getType() {
        return type;
    }

    public final void setType(String type) {
        this.type = Source.Type.valueOf(type);
    }
    public final void setType(Source.Type type) {
        this.type = type;
    }

    public Text getText() {
        return text;
    }

    public void setText(Text content) {
        this.text = content;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    @Override
    public int hashCode() {
        return (id == 0 ? super.hashCode() : Objects.hashCode(id));
    }

    @Override
    public boolean equals(Object obj) {
        if (id != 0 && obj != null && obj instanceof Source) {
            return id.equals( ((Source)obj).id );
        }
        return super.equals(obj);
    }

    @Override
    public String toString() {
        return "Source [id=" + id + ", type="+type+", name=" + name + "]";
    }
}
