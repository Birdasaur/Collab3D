package com.example.collab3d.common;

/**
 * Server-owned persistent result state for one collaboration participant.
 *
 * <p>This deliberately remains small for the API demonstration. The server is
 * the only authority allowed to mutate these counters. Accuracy is derived
 * from authoritative counters rather than replicated as independent state.</p>
 */
public record ParticipantResultState(
        long objectId,
        long shotsFired,
        long hitCount,
        long score) {

    /** Returns hit accuracy as a percentage in the range [0, 100]. */
    public double accuracyPercent() {
        return shotsFired <= 0L
                ? 0.0
                : (100.0 * hitCount) / shotsFired;
    }
}
