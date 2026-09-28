package com.example.collab3d.client;

import com.example.collab3d.common.NetworkConstants;
import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.Quaterniond;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plain-Java local camera simulation that is independent of the JavaFX scene
 * graph.
 *
 * <p>This class is deliberately designed around single-writer ownership. The
 * {@link LocalPosePublisher} motion thread is the only thread that calls
 * {@link #update(CameraInputState.Snapshot, long)}. JavaFX only calls
 * {@link #latestPose()} to render the newest immutable snapshot. This avoids
 * locks in the high-frequency path and, more importantly, prevents background
 * access to JavaFX nodes.</p>
 *
 * <p>The model preserves the original prototype controls: +Z is camera forward,
 * +X is camera right, JavaFX -Y is world up, W/S move forward/backward through
 * the current pitch, A/D strafe, and Q/E move vertically in world space.</p>
 */
final class CameraMotionModel {

    private static final double MOUSE_SENSITIVITY_DEGREES_PER_PIXEL = 0.20;
    private static final double NORMAL_SPEED = 8.0;
    private static final double FAST_SPEED = 28.0;
    private static final double MAX_PITCH_DEGREES = 89.0;
    private static final double MAX_STEP_SECONDS = 0.100;
    private static final double VECTOR_EPSILON = 1.0e-12;

    private final AtomicReference<Pose3d> latestPose;

    // The fields below are owned exclusively by the local motion thread.
    private double x;
    private double y;
    private double z;
    private double yawDegrees;
    private double pitchDegrees;
    private long previousUpdateNanos;

    CameraMotionModel(Pose3d initialPose) {
        Pose3d initial = Objects.requireNonNull(initialPose, "initialPose").normalized();
        x = initial.x();
        y = initial.y();
        z = initial.z();

        // Recover the yaw/pitch representation used by this first-person camera
        // from the supplied quaternion so the model can also be initialized from
        // a non-identity pose later without changing its public API.
        Vector3 forward = rotate(initial.orientation(), new Vector3(0.0, 0.0, 1.0));
        yawDegrees = Math.toDegrees(Math.atan2(forward.x(), forward.z()));
        pitchDegrees = Math.toDegrees(Math.asin(clamp(-forward.y(), -1.0, 1.0)));

        latestPose = new AtomicReference<>(new Pose3d(
                x,
                y,
                z,
                orientation(),
                initial.sampleTimeNanos()).normalized());
    }

    /**
     * Advances local camera state using input accumulated since the prior motion
     * tick and publishes a new immutable pose snapshot.
     *
     * @param input controls and mouse delta for this step
     * @param nowNanos client-local monotonic sample time
     * @return the newly published local pose
     */
    Pose3d update(CameraInputState.Snapshot input, long nowNanos) {
        Objects.requireNonNull(input, "input");

        yawDegrees = wrapDegrees(
                yawDegrees + input.lookDeltaX() * MOUSE_SENSITIVITY_DEGREES_PER_PIXEL);
        pitchDegrees = clamp(
                pitchDegrees - input.lookDeltaY() * MOUSE_SENSITIVITY_DEGREES_PER_PIXEL,
                -MAX_PITCH_DEGREES,
                MAX_PITCH_DEGREES);

        double seconds = 0.0;
        if (previousUpdateNanos != 0L && nowNanos > previousUpdateNanos) {
            seconds = Math.min(
                    MAX_STEP_SECONDS,
                    (nowNanos - previousUpdateNanos) / 1_000_000_000.0);
        }
        previousUpdateNanos = nowNanos;

        Quaterniond orientation = orientation();
        if (seconds > 0.0) {
            integrateTranslation(input, orientation, seconds);
        }

        Pose3d pose = new Pose3d(
                x,
                y,
                z,
                orientation,
                nowNanos).clamped(NetworkConstants.WORLD_LIMIT);

        // Keep local rendering and transmitted state identical when the world
        // safety bound is reached.
        x = pose.x();
        y = pose.y();
        z = pose.z();
        latestPose.set(pose);
        return pose;
    }

    /** Returns the most recently completed motion-step snapshot. */
    Pose3d latestPose() {
        return latestPose.get();
    }

    private void integrateTranslation(
            CameraInputState.Snapshot input,
            Quaterniond orientation,
            double seconds) {

        Vector3 forward = rotate(orientation, new Vector3(0.0, 0.0, 1.0));
        Vector3 right = rotate(orientation, new Vector3(1.0, 0.0, 0.0));
        Vector3 worldUp = new Vector3(0.0, -1.0, 0.0);
        Vector3 direction = Vector3.ZERO;

        if (input.forward()) {
            direction = direction.add(forward);
        }
        if (input.backward()) {
            direction = direction.subtract(forward);
        }
        if (input.right()) {
            direction = direction.add(right);
        }
        if (input.left()) {
            direction = direction.subtract(right);
        }
        if (input.up()) {
            direction = direction.add(worldUp);
        }
        if (input.down()) {
            direction = direction.subtract(worldUp);
        }

        double length = direction.length();
        if (length <= VECTOR_EPSILON) {
            return;
        }

        double speed = input.fast() ? FAST_SPEED : NORMAL_SPEED;
        Vector3 movement = direction.scale(speed * seconds / length);
        x += movement.x();
        y += movement.y();
        z += movement.z();
    }

    /** Builds the same yaw-then-pitch orientation used by the old FX transforms. */
    private Quaterniond orientation() {
        Quaterniond yaw = axisAngle(0.0, 1.0, 0.0, Math.toRadians(yawDegrees));
        Quaterniond pitch = axisAngle(1.0, 0.0, 0.0, Math.toRadians(pitchDegrees));
        return multiply(yaw, pitch).normalized();
    }

    private static Quaterniond axisAngle(
            double axisX,
            double axisY,
            double axisZ,
            double angleRadians) {

        double half = angleRadians * 0.5;
        double sine = Math.sin(half);
        return new Quaterniond(
                axisX * sine,
                axisY * sine,
                axisZ * sine,
                Math.cos(half)).normalized();
    }

    private static Quaterniond multiply(Quaterniond a, Quaterniond b) {
        return new Quaterniond(
                a.w() * b.x() + a.x() * b.w() + a.y() * b.z() - a.z() * b.y(),
                a.w() * b.y() - a.x() * b.z() + a.y() * b.w() + a.z() * b.x(),
                a.w() * b.z() + a.x() * b.y() - a.y() * b.x() + a.z() * b.w(),
                a.w() * b.w() - a.x() * b.x() - a.y() * b.y() - a.z() * b.z());
    }

    /** Rotates a vector by a normalized quaternion without any JavaFX dependency. */
    private static Vector3 rotate(Quaterniond input, Vector3 vector) {
        Quaterniond q = input.normalized();
        Vector3 qv = new Vector3(q.x(), q.y(), q.z());
        Vector3 t = qv.cross(vector).scale(2.0);
        return vector
                .add(t.scale(q.w()))
                .add(qv.cross(t));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped > 180.0) {
            wrapped -= 360.0;
        } else if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }

    private record Vector3(double x, double y, double z) {
        private static final Vector3 ZERO = new Vector3(0.0, 0.0, 0.0);

        Vector3 add(Vector3 other) {
            return new Vector3(x + other.x, y + other.y, z + other.z);
        }

        Vector3 subtract(Vector3 other) {
            return new Vector3(x - other.x, y - other.y, z - other.z);
        }

        Vector3 scale(double factor) {
            return new Vector3(x * factor, y * factor, z * factor);
        }

        Vector3 cross(Vector3 other) {
            return new Vector3(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x);
        }

        double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }
}
