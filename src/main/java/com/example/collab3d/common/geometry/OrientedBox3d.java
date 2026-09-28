package com.example.collab3d.common.geometry;

import com.example.collab3d.common.Quaterniond;

/** World-space oriented bounding box. */
public record OrientedBox3d(
        Vector3d center,
        Vector3d halfExtents,
        Quaterniond orientation)
        implements CollisionShape3d {
}
