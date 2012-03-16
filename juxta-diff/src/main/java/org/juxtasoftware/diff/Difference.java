package org.juxtasoftware.diff;

import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;

import eu.interedition.text.Annotation;

public class Difference {
    public enum Type{ ADD_DEL, CHANGE };
    private final Annotation base;
    private final Annotation witness;
    private final int editDistance;
    private final int group;
    private final Type type;

    public Difference(int group, Type t, Annotation base, Annotation witness, int editDistance) {
        this.group = group;
        this.type = t;
        this.base = base;
        this.witness = witness;
        this.editDistance = editDistance;
    }
    
    public int getGroup() {
        return this.group;
    }
    
    public Type getType() {
        return this.type;
    }

    public Annotation getBase() {
        return base;
    }

    public Annotation getWitness() {
        return witness;
    }

    public int getEditDistance() {
        return editDistance;
    }

    @Override
    public String toString() {
        final ToStringHelper toStringHelper = Objects.toStringHelper(this).addValue(getBase()).addValue(getWitness());
        if (getEditDistance() >= 0) {
            toStringHelper.add("distance", getEditDistance());
        }
        return toStringHelper.toString();
    }
}
