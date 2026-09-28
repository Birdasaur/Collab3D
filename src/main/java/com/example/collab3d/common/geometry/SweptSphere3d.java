package com.example.collab3d.common.geometry;

/** Sphere translated continuously along one simulation segment. */
public record SweptSphere3d(
        Vector3d start,
        Vector3d end,
        double radius) {
}
