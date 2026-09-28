package com.example.collab3d.common;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe runtime counters and timing gauges for the collaboration network.
 *
 * <p>The model is intentionally passive. It does not schedule work or influence
 * networking decisions; hot paths only increment LongAdders or replace simple
 * AtomicLong gauges.</p>
 */
public final class NetworkRuntimeStats {

    private final long startedAtNanos = System.nanoTime();

    private final LongAdder clientMotionTicks = new LongAdder();
    private final LongAdder clientPoseSendAttempts = new LongAdder();
    private final LongAdder clientPoseSends = new LongAdder();
    private final LongAdder clientPoseSendSkips = new LongAdder();

    private final LongAdder clientSimEtherealFrames = new LongAdder();
    private final LongAdder clientRemoteObjectUpdates = new LongAdder();
    private final LongAdder clientRenderFrames = new LongAdder();

    // Remote rendering policy counters.
    private final LongAdder clientRemoteInterpolatedRenders = new LongAdder();
    private final LongAdder clientRemoteExtrapolatedRenders = new LongAdder();
    private final LongAdder clientRemoteHeldRenders = new LongAdder();
    private final LongAdder clientRemoteExtrapolationNanos = new LongAdder();
    private final AtomicLong clientRemoteMaxExtrapolationNanos = new AtomicLong();
    private final AtomicLong clientRemoteHistoryDepth = new AtomicLong();
    private final AtomicLong clientRemoteHistorySpanNanos = new AtomicLong();
    private final AtomicLong clientRemoteInterpolationDelayNanos = new AtomicLong();
    private final AtomicLong clientRemoteJitterNanos = new AtomicLong();

    private final LongAdder clientTimeSyncSamples = new LongAdder();
    private final AtomicLong clientEstimatedRttNanos = new AtomicLong();
    private final AtomicLong clientEstimatedClockOffsetNanos = new AtomicLong();

    private final LongAdder serverPoseReceives = new LongAdder();
    private final LongAdder serverPoseAccepts = new LongAdder();
    private final LongAdder serverPoseRejects = new LongAdder();
    private final LongAdder serverStateFrames = new LongAdder();
    private final LongAdder serverTimeSyncRequests = new LongAdder();
    private final LongAdder serverRewindQueries = new LongAdder();
    private final LongAdder serverRewindHits = new LongAdder();
    private final LongAdder serverRewindInterpolations = new LongAdder();
    private final LongAdder serverRewindMisses = new LongAdder();
    private final AtomicLong serverRewindParticipantCount = new AtomicLong();
    private final AtomicLong serverRewindHistoryDepth = new AtomicLong();
    private final AtomicLong serverRewindHistorySpanNanos = new AtomicLong();

    public void recordClientMotionTick() {
        clientMotionTicks.increment();
    }

    public void recordClientPoseSendAttempt() {
        clientPoseSendAttempts.increment();
    }

    public void recordClientPoseSent() {
        clientPoseSends.increment();
    }

    public void recordClientPoseSendSkipped() {
        clientPoseSendSkips.increment();
    }

    public void recordClientSimEtherealFrame() {
        clientSimEtherealFrames.increment();
    }

    public void recordClientRemoteObjectUpdate() {
        clientRemoteObjectUpdates.increment();
    }

    public void recordClientRenderFrame() {
        clientRenderFrames.increment();
    }

    public void recordClientRemoteInterpolatedRender() {
        clientRemoteInterpolatedRenders.increment();
    }

    public void recordClientRemoteExtrapolatedRender(long extrapolationNanos) {
        long bounded = Math.max(0L, extrapolationNanos);
        clientRemoteExtrapolatedRenders.increment();
        clientRemoteExtrapolationNanos.add(bounded);
        clientRemoteMaxExtrapolationNanos.accumulateAndGet(bounded, Math::max);
    }

    public void recordClientRemoteHeldRender() {
        clientRemoteHeldRenders.increment();
    }

    public void recordClientRemoteBufferState(
            int depth,
            long spanNanos,
            long interpolationDelayNanos,
            long jitterNanos) {
        clientRemoteHistoryDepth.set(Math.max(0, depth));
        clientRemoteHistorySpanNanos.set(Math.max(0L, spanNanos));
        clientRemoteInterpolationDelayNanos.set(
                Math.max(0L, interpolationDelayNanos));
        clientRemoteJitterNanos.set(Math.max(0L, jitterNanos));
    }

    /** Records one accepted clock-sync response and the current filtered estimate. */
    public void recordClientTimeSyncSample(
            long estimatedOffsetNanos,
            long estimatedRttNanos) {
        clientTimeSyncSamples.increment();
        clientEstimatedClockOffsetNanos.set(estimatedOffsetNanos);
        clientEstimatedRttNanos.set(Math.max(0L, estimatedRttNanos));
    }

    public void recordServerPoseReceived() {
        serverPoseReceives.increment();
    }

    public void recordServerPoseAccepted() {
        serverPoseAccepts.increment();
    }

    public void recordServerPoseRejected() {
        serverPoseRejects.increment();
    }

    public void recordServerStateFrame() {
        serverStateFrames.increment();
    }

    public void recordServerTimeSyncRequest() {
        serverTimeSyncRequests.increment();
    }

    public void recordServerRewindQuery(boolean available, boolean interpolated) {
        serverRewindQueries.increment();
        if (available) {
            serverRewindHits.increment();
            if (interpolated) {
                serverRewindInterpolations.increment();
            }
        } else {
            serverRewindMisses.increment();
        }
    }

    public void recordServerRewindHistoryState(
            int participantCount,
            int totalHistoryDepth,
            long maximumHistorySpanNanos) {
        serverRewindParticipantCount.set(Math.max(0, participantCount));
        serverRewindHistoryDepth.set(Math.max(0, totalHistoryDepth));
        serverRewindHistorySpanNanos.set(Math.max(0L, maximumHistorySpanNanos));
    }

    public Snapshot snapshot() {
        return new Snapshot(
                System.nanoTime(),
                startedAtNanos,
                clientMotionTicks.sum(),
                clientPoseSendAttempts.sum(),
                clientPoseSends.sum(),
                clientPoseSendSkips.sum(),
                clientSimEtherealFrames.sum(),
                clientRemoteObjectUpdates.sum(),
                clientRenderFrames.sum(),
                clientRemoteInterpolatedRenders.sum(),
                clientRemoteExtrapolatedRenders.sum(),
                clientRemoteHeldRenders.sum(),
                clientRemoteExtrapolationNanos.sum(),
                clientRemoteMaxExtrapolationNanos.get(),
                clientRemoteHistoryDepth.get(),
                clientRemoteHistorySpanNanos.get(),
                clientRemoteInterpolationDelayNanos.get(),
                clientRemoteJitterNanos.get(),
                clientTimeSyncSamples.sum(),
                clientEstimatedRttNanos.get(),
                clientEstimatedClockOffsetNanos.get(),
                serverPoseReceives.sum(),
                serverPoseAccepts.sum(),
                serverPoseRejects.sum(),
                serverStateFrames.sum(),
                serverTimeSyncRequests.sum(),
                serverRewindQueries.sum(),
                serverRewindHits.sum(),
                serverRewindInterpolations.sum(),
                serverRewindMisses.sum(),
                serverRewindParticipantCount.get(),
                serverRewindHistoryDepth.get(),
                serverRewindHistorySpanNanos.get());
    }

    public record Snapshot(
            long capturedAtNanos,
            long startedAtNanos,
            long clientMotionTicks,
            long clientPoseSendAttempts,
            long clientPoseSends,
            long clientPoseSendSkips,
            long clientSimEtherealFrames,
            long clientRemoteObjectUpdates,
            long clientRenderFrames,
            long clientRemoteInterpolatedRenders,
            long clientRemoteExtrapolatedRenders,
            long clientRemoteHeldRenders,
            long clientRemoteExtrapolationNanos,
            long clientRemoteMaxExtrapolationNanos,
            long clientRemoteHistoryDepth,
            long clientRemoteHistorySpanNanos,
            long clientRemoteInterpolationDelayNanos,
            long clientRemoteJitterNanos,
            long clientTimeSyncSamples,
            long clientEstimatedRttNanos,
            long clientEstimatedClockOffsetNanos,
            long serverPoseReceives,
            long serverPoseAccepts,
            long serverPoseRejects,
            long serverStateFrames,
            long serverTimeSyncRequests,
            long serverRewindQueries,
            long serverRewindHits,
            long serverRewindInterpolations,
            long serverRewindMisses,
            long serverRewindParticipantCount,
            long serverRewindHistoryDepth,
            long serverRewindHistorySpanNanos) {

        public double uptimeSeconds() {
            return Math.max(0L, capturedAtNanos - startedAtNanos) / 1_000_000_000.0;
        }

        public double clientRemoteAverageExtrapolationNanos() {
            if (clientRemoteExtrapolatedRenders <= 0L) {
                return 0.0;
            }
            return clientRemoteExtrapolationNanos
                    / (double) clientRemoteExtrapolatedRenders;
        }

        public Window deltaFrom(Snapshot older) {
            if (older == null) {
                return Window.empty();
            }

            long elapsedNanos = Math.max(1L, capturedAtNanos - older.capturedAtNanos);
            double seconds = elapsedNanos / 1_000_000_000.0;

            return new Window(
                    seconds,
                    rate(clientMotionTicks - older.clientMotionTicks, seconds),
                    rate(clientPoseSendAttempts - older.clientPoseSendAttempts, seconds),
                    rate(clientPoseSends - older.clientPoseSends, seconds),
                    rate(clientPoseSendSkips - older.clientPoseSendSkips, seconds),
                    rate(clientSimEtherealFrames - older.clientSimEtherealFrames, seconds),
                    rate(clientRemoteObjectUpdates - older.clientRemoteObjectUpdates, seconds),
                    rate(clientRenderFrames - older.clientRenderFrames, seconds),
                    rate(clientRemoteInterpolatedRenders - older.clientRemoteInterpolatedRenders, seconds),
                    rate(clientRemoteExtrapolatedRenders - older.clientRemoteExtrapolatedRenders, seconds),
                    rate(clientRemoteHeldRenders - older.clientRemoteHeldRenders, seconds),
                    rate(clientTimeSyncSamples - older.clientTimeSyncSamples, seconds),
                    rate(serverPoseReceives - older.serverPoseReceives, seconds),
                    rate(serverPoseAccepts - older.serverPoseAccepts, seconds),
                    rate(serverPoseRejects - older.serverPoseRejects, seconds),
                    rate(serverStateFrames - older.serverStateFrames, seconds),
                    rate(serverTimeSyncRequests - older.serverTimeSyncRequests, seconds),
                    rate(serverRewindQueries - older.serverRewindQueries, seconds),
                    rate(serverRewindHits - older.serverRewindHits, seconds),
                    rate(serverRewindInterpolations - older.serverRewindInterpolations, seconds),
                    rate(serverRewindMisses - older.serverRewindMisses, seconds));
        }

        private static double rate(long count, double seconds) {
            return Math.max(0L, count) / seconds;
        }
    }

    public record Window(
            double windowSeconds,
            double clientMotionHz,
            double clientPoseAttemptHz,
            double clientPoseSendHz,
            double clientPoseSkipHz,
            double clientSimEtherealFrameHz,
            double clientRemoteObjectUpdateHz,
            double clientRenderHz,
            double clientRemoteInterpolationHz,
            double clientRemoteExtrapolationHz,
            double clientRemoteHoldHz,
            double clientTimeSyncHz,
            double serverPoseReceiveHz,
            double serverPoseAcceptHz,
            double serverPoseRejectHz,
            double serverStateFrameHz,
            double serverTimeSyncHz,
            double serverRewindQueryHz,
            double serverRewindHitHz,
            double serverRewindInterpolationHz,
            double serverRewindMissHz) {

        private static Window empty() {
            return new Window(
                    0.0,
                    0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0,
                    0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0);
        }
    }
}
