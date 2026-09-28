package com.example.collab3d.server.interaction;

import com.example.collab3d.common.geometry.Vector3d;

public record InteractionCollisionEvent(
        long interactionId,
        long actorObjectId,
        long clientSequence,
        long targetObjectId,
        long eventServerTimeNanos,
        Vector3d position,
        Vector3d normal)
        implements InteractionEvent {
}
