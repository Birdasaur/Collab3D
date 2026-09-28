package com.example.collab3d.server.interaction;

import com.example.collab3d.common.interactions.InteractionCollision;
import com.example.collab3d.common.messages.InteractionCollisionMessage;
import com.example.collab3d.common.messages.SharedInteractionMessage;
import com.jme3.network.Message;
import java.util.function.Consumer;

/** Converts authoritative domain events into network replication messages. */
public final class InteractionReplicationService {

    private final Consumer<Message> broadcaster;

    public InteractionReplicationService(Consumer<Message> broadcaster) {
        this.broadcaster = broadcaster;
    }

    public void replicate(InteractionEvent event) {
        if (event instanceof InteractionStartedEvent started) {
            SharedInteractionMessage message = new SharedInteractionMessage(
                    started.interaction());
            message.setReliable(true);
            broadcaster.accept(message);
        } else if (event instanceof InteractionCollisionEvent collision) {
            InteractionCollisionMessage message = new InteractionCollisionMessage(
                    new InteractionCollision(
                            collision.interactionId(),
                            collision.actorObjectId(),
                            collision.clientSequence(),
                            collision.targetObjectId(),
                            collision.eventServerTimeNanos(),
                            collision.position(),
                            collision.normal()));
            message.setReliable(true);
            broadcaster.accept(message);
        }
    }
}
