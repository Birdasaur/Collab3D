package com.example.collab3d.server;

import com.example.collab3d.common.NetworkConstants;
import com.example.collab3d.common.NetworkRuntimeStats;
import com.example.collab3d.common.Pose3d;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side authoritative history and rewind-query service.
 *
 * <p>The service is deliberately independent from SimEthereal's replication
 * internals. The server records the same authoritative pose it publishes for
 * each state frame, but retains that state locally for later historical
 * queries such as lag-compensated selection, pointer, or hit evaluation.</p>
 */
public final class ServerRewindService {

    private final Map<Long, ServerPoseHistory> histories =
            new ConcurrentHashMap<>();
    private final NetworkRuntimeStats runtimeStats;

    public ServerRewindService(NetworkRuntimeStats runtimeStats) {
        this.runtimeStats = Objects.requireNonNull(runtimeStats, "runtimeStats");
    }

    public void registerParticipant(
            long objectId,
            long serverTimeNanos,
            Pose3d initialPose) {

        ServerPoseHistory history = new ServerPoseHistory(
                objectId,
                NetworkConstants.SERVER_REWIND_HISTORY_DURATION_NANOS);
        history.add(serverTimeNanos, initialPose);
        histories.put(objectId, history);
        updateHistoryGauges();
    }

    public void removeParticipant(long objectId) {
        histories.remove(objectId);
        updateHistoryGauges();
    }

    public void recordAuthoritativePose(
            long objectId,
            long serverTimeNanos,
            Pose3d pose) {

        ServerPoseHistory history = histories.get(objectId);
        if (history == null) {
            return;
        }

        history.add(serverTimeNanos, pose);
        updateHistoryGauges();
    }

    /**
     * Reconstructs one participant's authoritative pose at the requested
     * server time.
     *
     * <p>No extrapolation is performed. Requests outside retained history are
     * explicitly reported as TOO_OLD or TOO_NEW.</p>
     */
    public RewindPoseResult poseAt(
            long objectId,
            long requestedServerTimeNanos) {

        ServerPoseHistory history = histories.get(objectId);
        if (history == null) {
            RewindPoseResult result = RewindPoseResult.unavailable(
                    RewindPoseResult.Status.PARTICIPANT_NOT_FOUND,
                    objectId,
                    requestedServerTimeNanos,
                    0L,
                    0L);
            runtimeStats.recordServerRewindQuery(false, false);
            return result;
        }

        RewindPoseResult result = history.poseAt(requestedServerTimeNanos);
        runtimeStats.recordServerRewindQuery(
                result.available(),
                result.status() == RewindPoseResult.Status.INTERPOLATED);
        return result;
    }

    public int participantCount() {
        return histories.size();
    }

    private void updateHistoryGauges() {
        int totalDepth = 0;
        long maximumSpan = 0L;

        for (ServerPoseHistory history : histories.values()) {
            totalDepth += history.depth();
            maximumSpan = Math.max(maximumSpan, history.spanNanos());
        }

        runtimeStats.recordServerRewindHistoryState(
                histories.size(),
                totalDepth,
                maximumSpan);
    }
}
