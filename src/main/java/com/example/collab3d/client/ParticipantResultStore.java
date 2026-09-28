package com.example.collab3d.client;

import com.example.collab3d.common.ParticipantResultState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side replica of server-owned participant result state. */
final class ParticipantResultStore {

    private final Map<Long, ParticipantResultState> states =
            new ConcurrentHashMap<>();

    void offer(ParticipantResultState state) {
        states.put(state.objectId(), state);
    }

    void remove(long objectId) {
        states.remove(objectId);
    }

    ParticipantResultState get(long objectId) {
        return states.get(objectId);
    }

    List<ParticipantResultState> snapshot() {
        List<ParticipantResultState> snapshot =
                new ArrayList<>(states.values());
        snapshot.sort(Comparator.comparingLong(
                ParticipantResultState::objectId));
        return List.copyOf(snapshot);
    }
}
