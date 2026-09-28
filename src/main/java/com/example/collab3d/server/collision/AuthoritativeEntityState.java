package com.example.collab3d.server.collision;

import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.geometry.CollisionShape3d;

/** Immutable authoritative state used for one collision pass. */
public record AuthoritativeEntityState(
        long objectId,
        Pose3d pose,
        CollisionShape3d collisionShape) {
}
