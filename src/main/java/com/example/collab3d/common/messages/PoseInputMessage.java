package com.example.collab3d.common.messages;

import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.Quaterniond;
import com.jme3.network.AbstractMessage;
import com.jme3.network.serializing.Serializable;

/**
 * Client-to-server pose update.
 *
 * <p>This message preserves both the pose and the original client-side sample
 * time. The sample timestamp is taken when the local camera pose is generated,
 * not when the server receives the message.</p>
 *
 * <p>The client sample timestamp belongs to the client's monotonic clock
 * domain. It must not be directly compared with server-side
 * {@link System#nanoTime()} values unless clock synchronization has been
 * established.</p>
 *
 * <p>The sequence number provides authoritative ordering for the unreliable
 * UDP pose stream. The server can reject duplicate or out-of-order updates
 * regardless of timestamp differences.</p>
 */
@Serializable
public final class PoseInputMessage extends AbstractMessage {

    private double x;
    private double y;
    private double z;

    private double qx;
    private double qy;
    private double qz;
    private double qw;

    private long clientSequence;
    private long clientSampleTimeNanos;

    /**
     * Required by SpiderMonkey serialization.
     */
    public PoseInputMessage() {
    }

    /**
     * Creates a pose update from the client's locally sampled pose.
     *
     * @param pose pose captured by the client's local camera motion model
     * @param clientSequence monotonically increasing client sequence number
     */
    public PoseInputMessage(
            Pose3d pose,
            long clientSequence) {

        Pose3d normalizedPose = pose.normalized();
        Quaterniond orientation = normalizedPose.orientation();

        x = normalizedPose.x();
        y = normalizedPose.y();
        z = normalizedPose.z();

        qx = orientation.x();
        qy = orientation.y();
        qz = orientation.z();
        qw = orientation.w();

        this.clientSequence = clientSequence;
        this.clientSampleTimeNanos = normalizedPose.sampleTimeNanos();
    }

    /**
     * Reconstructs the client pose while preserving its original client-side
     * sample timestamp.
     *
     * <p>The server separately records its own receive timestamp in
     * {@code TimedPose3d}. Keeping the two timestamps separate preserves their
     * distinct clock domains.</p>
     *
     * @return normalized client pose
     */
    public Pose3d toPose() {
        return new Pose3d(
                x,
                y,
                z,
                new Quaterniond(
                        qx,
                        qy,
                        qz,
                        qw),
                clientSampleTimeNanos)
                .normalized();
    }

    public long getClientSequence() {
        return clientSequence;
    }

    public long getClientSampleTimeNanos() {
        return clientSampleTimeNanos;
    }
}