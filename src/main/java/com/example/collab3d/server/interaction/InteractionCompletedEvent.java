package com.example.collab3d.server.interaction;

public record InteractionCompletedEvent(
        long interactionId,
        long actorObjectId,
        long eventServerTimeNanos,
        InteractionCompletionReason reason)
        implements InteractionEvent {
}
