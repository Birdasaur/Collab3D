package com.example.collab3d.server.interaction;

import java.util.List;

public record InteractionUpdateResult(
        InteractionState nextState,
        boolean complete,
        InteractionCompletionReason completionReason,
        List<InteractionEvent> events) {

    public static InteractionUpdateResult active(InteractionState state) {
        return new InteractionUpdateResult(state, false, null, List.of());
    }
}
