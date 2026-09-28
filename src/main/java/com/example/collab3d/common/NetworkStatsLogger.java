package com.example.collab3d.common;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

/**
 * Periodically converts {@link NetworkRuntimeStats} snapshots into readable
 * operational rate logs.
 *
 * <p>The logger runs on its own daemon thread so console I/O cannot perturb the
 * 120 Hz motion, networking, SimEthereal, or JavaFX timing paths being measured.</p>
 */
public final class NetworkStatsLogger implements AutoCloseable {

    public enum Role {
        CLIENT,
        SERVER
    }

    private final NetworkRuntimeStats stats;
    private final Logger logger;
    private final Role role;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<NetworkRuntimeStats.Window> latestWindow =
            new AtomicReference<>();
    private final AtomicReference<NetworkRuntimeStats.Snapshot> latestSnapshot =
            new AtomicReference<>();
    private final AtomicBoolean started = new AtomicBoolean();

    private NetworkRuntimeStats.Snapshot previousSnapshot;

    public NetworkStatsLogger(
            NetworkRuntimeStats stats,
            Logger logger,
            Role role) {
        this.stats = Objects.requireNonNull(stats, "stats");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.role = Objects.requireNonNull(role, "role");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(
                    runnable,
                    role == Role.CLIENT
                            ? "client-network-stats"
                            : "server-network-stats");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Starts periodic logging. Calling this method more than once is harmless. */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        previousSnapshot = stats.snapshot();
        latestSnapshot.set(previousSnapshot);
        scheduler.scheduleAtFixedRate(
                this::sampleAndLogSafely,
                NetworkConstants.RUNTIME_STATS_LOG_INTERVAL_SECONDS,
                NetworkConstants.RUNTIME_STATS_LOG_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    /** Most recently completed rate window, or null before the first interval. */
    public NetworkRuntimeStats.Window latestWindow() {
        return latestWindow.get();
    }

    /** Most recently logged cumulative snapshot. */
    public NetworkRuntimeStats.Snapshot latestSnapshot() {
        return latestSnapshot.get();
    }

    private void sampleAndLogSafely() {
        try {
            NetworkRuntimeStats.Snapshot current = stats.snapshot();
            NetworkRuntimeStats.Window window = current.deltaFrom(previousSnapshot);
            previousSnapshot = current;
            latestWindow.set(window);
            latestSnapshot.set(current);

            if (role == Role.CLIENT) {
                logClient(current, window);
            } else {
                logServer(current, window);
            }
        } catch (RuntimeException exception) {
            logger.warn("Unable to sample runtime network statistics.", exception);
        }
    }

    private void logClient(
            NetworkRuntimeStats.Snapshot totals,
            NetworkRuntimeStats.Window rates) {

        logger.info(
                "NET-STATS client window={}s motion={}Hz poseAttempt={}Hz poseSent={}Hz "
                        + "poseSkipped={}Hz simFrames={}Hz remoteObjects={}Hz render={}Hz "
                        + "remoteMotion[interp={}Hz extrap={}Hz hold={}Hz avgExtrap={}ms "
                        + "maxExtrap={}ms historyDepth={} historySpan={}ms "
                        + "buffer={}ms jitter={}ms] "
                        + "timeSync={}Hz rtt={}ms offset={}ms "
                        + "totals[sent={}, remoteObjects={}, renderFrames={}, syncSamples={}]",
                format(rates.windowSeconds()),
                format(rates.clientMotionHz()),
                format(rates.clientPoseAttemptHz()),
                format(rates.clientPoseSendHz()),
                format(rates.clientPoseSkipHz()),
                format(rates.clientSimEtherealFrameHz()),
                format(rates.clientRemoteObjectUpdateHz()),
                format(rates.clientRenderHz()),
                format(rates.clientRemoteInterpolationHz()),
                format(rates.clientRemoteExtrapolationHz()),
                format(rates.clientRemoteHoldHz()),
                formatMillis(totals.clientRemoteAverageExtrapolationNanos()),
                formatMillis(totals.clientRemoteMaxExtrapolationNanos()),
                totals.clientRemoteHistoryDepth(),
                formatMillis(totals.clientRemoteHistorySpanNanos()),
                formatMillis(totals.clientRemoteInterpolationDelayNanos()),
                formatMillis(totals.clientRemoteJitterNanos()),
                format(rates.clientTimeSyncHz()),
                formatMillis(totals.clientEstimatedRttNanos()),
                formatSignedMillis(totals.clientEstimatedClockOffsetNanos()),
                totals.clientPoseSends(),
                totals.clientRemoteObjectUpdates(),
                totals.clientRenderFrames(),
                totals.clientTimeSyncSamples());
    }

    private void logServer(
            NetworkRuntimeStats.Snapshot totals,
            NetworkRuntimeStats.Window rates) {

        logger.info(
                "NET-STATS server window={}s poseRx={}Hz accepted={}Hz rejected={}Hz "
                        + "stateFrames={}Hz timeSync={}Hz "
                        + "rewind[queries={}Hz hits={}Hz interp={}Hz misses={}Hz "
                        + "participants={} depth={} span={}ms] "
                        + "totals[rx={}, accepted={}, rejected={}, stateFrames={}, syncRequests={}, "
                        + "rewindQueries={}, rewindHits={}, rewindMisses={}]",
                format(rates.windowSeconds()),
                format(rates.serverPoseReceiveHz()),
                format(rates.serverPoseAcceptHz()),
                format(rates.serverPoseRejectHz()),
                format(rates.serverStateFrameHz()),
                format(rates.serverTimeSyncHz()),
                format(rates.serverRewindQueryHz()),
                format(rates.serverRewindHitHz()),
                format(rates.serverRewindInterpolationHz()),
                format(rates.serverRewindMissHz()),
                totals.serverRewindParticipantCount(),
                totals.serverRewindHistoryDepth(),
                formatMillis(totals.serverRewindHistorySpanNanos()),
                totals.serverPoseReceives(),
                totals.serverPoseAccepts(),
                totals.serverPoseRejects(),
                totals.serverStateFrames(),
                totals.serverTimeSyncRequests(),
                totals.serverRewindQueries(),
                totals.serverRewindHits(),
                totals.serverRewindMisses());
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static String formatMillis(double nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static String formatSignedMillis(long nanos) {
        return String.format(Locale.ROOT, "%+.3f", nanos / 1_000_000.0);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
