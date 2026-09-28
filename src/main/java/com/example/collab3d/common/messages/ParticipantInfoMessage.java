package com.example.collab3d.common.messages;

import com.jme3.network.AbstractMessage;
import com.jme3.network.serializing.Serializable;

@Serializable
public final class ParticipantInfoMessage extends AbstractMessage {
    private long objectId;
    private String displayName;
    private double hueDegrees;

    public ParticipantInfoMessage() {
    }

    public ParticipantInfoMessage(long objectId, String displayName, double hueDegrees) {
        this.objectId = objectId;
        this.displayName = displayName;
        this.hueDegrees = hueDegrees;
    }

    public long getObjectId() {
        return objectId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getHueDegrees() {
        return hueDegrees;
    }
}
