package com.example.collab3d.common;

public record Quaterniond(double x, double y, double z, double w) {
    private static final double EPSILON = 1.0e-12;

    public static Quaterniond identity() {
        return new Quaterniond(0.0, 0.0, 0.0, 1.0);
    }

    public Quaterniond normalized() {
        double lengthSquared = x * x + y * y + z * z + w * w;
        if (!Double.isFinite(lengthSquared) || lengthSquared < EPSILON) {
            return identity();
        }
        double inverseLength = 1.0 / Math.sqrt(lengthSquared);
        return new Quaterniond(
                x * inverseLength,
                y * inverseLength,
                z * inverseLength,
                w * inverseLength);
    }

    public static Quaterniond slerp(Quaterniond start, Quaterniond end, double amount) {
        Quaterniond a = start.normalized();
        Quaterniond b = end.normalized();
        double t = Math.max(0.0, Math.min(1.0, amount));
        double dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        if (dot < 0.0) {
            b = new Quaterniond(-b.x, -b.y, -b.z, -b.w);
            dot = -dot;
        }
        if (dot > 0.9995) {
            return new Quaterniond(
                    a.x + t * (b.x - a.x),
                    a.y + t * (b.y - a.y),
                    a.z + t * (b.z - a.z),
                    a.w + t * (b.w - a.w)).normalized();
        }
        double theta0 = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
        double theta = theta0 * t;
        double sinTheta = Math.sin(theta);
        double sinTheta0 = Math.sin(theta0);
        double scaleA = Math.cos(theta) - dot * sinTheta / sinTheta0;
        double scaleB = sinTheta / sinTheta0;
        return new Quaterniond(
                scaleA * a.x + scaleB * b.x,
                scaleA * a.y + scaleB * b.y,
                scaleA * a.z + scaleB * b.z,
                scaleA * a.w + scaleB * b.w).normalized();
    }
}
