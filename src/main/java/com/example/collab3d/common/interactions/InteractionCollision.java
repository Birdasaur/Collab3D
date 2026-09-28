package com.example.collab3d.common.interactions;

import com.example.collab3d.common.geometry.Vector3d;

/** Authoritative collision event replicated from server to clients. */
public record InteractionCollision(
        long interactionId,
        long actorObjectId,
        long clientSequence,
        long targetObjectId,
        long eventServerTimeNanos,
        Vector3d position,
        Vector3d normal) {
}
