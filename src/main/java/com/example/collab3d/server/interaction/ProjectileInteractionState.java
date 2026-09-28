package com.example.collab3d.server.interaction;

import com.example.collab3d.common.geometry.Vector3d;

public record ProjectileInteractionState(
        Vector3d origin,
        Vector3d position,
        Vector3d velocity,
        double radius,
        long startServerTimeNanos,
        long expirationServerTimeNanos)
        implements InteractionState {
}
