package com.example.collab3d.server.collision;

@FunctionalInterface
public interface CollisionFilter {
    boolean test(AuthoritativeEntityState entity);
}
