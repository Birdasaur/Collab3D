package com.example.collab3d.client;

import com.example.collab3d.common.interactions.SharedInteraction;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe latest authoritative interaction state per remote participant.
 * This intentionally stores network state only; JavaFX rendering remains
 * outside this class.
 */
final class SharedInteractionStore {

    private final Map<Long, SharedInteraction> latestByActor =
            new ConcurrentHashMap<>();

    void offer(SharedInteraction interaction) {
        latestByActor.compute(interaction.actorObjectId(), (id, previous) -> {
            if (previous == null
                    || interaction.interactionSequence() > previous.interactionSequence()) {
                return interaction;
            }
            return previous;
        });
    }

    SharedInteraction latest(long actorObjectId) {
        return latestByActor.get(actorObjectId);
    }

    void remove(long actorObjectId) {
        latestByActor.remove(actorObjectId);
    }

    Map<Long, SharedInteraction> snapshot() {
        return Map.copyOf(latestByActor);
    }
}
