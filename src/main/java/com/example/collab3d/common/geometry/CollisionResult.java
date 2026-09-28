package com.example.collab3d.common.geometry;

/** Earliest collision found for one sweep. */
public record CollisionResult(
        boolean hit,
        long targetObjectId,
        double sweepFraction,
        Vector3d impactPoint,
        Vector3d impactNormal) {

    public static CollisionResult none() {
        return new CollisionResult(
                false,
                -1L,
                Double.POSITIVE_INFINITY,
                null,
                null);
    }
}
