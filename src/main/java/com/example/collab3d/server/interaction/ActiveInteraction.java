package com.example.collab3d.server.interaction;

import com.example.collab3d.common.interactions.InteractionType;

/** Interaction-thread-owned authoritative lifecycle wrapper. */
public final class ActiveInteraction {

    private final long interactionId;
    private final long actorObjectId;
    private final long clientSequence;
    private final InteractionType type;
    private final long startServerTimeNanos;
    private long lastUpdateServerTimeNanos;
    private InteractionLifecycleState lifecycleState;
    private InteractionState state;

    public ActiveInteraction(
            long interactionId,
            long actorObjectId,
            long clientSequence,
            InteractionType type,
            long startServerTimeNanos,
            InteractionState state) {
        this.interactionId = interactionId;
        this.actorObjectId = actorObjectId;
        this.clientSequence = clientSequence;
        this.type = type;
        this.startServerTimeNanos = startServerTimeNanos;
        this.lastUpdateServerTimeNanos = startServerTimeNanos;
        this.lifecycleState = InteractionLifecycleState.ACTIVE;
        this.state = state;
    }

    public long interactionId() { return interactionId; }
    public long actorObjectId() { return actorObjectId; }
    public long clientSequence() { return clientSequence; }
    public InteractionType type() { return type; }
    public long startServerTimeNanos() { return startServerTimeNanos; }
    public long lastUpdateServerTimeNanos() { return lastUpdateServerTimeNanos; }
    public InteractionLifecycleState lifecycleState() { return lifecycleState; }
    public InteractionState state() { return state; }

    public void apply(InteractionState nextState, long updateTimeNanos) {
        state = nextState;
        lastUpdateServerTimeNanos = updateTimeNanos;
    }

    public void complete() { lifecycleState = InteractionLifecycleState.COMPLETED; }
    public void cancel() { lifecycleState = InteractionLifecycleState.CANCELLED; }
}
