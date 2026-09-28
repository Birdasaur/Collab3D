package com.example.collab3d.common;

/**
 * A pose sample with timing metadata from both the originating client and the
 * authoritative server.
 *
 * <p>The two nanosecond timestamps are deliberately kept in separate clock
 * domains. clientSampleTimeNanos is meaningful for ordering and elapsed-time
 * calculations from one client. serverReceiveTimeNanos is meaningful on the
 * server. They must not be directly subtracted from one another.</p>
 */
public record TimedPose3d(
        Pose3d pose,
        long sequence,
        long clientSampleTimeNanos,
        long serverReceiveTimeNanos) {

    public TimedPose3d {
        if (pose == null) {
            throw new IllegalArgumentException("pose must not be null");
        }
    }
}
