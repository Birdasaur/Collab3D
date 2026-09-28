package com.example.collab3d.common.messages;

import com.example.collab3d.common.interactions.InteractionType;
import com.example.collab3d.common.interactions.SharedInteraction;
import com.jme3.network.AbstractMessage;
import com.jme3.network.serializing.Serializable;

/** Server -> clients authoritative interaction-start broadcast. */
@Serializable
public final class SharedInteractionMessage extends AbstractMessage {

    private long interactionId;
    private long actorObjectId;
    private long interactionSequence;
    private long eventServerTimeNanos;
    private long acceptedServerTimeNanos;
    private int interactionTypeOrdinal;
    private double originX;
    private double originY;
    private double originZ;
    private double directionX;
    private double directionY;
    private double directionZ;
    private long targetObjectId = -1L;

    public SharedInteractionMessage() {
    }

    public SharedInteractionMessage(SharedInteraction interaction) {
        interactionId = interaction.interactionId();
        actorObjectId = interaction.actorObjectId();
        interactionSequence = interaction.interactionSequence();
        eventServerTimeNanos = interaction.eventServerTimeNanos();
        acceptedServerTimeNanos = interaction.acceptedServerTimeNanos();
        interactionTypeOrdinal = interaction.type().ordinal();
        originX = interaction.originX();
        originY = interaction.originY();
        originZ = interaction.originZ();
        directionX = interaction.directionX();
        directionY = interaction.directionY();
        directionZ = interaction.directionZ();
        targetObjectId = interaction.targetObjectId();
    }

    public SharedInteraction toInteraction() {
        InteractionType[] values = InteractionType.values();
        InteractionType type = interactionTypeOrdinal >= 0
                && interactionTypeOrdinal < values.length
                ? values[interactionTypeOrdinal]
                : InteractionType.POINTER_RAY;
        return new SharedInteraction(
                interactionId,
                actorObjectId,
                interactionSequence,
                eventServerTimeNanos,
                acceptedServerTimeNanos,
                type,
                originX,
                originY,
                originZ,
                directionX,
                directionY,
                directionZ,
                targetObjectId);
    }
}
