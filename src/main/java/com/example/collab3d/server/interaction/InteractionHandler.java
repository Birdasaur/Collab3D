package com.example.collab3d.server.interaction;

import com.example.collab3d.common.interactions.InteractionType;

public interface InteractionHandler {
    InteractionType type();

    InteractionStartResult start(
            long interactionId,
            StartInteractionCommand command,
            long acceptedServerTimeNanos,
            InteractionContext context);

    InteractionUpdateResult catchUp(
            ActiveInteraction interaction,
            long targetServerTimeNanos,
            InteractionContext context);

    InteractionUpdateResult update(
            ActiveInteraction interaction,
            long previousServerTimeNanos,
            long currentServerTimeNanos,
            InteractionContext context);
}
