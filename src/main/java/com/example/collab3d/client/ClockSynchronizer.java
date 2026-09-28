package com.example.collab3d.client;

import com.example.collab3d.common.NetworkConstants;
import com.example.collab3d.common.messages.TimeSyncRequestMessage;
import com.example.collab3d.common.messages.TimeSyncResponseMessage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Estimates the relationship between the local client's monotonic clock and
 * the server's monotonic clock.
 *
 * <p>This class implements a lightweight NTP-style four-timestamp exchange:</p>
 *
 * <pre>
 * client                         server
 *
 * t0  ---- TimeSyncRequest ---->  t1
 *                                  |
 *                                  t2
 * t3  <--- TimeSyncResponse -----
 * </pre>
 *
 * <p>For each completed exchange:</p>
 *
 * <pre>
 * roundTrip = (t3 - t0) - (t2 - t1)
 * offset    = ((t1 - t0) + (t2 - t3)) / 2
 * </pre>
 *
 * <p>{@code offset} is defined as {@code serverTime - clientTime}. Therefore a
 * client timestamp can be mapped to the estimated server timeline by adding
 * the current offset.</p>
 *
 * <p>Network queueing makes high-delay samples less trustworthy. The active
 * offset estimate is therefore derived from a rolling sample window and uses
 * the median offset of the lowest-RTT subset. This is intentionally more robust
 * than averaging every response, while remaining inexpensive enough for a
 * once-per-second synchronization exchange.</p>
 *
 * <p>This class never uses wall-clock time. {@link System#nanoTime()} is the
 * correct clock for measuring elapsed time and maintaining a stable monotonic
 * collaboration timeline.</p>
 */
final class ClockSynchronizer {

    private final AtomicLong nextSequence = new AtomicLong();
    private final Map<Long, Long> pendingRequests = new HashMap<>();
    private final Deque<Sample> samples = new ArrayDeque<>();

    private boolean synchronizedClock;
    private long estimatedOffsetNanos;
    private long estimatedRttNanos;

    /**
     * Creates a new synchronization request and remembers its local send time.
     */
    synchronized TimeSyncRequestMessage createRequest() {
        long sequence = nextSequence.incrementAndGet();
        long clientSendTimeNanos = System.nanoTime();

        pendingRequests.put(sequence, clientSendTimeNanos);

        /*
         * A reliable synchronization request should normally complete quickly.
         * Bound the pending map anyway so a broken connection cannot retain an
         * unbounded number of unanswered requests.
         */
        while (pendingRequests.size() > NetworkConstants.TIME_SYNC_MAX_PENDING) {
            Long oldestSequence = pendingRequests.keySet().stream()
                    .min(Long::compareTo)
                    .orElse(null);
            if (oldestSequence == null) {
                break;
            }
            pendingRequests.remove(oldestSequence);
        }

        return new TimeSyncRequestMessage(sequence, clientSendTimeNanos);
    }

    /**
     * Processes a server response using the local receive timestamp captured at
     * the message-handler boundary.
     *
     * @return updated clock estimate, or {@code null} when the response is stale
     * or internally inconsistent
     */
    synchronized Estimate acceptResponse(
            TimeSyncResponseMessage response,
            long clientReceiveTimeNanos) {

        if (response == null) {
            return null;
        }

        Long expectedClientSendTime = pendingRequests.remove(response.getSequence());
        if (expectedClientSendTime == null
                || expectedClientSendTime.longValue() != response.getClientSendTimeNanos()) {
            return null;
        }

        long t0 = response.getClientSendTimeNanos();
        long t1 = response.getServerReceiveTimeNanos();
        long t2 = response.getServerSendTimeNanos();
        long t3 = clientReceiveTimeNanos;

        if (t3 < t0 || t2 < t1) {
            return null;
        }

        long serverProcessingNanos = t2 - t1;
        long totalClientElapsedNanos = t3 - t0;
        long roundTripNanos = totalClientElapsedNanos - serverProcessingNanos;

        if (roundTripNanos < 0L) {
            return null;
        }

        long offsetNanos = averageWithoutOverflow(
                t1 - t0,
                t2 - t3);

        samples.addLast(new Sample(offsetNanos, roundTripNanos));
        while (samples.size() > NetworkConstants.TIME_SYNC_SAMPLE_WINDOW) {
            samples.removeFirst();
        }

        recomputeEstimate();

        return new Estimate(
                estimatedOffsetNanos,
                estimatedRttNanos,
                samples.size());
    }

    /**
     * Converts a timestamp from the local client clock domain to the estimated
     * server clock domain.
     */
    synchronized long toServerTime(long clientTimeNanos) {
        return clientTimeNanos + estimatedOffsetNanos;
    }

    /**
     * Converts a timestamp from the server clock domain to the estimated local
     * client clock domain.
     */
    synchronized long toClientTime(long serverTimeNanos) {
        return serverTimeNanos - estimatedOffsetNanos;
    }

    synchronized boolean hasEstimate() {
        return synchronizedClock;
    }

    synchronized long estimatedOffsetNanos() {
        return estimatedOffsetNanos;
    }

    synchronized long estimatedRttNanos() {
        return estimatedRttNanos;
    }

    /** Clears all samples, for example after a disconnect/reconnect boundary. */
    synchronized void reset() {
        pendingRequests.clear();
        samples.clear();
        synchronizedClock = false;
        estimatedOffsetNanos = 0L;
        estimatedRttNanos = 0L;
    }

    private void recomputeEstimate() {
        if (samples.isEmpty()) {
            synchronizedClock = false;
            estimatedOffsetNanos = 0L;
            estimatedRttNanos = 0L;
            return;
        }

        List<Sample> byDelay = new ArrayList<>(samples);
        byDelay.sort(Comparator.comparingLong(Sample::roundTripNanos));

        int selectedCount = Math.min(
                NetworkConstants.TIME_SYNC_BEST_SAMPLE_COUNT,
                byDelay.size());

        List<Long> selectedOffsets = new ArrayList<>(selectedCount);
        for (int index = 0; index < selectedCount; index++) {
            selectedOffsets.add(byDelay.get(index).offsetNanos());
        }
        selectedOffsets.sort(Long::compareTo);

        estimatedOffsetNanos = median(selectedOffsets);
        estimatedRttNanos = byDelay.get(0).roundTripNanos();
        synchronizedClock = true;
    }

    private static long median(List<Long> sortedValues) {
        int size = sortedValues.size();
        int middle = size / 2;

        if ((size & 1) == 1) {
            return sortedValues.get(middle);
        }

        return averageWithoutOverflow(
                sortedValues.get(middle - 1),
                sortedValues.get(middle));
    }

    /**
     * Computes (a + b) / 2 while avoiding overflow for large nanoTime values.
     */
    private static long averageWithoutOverflow(long a, long b) {
        return (a / 2L) + (b / 2L) + ((a % 2L + b % 2L) / 2L);
    }

    private record Sample(
            long offsetNanos,
            long roundTripNanos) {
    }

    /** Immutable externally useful view of the current clock estimate. */
    record Estimate(
            long offsetNanos,
            long roundTripNanos,
            int retainedSampleCount) {
    }
}
