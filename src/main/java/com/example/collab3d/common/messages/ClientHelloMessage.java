package com.example.collab3d.common.messages;

import com.jme3.network.AbstractMessage;
import com.jme3.network.serializing.Serializable;

@Serializable
public final class ClientHelloMessage extends AbstractMessage {
    private String displayName;

    public ClientHelloMessage() {
    }

    public ClientHelloMessage(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
