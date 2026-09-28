package com.example.collab3d.server;

import com.example.collab3d.common.Pose3d;

/**
 * Result of querying authoritative participant state at a historical server time.
 *
 * <p>Server rewind deliberately does not extrapolate. A successful query is
 * either an exact authoritative sample or an interpolation between two
 * authoritative samples that bracket the requested time.</p>
 */
public record RewindPoseResult(
        Status status,
        long objectId,
        long requestedServerTimeNanos,
        Pose3d pose,
        long oldestAvailableServerTimeNanos,
        long newestAvailableServerTimeNanos) {

    public enum Status {
        EXACT,
        INTERPOLATED,
        TOO_OLD,
        TOO_NEW,
        PARTICIPANT_NOT_FOUND,
        NO_HISTORY
    }

    public boolean available() {
        return status == Status.EXACT || status == Status.INTERPOLATED;
    }

    static RewindPoseResult unavailable(
            Status status,
            long objectId,
            long requestedServerTimeNanos,
            long oldestAvailableServerTimeNanos,
            long newestAvailableServerTimeNanos) {

        return new RewindPoseResult(
                status,
                objectId,
                requestedServerTimeNanos,
                null,
                oldestAvailableServerTimeNanos,
                newestAvailableServerTimeNanos);
    }
}
