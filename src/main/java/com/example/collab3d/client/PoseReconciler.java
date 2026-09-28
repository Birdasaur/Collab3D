package com.example.collab3d.client;

import com.example.collab3d.common.NetworkConstants;
import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.Quaterniond;

/**
 * Smooths small corrections between consecutive rendered remote poses while
 * snapping immediately when the discrepancy is too large to hide safely.
 */
final class PoseReconciler {

    private Pose3d renderedPose;
    private long lastRenderTimeNanos;

    Pose3d reconcile(Pose3d targetPose, long renderClockNanos) {
        if (targetPose == null) {
            return renderedPose;
        }

        if (renderedPose == null || lastRenderTimeNanos == 0L) {
            renderedPose = targetPose;
            lastRenderTimeNanos = renderClockNanos;
            return renderedPose;
        }

        long elapsedNanos = Math.max(0L, renderClockNanos - lastRenderTimeNanos);
        lastRenderTimeNanos = renderClockNanos;

        if (shouldSnap(renderedPose, targetPose)) {
            renderedPose = targetPose;
            return renderedPose;
        }

        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double halfLife = Math.max(
                1.0e-6,
                NetworkConstants.RECONCILIATION_HALF_LIFE_SECONDS);
        double alpha = 1.0 - Math.pow(0.5, elapsedSeconds / halfLife);

        renderedPose = Pose3d.interpolate(
                renderedPose,
                targetPose,
                alpha);
        return renderedPose;
    }

    void reset() {
        renderedPose = null;
        lastRenderTimeNanos = 0L;
    }

    private static boolean shouldSnap(Pose3d current, Pose3d target) {
        double dx = target.x() - current.x();
        double dy = target.y() - current.y();
        double dz = target.z() - current.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance >= NetworkConstants.RECONCILIATION_SNAP_DISTANCE) {
            return true;
        }

        return quaternionAngleDegrees(
                current.orientation(),
                target.orientation())
                >= NetworkConstants.RECONCILIATION_SNAP_ANGLE_DEGREES;
    }

    private static double quaternionAngleDegrees(
            Quaterniond first,
            Quaterniond second) {

        Quaterniond a = first.normalized();
        Quaterniond b = second.normalized();
        double dot = Math.abs(
                a.x() * b.x()
                        + a.y() * b.y()
                        + a.z() * b.z()
                        + a.w() * b.w());
        dot = Math.max(-1.0, Math.min(1.0, dot));
        return Math.toDegrees(2.0 * Math.acos(dot));
    }
}
