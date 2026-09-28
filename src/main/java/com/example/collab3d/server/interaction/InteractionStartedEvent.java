package com.example.collab3d.server.interaction;

import com.example.collab3d.common.interactions.SharedInteraction;

public record InteractionStartedEvent(SharedInteraction interaction)
        implements InteractionEvent {
    @Override public long interactionId() { return interaction.interactionId(); }
    @Override public long actorObjectId() { return interaction.actorObjectId(); }
    @Override public long eventServerTimeNanos() { return interaction.eventServerTimeNanos(); }
}
