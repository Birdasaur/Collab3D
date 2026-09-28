package com.example.collab3d.client;

import com.example.collab3d.common.NetworkConstants;
import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.NetworkRuntimeStats;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the high-frequency local motion and pose-publication loop.
 *
 * <p>This scheduler is intentionally independent of JavaFX's AnimationTimer.
 * A 60 Hz JavaFX pulse therefore no longer limits local motion sampling or
 * network publication. With the current constants the model advances at
 * 120 Hz and a freshly timestamped pose can be sent at 120 Hz, while JavaFX
 * simply renders whichever completed pose is newest at its next frame.</p>
 *
 * <p>The class also keeps motion rate and network rate conceptually separate.
 * They are both 120 Hz today, but CLIENT_MOTION_HZ can later be raised (for
 * example to 240 Hz) without increasing network traffic.</p>
 */
final class LocalPosePublisher implements AutoCloseable {

    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1L);

    private final CameraInputState inputState;
    private final CameraMotionModel motionModel;
    private final CollaborationNetworkClient networkClient;
    private final NetworkRuntimeStats runtimeStats;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean started = new AtomicBoolean();

    /*
     * Integer phase accumulator used to derive pose publication from motion ticks.
     * This avoids comparing nanosecond timestamps at an exact 8.333 ms boundary,
     * where normal scheduler jitter could accidentally reduce a nominal 120 Hz
     * publisher to 60 Hz for alternating samples.
     */
    private int posePublishPhase;

    LocalPosePublisher(
            CameraInputState inputState,
            CameraMotionModel motionModel,
            CollaborationNetworkClient networkClient,
            NetworkRuntimeStats runtimeStats) {

        this.inputState = Objects.requireNonNull(inputState, "inputState");
        this.motionModel = Objects.requireNonNull(motionModel, "motionModel");
        this.networkClient = Objects.requireNonNull(networkClient, "networkClient");
        this.runtimeStats = Objects.requireNonNull(runtimeStats, "runtimeStats");

        if (NetworkConstants.CLIENT_MOTION_HZ <= 0
                || NetworkConstants.CLIENT_POSE_HZ <= 0) {
            throw new IllegalArgumentException("Client rates must be positive.");
        }
        if (NetworkConstants.CLIENT_POSE_HZ > NetworkConstants.CLIENT_MOTION_HZ) {
            throw new IllegalArgumentException(
                    "CLIENT_POSE_HZ cannot exceed CLIENT_MOTION_HZ because "
                            + "each network pose should represent a completed motion sample.");
        }

        posePublishPhase = NetworkConstants.CLIENT_MOTION_HZ
                - NetworkConstants.CLIENT_POSE_HZ;
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "local-camera-motion");
            thread.setDaemon(true);
            return thread;
        });
    }

    void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        long motionPeriodNanos = NANOS_PER_SECOND / NetworkConstants.CLIENT_MOTION_HZ;
        scheduler.scheduleAtFixedRate(
                this::tickSafely,
                0L,
                motionPeriodNanos,
                TimeUnit.NANOSECONDS);
    }

    private void tickSafely() {
        try {
            long now = System.nanoTime();
            CameraInputState.Snapshot input = inputState.snapshotAndConsumeLook();
            Pose3d pose = motionModel.update(input, now);
            runtimeStats.recordClientMotionTick();

            posePublishPhase += NetworkConstants.CLIENT_POSE_HZ;
            if (posePublishPhase >= NetworkConstants.CLIENT_MOTION_HZ) {
                posePublishPhase -= NetworkConstants.CLIENT_MOTION_HZ;
                networkClient.sendPose(pose);
            }
        } catch (RuntimeException exception) {
            // ScheduledExecutorService suppresses future executions when a task
            // throws. Log explicitly so a stopped local-motion loop is visible.
            System.err.println("Fatal local camera motion/publish failure:");
            exception.printStackTrace(System.err);
            scheduler.shutdown();
        }
    }

    @Override
    public void close() {
        inputState.clearMovement();
        scheduler.shutdownNow();
    }
}
