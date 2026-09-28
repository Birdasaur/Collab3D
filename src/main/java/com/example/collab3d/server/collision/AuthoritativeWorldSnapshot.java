package com.example.collab3d.server.collision;

import java.util.Map;

/** Immutable live authoritative world snapshot. */
public record AuthoritativeWorldSnapshot(
        long serverTimeNanos,
        Map<Long, AuthoritativeEntityState> entities) {

    public AuthoritativeWorldSnapshot {
        entities = Map.copyOf(entities);
    }

    public static AuthoritativeWorldSnapshot empty() {
        return new AuthoritativeWorldSnapshot(0L, Map.of());
    }
}
