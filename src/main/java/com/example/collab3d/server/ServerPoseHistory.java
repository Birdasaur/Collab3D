package com.example.collab3d.server;

import com.example.collab3d.common.Pose3d;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Thread-safe bounded history of one participant's authoritative server poses.
 *
 * <p>Samples are keyed exclusively by the server's monotonic clock. Client
 * timestamps are intentionally not used here because each client has a
 * different System.nanoTime() clock domain.</p>
 */
final class ServerPoseHistory {

    private final long objectId;
    private final long retentionNanos;
    private final Deque<Sample> samples = new ArrayDeque<>();

    ServerPoseHistory(long objectId, long retentionNanos) {
        if (retentionNanos <= 0L) {
            throw new IllegalArgumentException("retentionNanos must be positive");
        }
        this.objectId = objectId;
        this.retentionNanos = retentionNanos;
    }

    synchronized void add(long serverTimeNanos, Pose3d pose) {
        if (pose == null) {
            return;
        }

        Sample newest = samples.peekLast();
        if (newest != null && serverTimeNanos <= newest.serverTimeNanos()) {
            return;
        }

        samples.addLast(new Sample(
                serverTimeNanos,
                withServerTimestamp(pose, serverTimeNanos)));

        long cutoff = serverTimeNanos - retentionNanos;
        while (samples.size() > 1) {
            Sample oldest = samples.peekFirst();
            if (oldest == null || oldest.serverTimeNanos() >= cutoff) {
                break;
            }
            samples.removeFirst();
        }
    }

    synchronized RewindPoseResult poseAt(long requestedServerTimeNanos) {
        Sample oldest = samples.peekFirst();
        Sample newest = samples.peekLast();

        if (oldest == null || newest == null) {
            return RewindPoseResult.unavailable(
                    RewindPoseResult.Status.NO_HISTORY,
                    objectId,
                    requestedServerTimeNanos,
                    0L,
                    0L);
        }

        if (requestedServerTimeNanos < oldest.serverTimeNanos()) {
            return RewindPoseResult.unavailable(
                    RewindPoseResult.Status.TOO_OLD,
                    objectId,
                    requestedServerTimeNanos,
                    oldest.serverTimeNanos(),
                    newest.serverTimeNanos());
        }

        if (requestedServerTimeNanos > newest.serverTimeNanos()) {
            return RewindPoseResult.unavailable(
                    RewindPoseResult.Status.TOO_NEW,
                    objectId,
                    requestedServerTimeNanos,
                    oldest.serverTimeNanos(),
                    newest.serverTimeNanos());
        }

        if (requestedServerTimeNanos == oldest.serverTimeNanos()) {
            return exact(requestedServerTimeNanos, oldest, newest);
        }
        if (requestedServerTimeNanos == newest.serverTimeNanos()) {
            return exact(requestedServerTimeNanos, newest, newest);
        }

        Iterator<Sample> iterator = samples.iterator();
        Sample before = iterator.next();

        while (iterator.hasNext()) {
            Sample after = iterator.next();

            if (requestedServerTimeNanos == after.serverTimeNanos()) {
                return exact(requestedServerTimeNanos, after, newest);
            }

            if (requestedServerTimeNanos < after.serverTimeNanos()) {
                long interval = after.serverTimeNanos() - before.serverTimeNanos();
                if (interval <= 0L) {
                    return RewindPoseResult.unavailable(
                            RewindPoseResult.Status.NO_HISTORY,
                            objectId,
                            requestedServerTimeNanos,
                            oldest.serverTimeNanos(),
                            newest.serverTimeNanos());
                }

                double amount = (requestedServerTimeNanos - before.serverTimeNanos())
                        / (double) interval;

                Pose3d interpolated = Pose3d.interpolate(
                        before.pose(),
                        after.pose(),
                        amount);

                interpolated = withServerTimestamp(
                        interpolated,
                        requestedServerTimeNanos);

                return new RewindPoseResult(
                        RewindPoseResult.Status.INTERPOLATED,
                        objectId,
                        requestedServerTimeNanos,
                        interpolated,
                        oldest.serverTimeNanos(),
                        newest.serverTimeNanos());
            }

            before = after;
        }

        return RewindPoseResult.unavailable(
                RewindPoseResult.Status.NO_HISTORY,
                objectId,
                requestedServerTimeNanos,
                oldest.serverTimeNanos(),
                newest.serverTimeNanos());
    }

    synchronized int depth() {
        return samples.size();
    }

    synchronized long spanNanos() {
        Sample oldest = samples.peekFirst();
        Sample newest = samples.peekLast();
        if (oldest == null || newest == null) {
            return 0L;
        }
        return Math.max(0L, newest.serverTimeNanos() - oldest.serverTimeNanos());
    }

    private RewindPoseResult exact(
            long requestedServerTimeNanos,
            Sample sample,
            Sample newest) {

        Sample oldest = samples.peekFirst();
        return new RewindPoseResult(
                RewindPoseResult.Status.EXACT,
                objectId,
                requestedServerTimeNanos,
                sample.pose(),
                oldest == null ? sample.serverTimeNanos() : oldest.serverTimeNanos(),
                newest.serverTimeNanos());
    }

    private static Pose3d withServerTimestamp(Pose3d pose, long serverTimeNanos) {
        Pose3d normalized = pose.normalized();
        return new Pose3d(
                normalized.x(),
                normalized.y(),
                normalized.z(),
                normalized.orientation(),
                serverTimeNanos);
    }

    private record Sample(long serverTimeNanos, Pose3d pose) {
    }
}
