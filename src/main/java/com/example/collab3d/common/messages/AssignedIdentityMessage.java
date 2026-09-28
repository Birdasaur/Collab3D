package com.example.collab3d.common.messages;

import com.jme3.network.AbstractMessage;
import com.jme3.network.serializing.Serializable;

@Serializable
public final class AssignedIdentityMessage extends AbstractMessage {
    private long objectId;
    private String displayName;

    public AssignedIdentityMessage() {
    }

    public AssignedIdentityMessage(long objectId, String displayName) {
        this.objectId = objectId;
        this.displayName = displayName;
    }

    public long getObjectId() {
        return objectId;
    }

    public String getDisplayName() {
        return displayName;
    }
}
