package com.example.collab3d.common.geometry;

import java.util.List;

/** Immutable collision proxy composed from simpler world-space shapes. */
public record CompoundShape3d(List<CollisionShape3d> children)
        implements CollisionShape3d {

    public CompoundShape3d {
        children = List.copyOf(children);
    }
}
