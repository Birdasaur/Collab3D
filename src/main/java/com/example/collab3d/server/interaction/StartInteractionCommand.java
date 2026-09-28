package com.example.collab3d.server.interaction;

import com.example.collab3d.common.geometry.Vector3d;
import com.example.collab3d.common.interactions.InteractionType;

/** Immutable command transferred from a network thread to the interaction thread. */
public record StartInteractionCommand(
        long actorObjectId,
        long clientSequence,
        long eventServerTimeNanos,
        InteractionType type,
        Vector3d submittedOrigin,
        Vector3d direction) {
}
