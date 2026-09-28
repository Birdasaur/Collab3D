package com.example.collab3d.server.collision;

import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.Quaterniond;
import com.example.collab3d.common.geometry.CollisionShape3d;
import com.example.collab3d.common.geometry.CompoundShape3d;
import com.example.collab3d.common.geometry.OrientedBox3d;
import com.example.collab3d.common.geometry.Vector3d;
import java.util.List;

/**
 * Collision proxy for the rendered participant arrow.
 *
 * <p>The proxy intentionally follows the visible pieces instead of enclosing
 * the whole elongated arrow in one loose volume: camera body, thin direction
 * shaft, and tetrahedral arrow-head bounds.</p>
 */
public final class ParticipantCollisionShapeProvider
        implements CollisionShapeProvider {

    @Override
    public CollisionShape3d createShape(long objectId, Pose3d pose) {
        Quaterniond q = pose.orientation().normalized();
        Vector3d origin = new Vector3d(pose.x(), pose.y(), pose.z());

        OrientedBox3d body = box(
                origin,
                q,
                new Vector3d(0.0, 0.0, 0.0),
                new Vector3d(0.28, 0.28, 0.28));

        OrientedBox3d shaft = box(
                origin,
                q,
                new Vector3d(0.0, 0.0, 1.50),
                new Vector3d(0.10, 0.10, 1.50));

        OrientedBox3d arrowHead = box(
                origin,
                q,
                new Vector3d(0.0, 0.11, 3.60),
                new Vector3d(0.75, 0.72, 0.60));

        return new CompoundShape3d(List.of(body, shaft, arrowHead));
    }

    private static OrientedBox3d box(
            Vector3d origin,
            Quaterniond orientation,
            Vector3d localCenter,
            Vector3d halfExtents) {
        return new OrientedBox3d(
                origin.add(rotate(orientation, localCenter)),
                halfExtents,
                orientation);
    }

    private static Vector3d rotate(Quaterniond q, Vector3d v) {
        double ux = q.x();
        double uy = q.y();
        double uz = q.z();
        double s = q.w();
        double dot = ux * v.x() + uy * v.y() + uz * v.z();
        double crossX = uy * v.z() - uz * v.y();
        double crossY = uz * v.x() - ux * v.z();
        double crossZ = ux * v.y() - uy * v.x();
        double uu = ux * ux + uy * uy + uz * uz;
        return new Vector3d(
                2.0 * dot * ux + (s * s - uu) * v.x() + 2.0 * s * crossX,
                2.0 * dot * uy + (s * s - uu) * v.y() + 2.0 * s * crossY,
                2.0 * dot * uz + (s * s - uu) * v.z() + 2.0 * s * crossZ);
    }
}
