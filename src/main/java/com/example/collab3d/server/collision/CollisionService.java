package com.example.collab3d.server.collision;

import com.example.collab3d.common.Quaterniond;
import com.example.collab3d.common.geometry.CollisionResult;
import com.example.collab3d.common.geometry.CollisionShape3d;
import com.example.collab3d.common.geometry.CompoundShape3d;
import com.example.collab3d.common.geometry.OrientedBox3d;
import com.example.collab3d.common.geometry.SweptSphere3d;
import com.example.collab3d.common.geometry.Vector3d;
import java.util.Collection;

/** Pure renderer-independent swept collision queries. */
public final class CollisionService {

    public CollisionResult sweep(
            SweptSphere3d sweep,
            Collection<AuthoritativeEntityState> candidates,
            CollisionFilter filter) {

        CollisionResult best = CollisionResult.none();
        for (AuthoritativeEntityState candidate : candidates) {
            if (!filter.test(candidate)) {
                continue;
            }
            CollisionResult hit = sweepAgainstShape(
                    sweep,
                    candidate.objectId(),
                    candidate.collisionShape());
            if (hit.hit() && hit.sweepFraction() < best.sweepFraction()) {
                best = hit;
            }
        }
        return best;
    }

    private CollisionResult sweepAgainstShape(
            SweptSphere3d sweep,
            long objectId,
            CollisionShape3d shape) {

        if (shape instanceof OrientedBox3d box) {
            return sweepAgainstBox(sweep, objectId, box);
        }
        if (shape instanceof CompoundShape3d compound) {
            CollisionResult best = CollisionResult.none();
            for (CollisionShape3d child : compound.children()) {
                CollisionResult hit = sweepAgainstShape(sweep, objectId, child);
                if (hit.hit() && hit.sweepFraction() < best.sweepFraction()) {
                    best = hit;
                }
            }
            return best;
        }
        return CollisionResult.none();
    }

    private CollisionResult sweepAgainstBox(
            SweptSphere3d sweep,
            long objectId,
            OrientedBox3d box) {

        Quaterniond inverse = conjugate(box.orientation().normalized());
        Vector3d localStart = rotate(
                inverse,
                sweep.start().subtract(box.center()));
        Vector3d localEnd = rotate(
                inverse,
                sweep.end().subtract(box.center()));
        Vector3d direction = localEnd.subtract(localStart);
        Vector3d extent = box.halfExtents().add(new Vector3d(
                sweep.radius(), sweep.radius(), sweep.radius()));

        double tMin = 0.0;
        double tMax = 1.0;
        int hitAxis = -1;
        double hitSign = 0.0;

        double[] start = {localStart.x(), localStart.y(), localStart.z()};
        double[] delta = {direction.x(), direction.y(), direction.z()};
        double[] ext = {extent.x(), extent.y(), extent.z()};

        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < 1.0e-12) {
                if (start[axis] < -ext[axis] || start[axis] > ext[axis]) {
                    return CollisionResult.none();
                }
                continue;
            }

            double inverseDelta = 1.0 / delta[axis];
            double t1 = (-ext[axis] - start[axis]) * inverseDelta;
            double t2 = (ext[axis] - start[axis]) * inverseDelta;
            double nearSign = -1.0;
            if (t1 > t2) {
                double temp = t1;
                t1 = t2;
                t2 = temp;
                nearSign = 1.0;
            }

            if (t1 > tMin) {
                tMin = t1;
                hitAxis = axis;
                hitSign = nearSign;
            }
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return CollisionResult.none();
            }
        }

        if (tMin < 0.0 || tMin > 1.0) {
            return CollisionResult.none();
        }

        Vector3d localNormal = switch (hitAxis) {
            case 0 -> new Vector3d(hitSign, 0.0, 0.0);
            case 1 -> new Vector3d(0.0, hitSign, 0.0);
            case 2 -> new Vector3d(0.0, 0.0, hitSign);
            default -> direction.normalized().multiply(-1.0);
        };

        return new CollisionResult(
                true,
                objectId,
                tMin,
                Vector3d.lerp(sweep.start(), sweep.end(), tMin),
                rotate(box.orientation().normalized(), localNormal).normalized());
    }

    private static Quaterniond conjugate(Quaterniond q) {
        return new Quaterniond(-q.x(), -q.y(), -q.z(), q.w());
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
