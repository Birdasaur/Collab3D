package com.example.collab3d.server.interaction;

public enum InteractionCompletionReason {
    EXPIRED,
    COLLISION,
    CANCELLED_BY_ACTOR,
    INVALIDATED,
    SERVER_SHUTDOWN
}
