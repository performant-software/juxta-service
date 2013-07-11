package org.juxtasoftware.util;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Map;

import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;

import org.json.simple.JSONObject;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

public class BackgroundTaskStatus {
    public enum Status {PENDING, PROCESSING, COMPLETE, CANCEL_REQUESTED, CANCELLED, FAILED};
    
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
    private final BoundedRangeModel boundedRangeModel = new DefaultBoundedRangeModel();
    private final String title;
    private String note;
    private Status status;
    private final Map<Integer, BackgroundTaskSegment> segments = Maps.newLinkedHashMap();

    public BackgroundTaskStatus(String title) {
        this.title = title;
        this.boundedRangeModel.setMinimum(0);
        this.boundedRangeModel.setMaximum(100);
        this.status = Status.PENDING;
    }

    public BackgroundTaskSegment add(int weight, BackgroundTaskSegment segment) {
        this.status = Status.PROCESSING;
        this.segments.put(weight, segment);
        updateValue();
        segment.addChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                updateValue();
            }
        });
        
        return segment;
    }
    
    public void begin() {
        if ( this.status.equals(Status.PENDING)) {
            this.status = Status.PROCESSING;
        }
    }
    
    public void finish() {
        this.status = Status.COMPLETE;
    }
    
    public boolean isActive() {
        return  ( this.status.equals(Status.PENDING) || this.status.equals(Status.PROCESSING) );
    }
    
    public void fail( final String reason ) {
        if ( isActive() ) {
            this.status = Status.FAILED;
            setNote( JSONObject.escape(reason) );
        }
    }

    public void cancel() {
        if ( isActive() ) {
            this.status = Status.CANCEL_REQUESTED;
        }
    }

    public final Status getStatus() {
        return this.status;
    }

    public String getNote() {
        return this.note;
    }

    public void setNote(String note) {
        note = (note == null ? title : note);
        if (!Objects.equal(this.note, note)) {
            final String oldValue = this.note;
            propertyChangeSupport.firePropertyChange("note", oldValue, this.note = note);
        }
    }
    
    public BoundedRangeModel getBoundedRangeModel() {
        return boundedRangeModel;
    }

    private void updateValue() {
        float value = 0;
        int totalWeight = 0;
        for (Map.Entry<Integer, BackgroundTaskSegment> te : segments.entrySet()) {
            final Integer weight = te.getKey();
            totalWeight += weight;

            final BackgroundTaskSegment segment = te.getValue();
            final int maxValue = segment.getMaxValue();
            if (maxValue > 0) {
                value += weight * (segment.getValue() / (maxValue * 1.0f));
            }            
        }

        this.boundedRangeModel.setValue(Math.round(100 * (value / totalWeight)));
        
        // Check for status changes...
        if (this.status.equals(Status.CANCEL_REQUESTED)) {
            this.status = Status.CANCELLED;
            throw new BackgroundTaskCanceledException();
        } else if ( this.boundedRangeModel.getValue() == this.boundedRangeModel.getMaximum() ) {
            this.status = Status.COMPLETE;
        }
    }

    public void addChangeListener(PropertyChangeListener l) {
        propertyChangeSupport.addPropertyChangeListener(l);
    }

    public void removeChangeListener(PropertyChangeListener l) {
        propertyChangeSupport.removePropertyChangeListener(l);
    }
    
    @Override
    public String toString() {
        return this.status.toString();
    }
}
