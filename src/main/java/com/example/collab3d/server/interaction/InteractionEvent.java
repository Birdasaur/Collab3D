package com.example.collab3d.server.interaction;

public interface InteractionEvent {
    long interactionId();
    long actorObjectId();
    long eventServerTimeNanos();
}
