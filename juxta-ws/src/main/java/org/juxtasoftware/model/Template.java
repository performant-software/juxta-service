package org.juxtasoftware.model;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.google.gson.annotations.Expose;


public final class Template extends WorkspaceMember  {
    
    @Expose private String name;
    @Expose private WildcardQName rootElement = new WildcardQName("*", "*", "*");  
    @Expose private Set<TagAction> tagActions = new HashSet<TagAction>();
    @Expose private boolean isDefault = false;
        
    public Template() {
    }
    
    public Template(Template that) {
        this.id = null;
        this.name = that.name;
        this.rootElement = new WildcardQName( that.rootElement);
        this.isDefault = that.isDefault;
        this.tagActions.addAll( that.tagActions );
    }

    public  WildcardQName getRootElement() {
        return rootElement;
    }

    public final void setRootElement(WildcardQName rootElement) {
        this.rootElement = rootElement;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<TagAction> getTagActions() {
        return tagActions;
    }
    
    /**
     * If it is not already excluded, mark the tag <code>localName</code>
     * as excluded. When doing so, clear all other actions associated
     * with this tag as they will now be invalid.
     * 
     * @param localName
     */
    public void exclude( final String localName ) {
        boolean found = false;
        for ( Iterator<TagAction> itr = this.tagActions.iterator(); itr.hasNext(); ) {
            TagAction act = itr.next();
            if ( act.getTag().equals(localName) ) {
                if (  act.getActionAsEnum().equals( TagAction.Action.EXCLUDE)) {
                    found = true;
                } else {
                    itr.remove();
                }
            }
        }
        if ( found == false ) {
            TagAction tagAct = new TagAction();
            tagAct.setAction( TagAction.Action.EXCLUDE );
            tagAct.setTag( WildcardQName.fromString(localName));
            this.tagActions.add(tagAct );
        }
    }
    
    /**
     * Ensure that the tag <code>localName</code> is not excluded. If it 
     * was previously marked as excluded, this will be reversed.
     * @param localName
     */
    public void include( final String localName ) {
        for ( TagAction act : this.tagActions ) {
            if ( act.getTag().equals(localName) &&
                 act.getActionAsEnum().equals( TagAction.Action.EXCLUDE)) {
                this.tagActions.remove(act);
                break;
            }
        }
    }

    public void setTagActions(Set<TagAction> tagActions) {
        this.tagActions = tagActions;
    }
    
    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    @Override
    public String toString() {
        return "Template [id=" + id + ", name=" + name + ", tagActions=" + tagActions + "]";
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
        Template other = (Template) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        return true;
    }
    
    /**
     * A QNAme that can accept wildcard '*' characters for uri and prefix
     * @author loufoster
     *
     */
    public static class WildcardQName {
        @Expose final private String namespaceUri;
        @Expose final private String namespacePrefix;
        @Expose final private String localName;
        
        /**
         * Create from a string of the format uri:prefix:name.
         * URI and prefix are option and will default to *.
         * @param src
         * @return
         */
        public static WildcardQName fromString( final String data ) {
            String[] parts = data.split(":");
            if (parts.length == 3 ) {
                return new WildcardQName(parts[0], parts[1], parts[2]);
            }
            if (parts.length == 2 ) {
                return new WildcardQName("*", parts[0], parts[1]);
            }
            return new WildcardQName("*", "*", data);
        }
        
        public WildcardQName( WildcardQName that ) {
            this.namespacePrefix = that.namespacePrefix;
            this.localName = that.localName;
            this.namespaceUri = that.namespaceUri;
        }
        
        public WildcardQName(String uri, String prefix, String local ) {
            this.namespaceUri  = uri;
            this.namespacePrefix = prefix;
            this.localName = local;
        }

        public String getNamespaceUri() {
            return namespaceUri;
        }

        public String getNamespacePrefix() {
            return namespacePrefix;
        }

        public String getLocalName() {
            return localName;
        }

        @Override
        public String toString() {
            return namespaceUri + ":" + namespacePrefix + ":" + localName;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((localName == null) ? 0 : localName.hashCode());
            result = prime * result + ((namespacePrefix == null) ? 0 : namespacePrefix.hashCode());
            result = prime * result + ((namespaceUri == null) ? 0 : namespaceUri.hashCode());
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
            
            if ( obj.getClass() == String.class ) {
                return ( WildcardQName.fromString( (String)obj).toString().equals(this.toString()));
            }
            
            if (getClass() != obj.getClass()) {
                return false;
            }

            WildcardQName other = (WildcardQName) obj;
            return this.toString().equals(other.toString());
        }
    }


    /**
     * TagAction binds an XML tag to a parse action
     * @author loufoster
     *
     */
    public static class TagAction {
        
        public enum Action {INCLUDE, EXCLUDE, NEW_LINE, NOTABLE};
        
        //all of these are exposed to json by default
        @Expose private Long id;
        @Expose private WildcardQName tag;
        @Expose private Action action;
        
        // template is NOT
        private Template template;

        public Long getId() {
            return id;
        }
        
        public void setId(final Long id) {
            this.id = id;
        }
        
        public Template getTemplate() {
            return template;
        }

        public void setTemplate(Template template) {
            this.template = template;
        }
        
        public void setTag( WildcardQName tag ) {
            this.tag = tag;
        }
        
        public WildcardQName getTag() {
            return this.tag;
        }
        
        public String getAction() {
            return action.toString();
        }

        public Action getActionAsEnum() {
            return action;
        }
        
        public void setAction(String action) {
            this.action = Action.valueOf(action.toUpperCase());
        }
        public void setAction( Action act ) {
            this.action = act;
        }

        @Override
        public String toString() {
            return "TagAction [id=" + id + ", "+this.tag.toString()+", action=" + action + "]";
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((action == null) ? 0 : action.hashCode());
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result + ((tag == null) ? 0 : tag.hashCode());
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
            TagAction other = (TagAction) obj;
            if (action == null) {
                if (other.action != null) {
                    return false;
                }
            } else if (!action.equals(other.action)) {
                return false;
            }
            if (id == null) {
                if (other.id != null) {
                    return false;
                }
            } else if (!id.equals(other.id)) {
                return false;
            }
            if (tag == null) {
                if (other.tag != null) {
                    return false;
                }
            } else if (!tag.equals(other.tag)) {
                return false;
            }
            return true;
        }
    }
}
