package com.example.collab3d.server.interaction;

public record ProjectileInteractionConfiguration(
        double speed,
        double radius,
        double muzzleOffset,
        long lifetimeNanos,
        long maxCatchUpNanos,
        long simulationStepNanos) {
}
