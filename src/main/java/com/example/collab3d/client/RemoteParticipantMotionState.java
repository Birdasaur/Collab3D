package com.example.collab3d.client;

import com.example.collab3d.common.NetworkConstants;
import com.example.collab3d.common.Pose3d;

/**
 * Per-participant remote motion state.
 *
 * <p>The preferred path is authoritative interpolation at a dynamically delayed
 * render time. The interpolation delay is controlled by measured snapshot
 * jitter and is bounded by FPS-oriented minimum/default/maximum policy values.
 * Short extrapolation is used only when the render clock runs past the newest
 * authoritative sample. If the prediction horizon becomes too large, the latest
 * authoritative sample is held instead of continuing to guess.</p>
 */
final class RemoteParticipantMotionState {

    enum RenderMode {
        INTERPOLATED,
        EXTRAPOLATED,
        HELD,
        DIRECT
    }

    private final RemotePoseHistory history = new RemotePoseHistory();
    private final RemotePosePredictor predictor = new RemotePosePredictor();
    private final PoseReconciler reconciler = new PoseReconciler();
    private final RemoteInterpolationController interpolationController =
            new RemoteInterpolationController();

    void offer(Pose3d pose, long arrivalTimeNanos) {
        if (pose == null) {
            return;
        }

        history.offer(pose);
        interpolationController.observe(
                pose.sampleTimeNanos(),
                arrivalTimeNanos);
    }

    RenderResult renderPose(
            long serverNowNanos,
            boolean bufferedInterpolationEnabled) {

        Pose3d latest = history.latest();
        if (latest == null) {
            return null;
        }

        long interpolationDelayNanos =
                interpolationController.currentDelayNanos();
        long jitterNanos = interpolationController.jitterEstimateNanos();

        if (!bufferedInterpolationEnabled) {
            reconciler.reset();
            return result(
                    latest,
                    RenderMode.DIRECT,
                    0L,
                    interpolationDelayNanos,
                    jitterNanos);
        }

        long renderTimeNanos = serverNowNanos - interpolationDelayNanos;

        RemotePoseHistory.Bracket bracket = history.bracket(renderTimeNanos);
        if (bracket == null || bracket.before() == null) {
            return null;
        }

        Pose3d targetPose;
        RenderMode mode;
        long extrapolationNanos = 0L;

        if (bracket.canInterpolate()) {
            Pose3d before = bracket.before();
            Pose3d after = bracket.after();
            long duration = after.sampleTimeNanos() - before.sampleTimeNanos();
            double amount = duration <= 0L
                    ? 1.0
                    : (renderTimeNanos - before.sampleTimeNanos())
                            / (double) duration;

            targetPose = Pose3d.interpolate(before, after, amount);
            targetPose = new Pose3d(
                    targetPose.x(),
                    targetPose.y(),
                    targetPose.z(),
                    targetPose.orientation(),
                    renderTimeNanos);
            mode = RenderMode.INTERPOLATED;

        } else if (renderTimeNanos > latest.sampleTimeNanos()) {
            extrapolationNanos = renderTimeNanos - latest.sampleTimeNanos();

            if (extrapolationNanos <= NetworkConstants.MAX_PREDICTION_NANOS) {
                targetPose = predictor.predict(
                        history.previous(),
                        latest,
                        renderTimeNanos);
                mode = RenderMode.EXTRAPOLATED;
            } else {
                targetPose = latest;
                mode = RenderMode.HELD;
            }

        } else {
            // Render time is at/before the oldest retained sample.
            targetPose = bracket.before();
            mode = RenderMode.HELD;
        }

        Pose3d renderPose = reconciler.reconcile(targetPose, serverNowNanos);
        return result(
                renderPose,
                mode,
                extrapolationNanos,
                interpolationDelayNanos,
                jitterNanos);
    }

    void reset() {
        history.clear();
        interpolationController.reset();
        reconciler.reset();
    }

    private RenderResult result(
            Pose3d pose,
            RenderMode mode,
            long extrapolationNanos,
            long interpolationDelayNanos,
            long jitterNanos) {

        return new RenderResult(
                pose,
                mode,
                extrapolationNanos,
                history.size(),
                history.spanNanos(),
                interpolationDelayNanos,
                jitterNanos);
    }

    record RenderResult(
            Pose3d pose,
            RenderMode mode,
            long extrapolationNanos,
            int historyDepth,
            long historySpanNanos,
            long interpolationDelayNanos,
            long jitterNanos) {
    }
}
