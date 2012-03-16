package org.juxtasoftware.util;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class BackgroundTaskSegment {
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    private int value;
    private int maxValue;
    
    public BackgroundTaskSegment() {
    }
    
    public BackgroundTaskSegment(int maxValue) {
        this.maxValue = maxValue;
    }
    
    public int getValue() {
        return value;
    }
    
    public void setValue(int value) {
        if (this.value != value) {
            final int oldValue = this.value;
            propertyChangeSupport.firePropertyChange("value", oldValue, this.value = value);
        }
    }

    public int getMaxValue() {
        return maxValue;
    }
    
    public void setMaxValue(int maxValue) {
        if (this.maxValue != maxValue) {
            int oldValue = this.maxValue;
            propertyChangeSupport.firePropertyChange("maxValue", oldValue, this.maxValue = maxValue);
        }
    }

    public void incrementValue() {
        setValue(this.value + 1);
    }
    
    public void addChangeListener(PropertyChangeListener l) {
        propertyChangeSupport.addPropertyChangeListener(l);
    }
    
    public void removeChangeListener(PropertyChangeListener l) {
        propertyChangeSupport.removePropertyChangeListener(l);
    }
}
