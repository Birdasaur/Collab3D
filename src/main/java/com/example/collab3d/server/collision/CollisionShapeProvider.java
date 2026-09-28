package com.example.collab3d.server.collision;

import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.geometry.CollisionShape3d;

@FunctionalInterface
public interface CollisionShapeProvider {
    CollisionShape3d createShape(long objectId, Pose3d pose);
}
