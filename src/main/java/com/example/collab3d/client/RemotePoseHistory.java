package com.example.collab3d.client;

import com.example.collab3d.common.NetworkConstants;
import com.example.collab3d.common.Pose3d;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Small ordered history of authoritative remote poses keyed by the server-time
 * timestamp carried in {@link Pose3d#sampleTimeNanos()}.
 *
 * <p>The history is intentionally bounded by time rather than by an arbitrary
 * sample count. At 120 Hz the default 500 ms retention window is only about 60
 * samples per participant, but it is long enough for interpolation, bounded
 * extrapolation, diagnostics, and later rewind/lag-compensation work.</p>
 */
final class RemotePoseHistory {

    private final Deque<Pose3d> samples = new ArrayDeque<>();

    synchronized void offer(Pose3d pose) {
        if (pose == null) {
            return;
        }

        Pose3d normalized = pose.normalized();
        long sampleTime = normalized.sampleTimeNanos();
        if (sampleTime <= 0L) {
            return;
        }

        Pose3d latest = samples.peekLast();
        if (latest != null) {
            long latestTime = latest.sampleTimeNanos();

            if (sampleTime < latestTime) {
                // Ignore stale/out-of-order authoritative samples.
                return;
            }

            if (sampleTime == latestTime) {
                // Keep the newest value for a repeated frame timestamp.
                samples.removeLast();
            }
        }

        samples.addLast(normalized);
        prune(sampleTime - NetworkConstants.REMOTE_HISTORY_DURATION_NANOS);
    }

    synchronized Pose3d latest() {
        return samples.peekLast();
    }

    synchronized Pose3d previous() {
        if (samples.size() < 2) {
            return null;
        }

        Iterator<Pose3d> iterator = samples.descendingIterator();
        iterator.next(); // latest
        return iterator.next();
    }

    synchronized Bracket bracket(long targetServerTimeNanos) {
        if (samples.isEmpty()) {
            return null;
        }

        Pose3d first = samples.peekFirst();
        Pose3d last = samples.peekLast();

        if (targetServerTimeNanos <= first.sampleTimeNanos()) {
            return new Bracket(first, first);
        }

        if (targetServerTimeNanos >= last.sampleTimeNanos()) {
            return new Bracket(last, null);
        }

        Pose3d before = first;
        for (Pose3d sample : samples) {
            if (sample.sampleTimeNanos() >= targetServerTimeNanos) {
                return new Bracket(before, sample);
            }
            before = sample;
        }

        return new Bracket(last, null);
    }

    synchronized int size() {
        return samples.size();
    }

    synchronized long spanNanos() {
        if (samples.size() < 2) {
            return 0L;
        }
        return Math.max(
                0L,
                samples.peekLast().sampleTimeNanos()
                        - samples.peekFirst().sampleTimeNanos());
    }

    synchronized void clear() {
        samples.clear();
    }

    private void prune(long cutoffNanos) {
        while (samples.size() > 2) {
            Pose3d first = samples.peekFirst();
            if (first == null || first.sampleTimeNanos() >= cutoffNanos) {
                break;
            }
            samples.removeFirst();
        }
    }

    record Bracket(Pose3d before, Pose3d after) {

        boolean canInterpolate() {
            return before != null
                    && after != null
                    && after.sampleTimeNanos() > before.sampleTimeNanos();
        }
    }
}
