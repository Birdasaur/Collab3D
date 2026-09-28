package com.example.collab3d.server.interaction;

import java.util.List;

public record InteractionStartResult(
        boolean accepted,
        InteractionState initialState,
        InteractionRejectReason rejectReason,
        List<InteractionEvent> events) {

    public static InteractionStartResult reject(InteractionRejectReason reason) {
        return new InteractionStartResult(false, null, reason, List.of());
    }
}
