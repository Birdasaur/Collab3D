package com.example.collab3d.common.geometry;

/** Immutable renderer-neutral 3D vector. */
public record Vector3d(double x, double y, double z) {

    public Vector3d add(Vector3d other) {
        return new Vector3d(x + other.x, y + other.y, z + other.z);
    }

    public Vector3d subtract(Vector3d other) {
        return new Vector3d(x - other.x, y - other.y, z - other.z);
    }

    public Vector3d multiply(double scalar) {
        return new Vector3d(x * scalar, y * scalar, z * scalar);
    }

    public double dot(Vector3d other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public double lengthSquared() {
        return dot(this);
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public Vector3d normalized() {
        double length = length();
        if (!Double.isFinite(length) || length < 1.0e-12) {
            return new Vector3d(0.0, 0.0, 0.0);
        }
        return multiply(1.0 / length);
    }

    public static Vector3d lerp(Vector3d a, Vector3d b, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return new Vector3d(
                a.x + (b.x - a.x) * clamped,
                a.y + (b.y - a.y) * clamped,
                a.z + (b.z - a.z) * clamped);
    }
}
