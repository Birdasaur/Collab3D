package com.example.collab3d.client;

import com.example.collab3d.common.NetworkRuntimeStats;
import com.example.collab3d.common.Pose3d;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe bridge between SimEthereal's networking thread and the JavaFX
 * render thread.
 */
final class RemotePoseStore {

    private final NetworkRuntimeStats runtimeStats;
    private final Map<Long, RemoteParticipantMotionState> states =
            new ConcurrentHashMap<>();
    private final AtomicLong updateCount = new AtomicLong();

    RemotePoseStore(NetworkRuntimeStats runtimeStats) {
        this.runtimeStats = Objects.requireNonNull(runtimeStats, "runtimeStats");
    }

    void offer(long objectId, Pose3d pose) {
        if (pose == null) {
            return;
        }

        long arrivalTimeNanos = System.nanoTime();

        states.computeIfAbsent(
                objectId,
                ignored -> new RemoteParticipantMotionState())
                .offer(pose, arrivalTimeNanos);

        updateCount.incrementAndGet();
    }

    void remove(long objectId) {
        RemoteParticipantMotionState removed = states.remove(objectId);
        if (removed != null) {
            removed.reset();
        }
    }

    Pose3d getRenderPose(
            long objectId,
            long serverNowNanos,
            boolean bufferedInterpolationEnabled) {

        RemoteParticipantMotionState state = states.get(objectId);
        if (state == null) {
            return null;
        }

        RemoteParticipantMotionState.RenderResult result = state.renderPose(
                serverNowNanos,
                bufferedInterpolationEnabled);

        if (result == null) {
            return null;
        }

        if (bufferedInterpolationEnabled) {
            switch (result.mode()) {
                case INTERPOLATED -> runtimeStats.recordClientRemoteInterpolatedRender();
                case EXTRAPOLATED -> runtimeStats.recordClientRemoteExtrapolatedRender(
                        result.extrapolationNanos());
                case HELD -> runtimeStats.recordClientRemoteHeldRender();
                case DIRECT -> {
                    // DIRECT is only expected when buffering is disabled.
                }
            }

            runtimeStats.recordClientRemoteBufferState(
                    result.historyDepth(),
                    result.historySpanNanos(),
                    result.interpolationDelayNanos(),
                    result.jitterNanos());
        }

        return result.pose();
    }

    long updateCount() {
        return updateCount.get();
    }
}
