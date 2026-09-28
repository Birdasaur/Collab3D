package com.example.collab3d.common.interactions;

/** Immutable server-authoritative interaction start delivered to clients. */
public record SharedInteraction(
        long interactionId,
        long actorObjectId,
        long interactionSequence,
        long eventServerTimeNanos,
        long acceptedServerTimeNanos,
        InteractionType type,
        double originX,
        double originY,
        double originZ,
        double directionX,
        double directionY,
        double directionZ,
        long targetObjectId) {

    public boolean hasTarget() {
        return targetObjectId >= 0L;
    }
}
