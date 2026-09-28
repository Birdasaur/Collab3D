package com.example.collab3d.client;

import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.Quaterniond;

/**
 * Performs short constant-velocity extrapolation for remote participants when
 * the interpolation buffer temporarily runs past the newest authoritative
 * sample.
 *
 * <p>Prediction is deliberately bounded by the caller. This class should never
 * be used to project arbitrarily far into the future.</p>
 */
final class RemotePosePredictor {

    Pose3d predict(
            Pose3d previous,
            Pose3d latest,
            long targetServerTimeNanos) {

        if (latest == null) {
            return null;
        }
        if (previous == null) {
            return withSampleTime(latest, targetServerTimeNanos);
        }

        long sampleDeltaNanos = latest.sampleTimeNanos()
                - previous.sampleTimeNanos();
        long predictionDeltaNanos = targetServerTimeNanos
                - latest.sampleTimeNanos();

        if (sampleDeltaNanos <= 0L || predictionDeltaNanos <= 0L) {
            return withSampleTime(latest, targetServerTimeNanos);
        }

        double factor = predictionDeltaNanos / (double) sampleDeltaNanos;

        double x = latest.x() + (latest.x() - previous.x()) * factor;
        double y = latest.y() + (latest.y() - previous.y()) * factor;
        double z = latest.z() + (latest.z() - previous.z()) * factor;

        Quaterniond orientation = extrapolateOrientation(
                previous.orientation(),
                latest.orientation(),
                factor);

        return new Pose3d(
                x,
                y,
                z,
                orientation,
                targetServerTimeNanos).normalized();
    }

    private static Pose3d withSampleTime(Pose3d pose, long sampleTimeNanos) {
        return new Pose3d(
                pose.x(),
                pose.y(),
                pose.z(),
                pose.orientation(),
                sampleTimeNanos);
    }

    /**
     * Applies the most recent quaternion delta by a fractional/multiple amount.
     */
    private static Quaterniond extrapolateOrientation(
            Quaterniond previous,
            Quaterniond latest,
            double factor) {

        Quaterniond a = previous.normalized();
        Quaterniond b = latest.normalized();

        // Ensure shortest-arc continuity before deriving the delta quaternion.
        if (dot(a, b) < 0.0) {
            b = negate(b);
        }

        Quaterniond delta = multiply(inverse(a), b).normalized();
        Quaterniond scaledDelta = pow(delta, factor);
        return multiply(b, scaledDelta).normalized();
    }

    private static Quaterniond inverse(Quaterniond quaternion) {
        Quaterniond q = quaternion.normalized();
        return new Quaterniond(-q.x(), -q.y(), -q.z(), q.w());
    }

    private static Quaterniond multiply(Quaterniond a, Quaterniond b) {
        return new Quaterniond(
                a.w() * b.x() + a.x() * b.w() + a.y() * b.z() - a.z() * b.y(),
                a.w() * b.y() - a.x() * b.z() + a.y() * b.w() + a.z() * b.x(),
                a.w() * b.z() + a.x() * b.y() - a.y() * b.x() + a.z() * b.w(),
                a.w() * b.w() - a.x() * b.x() - a.y() * b.y() - a.z() * b.z());
    }

    private static Quaterniond pow(Quaterniond quaternion, double exponent) {
        Quaterniond q = quaternion.normalized();

        // q and -q represent the same rotation. Prefer non-negative w so the
        // extracted angle is the shortest rotation.
        if (q.w() < 0.0) {
            q = negate(q);
        }

        double clampedW = Math.max(-1.0, Math.min(1.0, q.w()));
        double halfAngle = Math.acos(clampedW);
        double sinHalfAngle = Math.sin(halfAngle);

        if (Math.abs(sinHalfAngle) < 1.0e-10) {
            return Quaterniond.identity();
        }

        double axisX = q.x() / sinHalfAngle;
        double axisY = q.y() / sinHalfAngle;
        double axisZ = q.z() / sinHalfAngle;

        double scaledHalfAngle = halfAngle * exponent;
        double scaledSin = Math.sin(scaledHalfAngle);

        return new Quaterniond(
                axisX * scaledSin,
                axisY * scaledSin,
                axisZ * scaledSin,
                Math.cos(scaledHalfAngle)).normalized();
    }

    private static double dot(Quaterniond a, Quaterniond b) {
        return a.x() * b.x()
                + a.y() * b.y()
                + a.z() * b.z()
                + a.w() * b.w();
    }

    private static Quaterniond negate(Quaterniond quaternion) {
        return new Quaterniond(
                -quaternion.x(),
                -quaternion.y(),
                -quaternion.z(),
                -quaternion.w());
    }
}
