package com.example.collab3d.common.messages;

import com.jme3.network.AbstractMessage;
import com.jme3.network.serializing.Serializable;

@Serializable
public final class ServerNoticeMessage extends AbstractMessage {
    private String text;

    public ServerNoticeMessage() {
    }

    public ServerNoticeMessage(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
