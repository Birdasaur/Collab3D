package com.example.collab3d.server.interaction;

import com.example.collab3d.server.collision.CollisionService;
import com.example.collab3d.server.collision.WorldStateSampler;
import java.util.Objects;

/** Narrow read-only service bundle exposed to interaction handlers. */
public final class InteractionContext {

    private final WorldStateSampler worldStateSampler;
    private final CollisionService collisionService;

    public InteractionContext(
            WorldStateSampler worldStateSampler,
            CollisionService collisionService) {
        this.worldStateSampler = Objects.requireNonNull(worldStateSampler, "worldStateSampler");
        this.collisionService = Objects.requireNonNull(collisionService, "collisionService");
    }

    public WorldStateSampler worldStateSampler() { return worldStateSampler; }
    public CollisionService collisionService() { return collisionService; }
}
