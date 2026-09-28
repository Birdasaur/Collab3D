package com.example.collab3d.common.messages;

import com.example.collab3d.common.geometry.Vector3d;
import com.example.collab3d.common.interactions.InteractionCollision;
import com.jme3.network.AbstractMessage;
import com.jme3.network.serializing.Serializable;

/** Server -> clients authoritative interaction collision event. */
@Serializable
public final class InteractionCollisionMessage extends AbstractMessage {

    private long interactionId;
    private long actorObjectId;
    private long clientSequence;
    private long targetObjectId;
    private long eventServerTimeNanos;
    private double positionX;
    private double positionY;
    private double positionZ;
    private double normalX;
    private double normalY;
    private double normalZ;

    public InteractionCollisionMessage() {
    }

    public InteractionCollisionMessage(InteractionCollision collision) {
        interactionId = collision.interactionId();
        actorObjectId = collision.actorObjectId();
        clientSequence = collision.clientSequence();
        targetObjectId = collision.targetObjectId();
        eventServerTimeNanos = collision.eventServerTimeNanos();
        positionX = collision.position().x();
        positionY = collision.position().y();
        positionZ = collision.position().z();
        normalX = collision.normal().x();
        normalY = collision.normal().y();
        normalZ = collision.normal().z();
    }

    public InteractionCollision toCollision() {
        return new InteractionCollision(
                interactionId,
                actorObjectId,
                clientSequence,
                targetObjectId,
                eventServerTimeNanos,
                new Vector3d(positionX, positionY, positionZ),
                new Vector3d(normalX, normalY, normalZ));
    }
}
