package com.example.collab3d.client;

import com.example.collab3d.common.NetworkConstants;

/**
 * Per-remote-participant adaptive interpolation/jitter controller.
 *
 * <p>The controller measures delivery jitter by comparing the spacing between
 * authoritative server timestamps with the spacing between local arrival
 * timestamps. Absolute client/server clock offset cancels from this delta, so
 * the estimator does not depend on clock synchronization.</p>
 *
 * <p>Buffer policy is intentionally asymmetric: the interpolation delay grows
 * quickly when jitter appears, then decays slowly when delivery becomes stable.
 * This avoids oscillating the remote render timeline while still keeping the
 * normal buffer close to the low-latency FPS-oriented default.</p>
 */
final class RemoteInterpolationController {

    private long previousServerSampleTimeNanos;
    private long previousArrivalTimeNanos;

    private double jitterEstimateNanos;
    private double interpolationDelayNanos =
            NetworkConstants.REMOTE_INTERPOLATION_DEFAULT_DELAY_NANOS;

    synchronized void observe(
            long serverSampleTimeNanos,
            long arrivalTimeNanos) {

        if (serverSampleTimeNanos <= 0L || arrivalTimeNanos <= 0L) {
            return;
        }

        if (previousServerSampleTimeNanos > 0L
                && previousArrivalTimeNanos > 0L) {

            long serverDelta = serverSampleTimeNanos
                    - previousServerSampleTimeNanos;
            long arrivalDelta = arrivalTimeNanos
                    - previousArrivalTimeNanos;

            if (serverDelta > 0L && arrivalDelta > 0L) {
                long variation = absoluteDifference(arrivalDelta, serverDelta);
                variation = Math.min(
                        variation,
                        NetworkConstants.REMOTE_JITTER_SAMPLE_CAP_NANOS);

                jitterEstimateNanos +=
                        NetworkConstants.REMOTE_JITTER_EWMA_ALPHA
                        * (variation - jitterEstimateNanos);

                updateInterpolationDelay();
            }
        }

        previousServerSampleTimeNanos = serverSampleTimeNanos;
        previousArrivalTimeNanos = arrivalTimeNanos;
    }

    synchronized long currentDelayNanos() {
        return Math.round(interpolationDelayNanos);
    }

    synchronized long jitterEstimateNanos() {
        return Math.round(jitterEstimateNanos);
    }

    synchronized void reset() {
        previousServerSampleTimeNanos = 0L;
        previousArrivalTimeNanos = 0L;
        jitterEstimateNanos = 0.0;
        interpolationDelayNanos =
                NetworkConstants.REMOTE_INTERPOLATION_DEFAULT_DELAY_NANOS;
    }

    private void updateInterpolationDelay() {
        double target = NetworkConstants.REMOTE_INTERPOLATION_DEFAULT_DELAY_NANOS
                + NetworkConstants.REMOTE_JITTER_BUFFER_MULTIPLIER
                * jitterEstimateNanos;

        target = clamp(
                target,
                NetworkConstants.REMOTE_INTERPOLATION_MIN_DELAY_NANOS,
                NetworkConstants.REMOTE_INTERPOLATION_MAX_DELAY_NANOS);

        double alpha = target > interpolationDelayNanos
                ? NetworkConstants.REMOTE_INTERPOLATION_INCREASE_ALPHA
                : NetworkConstants.REMOTE_INTERPOLATION_DECREASE_ALPHA;

        interpolationDelayNanos += alpha * (target - interpolationDelayNanos);
        interpolationDelayNanos = clamp(
                interpolationDelayNanos,
                NetworkConstants.REMOTE_INTERPOLATION_MIN_DELAY_NANOS,
                NetworkConstants.REMOTE_INTERPOLATION_MAX_DELAY_NANOS);
    }

    private static long absoluteDifference(long a, long b) {
        long difference = a - b;
        if (difference == Long.MIN_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.abs(difference);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
