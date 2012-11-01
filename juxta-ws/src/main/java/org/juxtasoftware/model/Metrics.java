package org.juxtasoftware.model;

public class Metrics {
    private Long id;
    private String workspace;
    private int numSources;
    private int minSourceSize;
    private int maxSourceSize;
    private int meanSourceSize;
    private int totalSourcesSize;
    private int totalSecsCollating;
    private int numCollationsStarted;
    private int numCollationsFinished;
    
    public Long getId() {
        return id;
    }
    public void setId( Long id ) {
        this.id = id;
    }
    public String getWorkspace() {
        return workspace;
    }
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }
    public int getNumSources() {
        return numSources;
    }
    public void setNumSources(int numSources) {
        this.numSources = numSources;
    }
    public int getMinSourceSize() {
        return minSourceSize;
    }
    public void setMinSourceSize(int minSourceSize) {
        this.minSourceSize = minSourceSize;
    }
    public int getMaxSourceSize() {
        return maxSourceSize;
    }
    public void setMaxSourceSize(int maxSourceSize) {
        this.maxSourceSize = maxSourceSize;
    }
    public int getMeanSourceSize() {
        return meanSourceSize;
    }
    public void setMeanSourceSize(int meanSourceSize) {
        this.meanSourceSize = meanSourceSize;
    }
    public int getTotalSourcesSize() {
        return totalSourcesSize;
    }
    public void setTotalSourcesSize(int totalSourcesSize) {
        this.totalSourcesSize = totalSourcesSize;
    }
    public int getTotalSecsCollating() {
        return totalSecsCollating;
    }
    public void setTotalSecsCollating(int totalSecsCollating) {
        this.totalSecsCollating = totalSecsCollating;
    }
    public int getNumCollationsStarted() {
        return numCollationsStarted;
    }
    public void setNumCollationsStarted(int numCollationsStarted) {
        this.numCollationsStarted = numCollationsStarted;
    }
    public int getNumCollationsFinished() {
        return numCollationsFinished;
    }
    public void setNumCollationsFinished(int numCollationsFinished) {
        this.numCollationsFinished = numCollationsFinished;
    }
}
