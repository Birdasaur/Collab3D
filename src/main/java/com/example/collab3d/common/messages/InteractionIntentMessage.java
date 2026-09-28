package com.example.collab3d.common.messages;

import com.example.collab3d.common.interactions.InteractionType;
import com.jme3.network.AbstractMessage;
import com.jme3.network.serializing.Serializable;

/**
 * Client -> server interaction intent.
 *
 * <p>The client supplies its interaction sequence and the event time already
 * mapped into the synchronized server-time domain. The server never trusts a
 * client-supplied participant id; actor identity is derived from the transport
 * connection.</p>
 */
@Serializable
public final class InteractionIntentMessage extends AbstractMessage {

    private long interactionSequence;
    private long eventServerTimeNanos;
    private int interactionTypeOrdinal;
    private double originX;
    private double originY;
    private double originZ;
    private double directionX;
    private double directionY;
    private double directionZ;
    private long targetObjectId = -1L;

    public InteractionIntentMessage() {
    }

    public InteractionIntentMessage(
            long interactionSequence,
            long eventServerTimeNanos,
            InteractionType type,
            double originX,
            double originY,
            double originZ,
            double directionX,
            double directionY,
            double directionZ,
            long targetObjectId) {
        this.interactionSequence = interactionSequence;
        this.eventServerTimeNanos = eventServerTimeNanos;
        this.interactionTypeOrdinal = type.ordinal();
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.directionX = directionX;
        this.directionY = directionY;
        this.directionZ = directionZ;
        this.targetObjectId = targetObjectId;
    }

    public long getInteractionSequence() { return interactionSequence; }
    public long getEventServerTimeNanos() { return eventServerTimeNanos; }
    public InteractionType getInteractionType() {
        InteractionType[] values = InteractionType.values();
        if (interactionTypeOrdinal < 0 || interactionTypeOrdinal >= values.length) {
            return InteractionType.POINTER_RAY;
        }
        return values[interactionTypeOrdinal];
    }
    public double getOriginX() { return originX; }
    public double getOriginY() { return originY; }
    public double getOriginZ() { return originZ; }
    public double getDirectionX() { return directionX; }
    public double getDirectionY() { return directionY; }
    public double getDirectionZ() { return directionZ; }
    public long getTargetObjectId() { return targetObjectId; }
}
