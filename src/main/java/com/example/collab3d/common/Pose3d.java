package com.example.collab3d.common;

/**
 *
 * Immutable, renderer-neutral 3D pose.
 *
 * <p>
 * The pose contains a position, orientation, and the monotonic timestamp
 * associated with the sample. The timestamp is deliberately carried with the
 * pose so higher-level networking and motion systems can preserve temporal
 * information without introducing JavaFX or SimEthereal types into the common
 * model.</p>
 *
 * <p>
 * The meaning of {@code sampleTimeNanos} depends on the clock domain in which
 * the pose was created. Client and server {@link System#nanoTime()} values must
 * not be directly compared unless clock synchronization has been
 * established.</p>
 *
 * @param x position on the X axis
 * @param y position on the Y axis
 * @param z position on the Z axis
 * @param orientation quaternion orientation
 * @param sampleTimeNanos monotonic sample timestamp associated with this pose
 */
public record Pose3d(
        double x,
        double y,
        double z,
        Quaterniond orientation,
        long sampleTimeNanos) {

    /**
     *
     * Returns a numerically safe version of this pose.
     *
     * <p>
     * Non-finite position values are replaced with zero and the quaternion is
     * normalized. A missing orientation becomes the identity quaternion.</p>
     *
     * @return normalized pose
     */
    public Pose3d normalized() {
        return new Pose3d(
                finiteOrZero(x),
                finiteOrZero(y),
                finiteOrZero(z),
                orientation == null
                        ? Quaterniond.identity()
                        : orientation.normalized(),
                sampleTimeNanos);
    }

    /**
     *
     * Clamps the position to the supplied world-space bounds while preserving
     * orientation and timestamp.
     *
     * @param limit absolute position limit applied to all three axes
     * @return normalized and clamped pose
     */
    public Pose3d clamped(double limit) {
        Pose3d pose = normalized();

        return new Pose3d(
                clamp(pose.x, -limit, limit),
                clamp(pose.y, -limit, limit),
                clamp(pose.z, -limit, limit),
                pose.orientation,
                pose.sampleTimeNanos);
    }

    /**
     *
     * Interpolates between two poses.
     *
     * <p>
     * Position uses linear interpolation while orientation uses quaternion
     * spherical interpolation. The resulting timestamp is inherited from the
     * end pose because this method is primarily used to generate a rendered
     * state converging toward a newer target sample.</p>
     *
     * @param start starting pose
     * @param end ending pose
     * @param amount interpolation factor in the range {@code [0, 1]}
     * @return interpolated pose
     */
    public static Pose3d interpolate(
            Pose3d start,
            Pose3d end,
            double amount) {

        double t = Math.max(0.0, Math.min(1.0, amount));

        return new Pose3d(
                start.x + (end.x - start.x) * t,
                start.y + (end.y - start.y) * t,
                start.z + (end.z - start.z) * t,
                Quaterniond.slerp(
                        start.orientation,
                        end.orientation,
                        t),
                end.sampleTimeNanos);
    }

    /**
     *
     * Replaces NaN or infinite values with zero.
     */
    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    /**
     *
     * Clamps a scalar value to the supplied range.
     */
    private static double clamp(
            double value,
            double min,
            double max) {

        return Math.max(min, Math.min(max, value));
    }
}
