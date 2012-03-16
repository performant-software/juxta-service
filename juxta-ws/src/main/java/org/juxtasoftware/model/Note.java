package org.juxtasoftware.model;

import eu.interedition.text.Range;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class Note {
    private long id;
    private long witnessId;
    private String type;
    private String targetID;
    private Range anchorRange;
    private String content;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
    
    public long getWitnessId() {
        return witnessId;
    }

    public void setWitnessId(long witnessId) {
        this.witnessId = witnessId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTargetID() {
        return targetID;
    }

    public void setTargetID(String targetID) {
        this.targetID = targetID;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Range getAnchorRange() {
        return anchorRange;
    }

    public void setAnchorRange(Range anchorRange) {
        this.anchorRange = anchorRange;
    }
}
