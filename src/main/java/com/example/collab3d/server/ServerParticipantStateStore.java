package com.example.collab3d.server;

import com.example.collab3d.common.ParticipantResultState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative server-owned participant result-state store.
 *
 * <p>Clients never submit these values directly. They are derived from
 * authoritative server events: accepted interaction starts increment shots
 * fired, and authoritative participant collisions increment hit count and
 * score.</p>
 */
final class ServerParticipantStateStore {

    private static final long SCORE_PER_HIT = 1L;

    private final Map<Long, ParticipantResultState> states =
            new ConcurrentHashMap<>();

    ParticipantResultState registerParticipant(long objectId) {
        ParticipantResultState initial = new ParticipantResultState(
                objectId,
                0L,
                0L,
                0L);
        states.put(objectId, initial);
        return initial;
    }

    void removeParticipant(long objectId) {
        states.remove(objectId);
    }

    boolean containsParticipant(long objectId) {
        return states.containsKey(objectId);
    }

    ParticipantResultState recordShot(long objectId) {
        return states.computeIfPresent(
                objectId,
                (ignored, previous) -> new ParticipantResultState(
                        previous.objectId(),
                        previous.shotsFired() + 1L,
                        previous.hitCount(),
                        previous.score()));
    }

    ParticipantResultState recordHit(long objectId) {
        return states.computeIfPresent(
                objectId,
                (ignored, previous) -> new ParticipantResultState(
                        previous.objectId(),
                        previous.shotsFired(),
                        previous.hitCount() + 1L,
                        previous.score() + SCORE_PER_HIT));
    }

    List<ParticipantResultState> snapshot() {
        List<ParticipantResultState> snapshot =
                new ArrayList<>(states.values());
        snapshot.sort(Comparator.comparingLong(
                ParticipantResultState::objectId));
        return List.copyOf(snapshot);
    }
}
