package com.example.collab3d.server.interaction;

public enum InteractionRejectReason {
    NONE,
    INVALID_PARAMETERS,
    TOO_OLD,
    TOO_FAR_IN_FUTURE,
    ACTOR_NOT_FOUND,
    UNSUPPORTED_TYPE
}
