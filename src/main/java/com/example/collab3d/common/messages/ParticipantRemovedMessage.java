package com.example.collab3d.common.messages;

import com.jme3.network.AbstractMessage;
import com.jme3.network.serializing.Serializable;

@Serializable
public final class ParticipantRemovedMessage extends AbstractMessage {
    private long objectId;

    public ParticipantRemovedMessage() {
    }

    public ParticipantRemovedMessage(long objectId) {
        this.objectId = objectId;
    }

    public long getObjectId() {
        return objectId;
    }
}
