package com.example.collab3d.common;

import com.example.collab3d.common.messages.AssignedIdentityMessage;
import com.example.collab3d.common.messages.ClientHelloMessage;
import com.example.collab3d.common.messages.InteractionCollisionMessage;
import com.example.collab3d.common.messages.InteractionIntentMessage;
import com.example.collab3d.common.messages.ParticipantInfoMessage;
import com.example.collab3d.common.messages.ParticipantRemovedMessage;
import com.example.collab3d.common.messages.ParticipantResultStateMessage;
import com.example.collab3d.common.messages.PoseInputMessage;
import com.example.collab3d.common.messages.ServerNoticeMessage;
import com.example.collab3d.common.messages.SharedInteractionMessage;
import com.example.collab3d.common.messages.TimeSyncRequestMessage;
import com.example.collab3d.common.messages.TimeSyncResponseMessage;
import com.jme3.network.serializing.Serializer;
import java.util.concurrent.atomic.AtomicBoolean;

/** Registers application messages in one deterministic client/server order. */
public final class NetworkSerializers {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private NetworkSerializers() {
    }

    public static void registerAll() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        Serializer.registerClass(ClientHelloMessage.class);
        Serializer.registerClass(AssignedIdentityMessage.class);
        Serializer.registerClass(ParticipantInfoMessage.class);
        Serializer.registerClass(ParticipantRemovedMessage.class);
        Serializer.registerClass(PoseInputMessage.class);
        Serializer.registerClass(ServerNoticeMessage.class);

        /*
         * Keep these registrations in the same position/order on every client
         * and server. Adding them changes the wire protocol, which is why
         * NetworkConstants.PROTOCOL_VERSION was incremented as the
         * registry evolved.
         */
        Serializer.registerClass(TimeSyncRequestMessage.class);
        Serializer.registerClass(TimeSyncResponseMessage.class);

        /* Protocol version 4 interaction start/collision messages. */
        Serializer.registerClass(InteractionIntentMessage.class);
        Serializer.registerClass(SharedInteractionMessage.class);
        Serializer.registerClass(InteractionCollisionMessage.class);

        /* Protocol version 5 persistent participant result state. */
        Serializer.registerClass(ParticipantResultStateMessage.class);
    }
}
