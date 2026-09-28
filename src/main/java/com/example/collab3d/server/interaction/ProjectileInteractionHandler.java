package com.example.collab3d.server.interaction;

import com.example.collab3d.common.NetworkConstants;
import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.geometry.CollisionResult;
import com.example.collab3d.common.geometry.SweptSphere3d;
import com.example.collab3d.common.geometry.Vector3d;
import com.example.collab3d.common.interactions.InteractionType;
import com.example.collab3d.common.interactions.SharedInteraction;
import com.example.collab3d.server.collision.AuthoritativeEntityState;
import com.example.collab3d.server.collision.AuthoritativeWorldSnapshot;
import java.util.ArrayList;
import java.util.List;

/** Authoritative simulated tracer projectile with historical catch-up. */
public final class ProjectileInteractionHandler implements InteractionHandler {

    private final ProjectileInteractionConfiguration config;

    public ProjectileInteractionHandler(ProjectileInteractionConfiguration config) {
        this.config = config;
    }

    @Override
    public InteractionType type() {
        return InteractionType.TRACER_PROJECTILE;
    }

    @Override
    public InteractionStartResult start(
            long interactionId,
            StartInteractionCommand command,
            long acceptedServerTimeNanos,
            InteractionContext context) {

        if (command.eventServerTimeNanos() > acceptedServerTimeNanos
                + NetworkConstants.INTERACTION_MAX_FUTURE_TOLERANCE_NANOS) {
            return InteractionStartResult.reject(
                    InteractionRejectReason.TOO_FAR_IN_FUTURE);
        }
        if (acceptedServerTimeNanos - command.eventServerTimeNanos()
                > config.maxCatchUpNanos()) {
            return InteractionStartResult.reject(InteractionRejectReason.TOO_OLD);
        }

        Vector3d direction = command.direction().normalized();
        if (direction.lengthSquared() < 1.0e-12) {
            return InteractionStartResult.reject(
                    InteractionRejectReason.INVALID_PARAMETERS);
        }

        AuthoritativeWorldSnapshot sampled = context.worldStateSampler()
                .sampleAt(command.eventServerTimeNanos());
        AuthoritativeEntityState actor = sampled.entities()
                .get(command.actorObjectId());

        if (actor == null) {
            AuthoritativeWorldSnapshot latest = context.worldStateSampler().latest();
            if (command.eventServerTimeNanos() <= latest.serverTimeNanos()
                    + NetworkConstants.INTERACTION_MAX_FUTURE_TOLERANCE_NANOS) {
                actor = latest.entities().get(command.actorObjectId());
            }
        }
        if (actor == null) {
            return InteractionStartResult.reject(
                    InteractionRejectReason.ACTOR_NOT_FOUND);
        }

        Pose3d pose = actor.pose();
        Vector3d authoritativeOrigin = new Vector3d(
                pose.x(), pose.y(), pose.z());
        if (command.submittedOrigin() != null) {
            double originError = command.submittedOrigin()
                    .subtract(authoritativeOrigin)
                    .length();
            if (!Double.isFinite(originError)
                    || originError
                    > NetworkConstants.INTERACTION_ORIGIN_VALIDATION_TOLERANCE) {
                return InteractionStartResult.reject(
                        InteractionRejectReason.INVALID_PARAMETERS);
            }
        }

        Vector3d muzzlePosition = authoritativeOrigin.add(
                direction.multiply(config.muzzleOffset()));
        Vector3d velocity = direction.multiply(config.speed());
        ProjectileInteractionState state = new ProjectileInteractionState(
                authoritativeOrigin,
                muzzlePosition,
                velocity,
                config.radius(),
                command.eventServerTimeNanos(),
                command.eventServerTimeNanos() + config.lifetimeNanos());

        SharedInteraction start = new SharedInteraction(
                interactionId,
                command.actorObjectId(),
                command.clientSequence(),
                command.eventServerTimeNanos(),
                acceptedServerTimeNanos,
                type(),
                authoritativeOrigin.x(),
                authoritativeOrigin.y(),
                authoritativeOrigin.z(),
                direction.x(),
                direction.y(),
                direction.z(),
                -1L);

        return new InteractionStartResult(
                true,
                state,
                InteractionRejectReason.NONE,
                List.of(new InteractionStartedEvent(start)));
    }

    @Override
    public InteractionUpdateResult catchUp(
            ActiveInteraction interaction,
            long targetServerTimeNanos,
            InteractionContext context) {

        ProjectileInteractionState state = stateOf(interaction);
        long from = interaction.startServerTimeNanos();
        long target = Math.min(
                targetServerTimeNanos,
                state.expirationServerTimeNanos());
        InteractionUpdateResult result = InteractionUpdateResult.active(state);

        while (from < target && !result.complete()) {
            long to = Math.min(from + config.simulationStepNanos(), target);
            AuthoritativeWorldSnapshot world = context.worldStateSampler().sampleAt(to);
            result = advance(interaction, stateOf(result), from, to, world, context);
            from = to;
        }

        if (!result.complete()
                && targetServerTimeNanos >= state.expirationServerTimeNanos()) {
            return complete(
                    interaction,
                    stateOf(result),
                    state.expirationServerTimeNanos(),
                    InteractionCompletionReason.EXPIRED,
                    List.of());
        }
        return result;
    }

    @Override
    public InteractionUpdateResult update(
            ActiveInteraction interaction,
            long previousServerTimeNanos,
            long currentServerTimeNanos,
            InteractionContext context) {

        ProjectileInteractionState state = stateOf(interaction);
        long to = Math.min(
                currentServerTimeNanos,
                state.expirationServerTimeNanos());

        InteractionUpdateResult result = advance(
                interaction,
                state,
                previousServerTimeNanos,
                to,
                context.worldStateSampler().latest(),
                context);

        if (!result.complete()
                && currentServerTimeNanos >= state.expirationServerTimeNanos()) {
            return complete(
                    interaction,
                    stateOf(result),
                    state.expirationServerTimeNanos(),
                    InteractionCompletionReason.EXPIRED,
                    List.of());
        }
        return result;
    }

    private InteractionUpdateResult advance(
            ActiveInteraction interaction,
            ProjectileInteractionState state,
            long fromTime,
            long toTime,
            AuthoritativeWorldSnapshot world,
            InteractionContext context) {

        if (toTime <= fromTime) {
            return InteractionUpdateResult.active(state);
        }

        double seconds = (toTime - fromTime) / 1_000_000_000.0;
        Vector3d nextPosition = state.position().add(
                state.velocity().multiply(seconds));

        CollisionResult collision = context.collisionService().sweep(
                new SweptSphere3d(
                        state.position(),
                        nextPosition,
                        state.radius()),
                world.entities().values(),
                entity -> entity.objectId() != interaction.actorObjectId());

        if (!collision.hit()) {
            return InteractionUpdateResult.active(new ProjectileInteractionState(
                    state.origin(),
                    nextPosition,
                    state.velocity(),
                    state.radius(),
                    state.startServerTimeNanos(),
                    state.expirationServerTimeNanos()));
        }

        long impactTime = fromTime + Math.round(
                (toTime - fromTime) * collision.sweepFraction());
        ProjectileInteractionState impactState = new ProjectileInteractionState(
                state.origin(),
                collision.impactPoint(),
                state.velocity(),
                state.radius(),
                state.startServerTimeNanos(),
                state.expirationServerTimeNanos());

        List<InteractionEvent> events = new ArrayList<>();
        events.add(new InteractionCollisionEvent(
                interaction.interactionId(),
                interaction.actorObjectId(),
                interaction.clientSequence(),
                collision.targetObjectId(),
                impactTime,
                collision.impactPoint(),
                collision.impactNormal()));
        return complete(
                interaction,
                impactState,
                impactTime,
                InteractionCompletionReason.COLLISION,
                events);
    }

    private InteractionUpdateResult complete(
            ActiveInteraction interaction,
            ProjectileInteractionState state,
            long eventTime,
            InteractionCompletionReason reason,
            List<InteractionEvent> preceding) {

        List<InteractionEvent> events = new ArrayList<>(preceding);
        events.add(new InteractionCompletedEvent(
                interaction.interactionId(),
                interaction.actorObjectId(),
                eventTime,
                reason));
        return new InteractionUpdateResult(state, true, reason, List.copyOf(events));
    }

    private static ProjectileInteractionState stateOf(
            ActiveInteraction interaction) {
        return (ProjectileInteractionState) interaction.state();
    }

    private static ProjectileInteractionState stateOf(
            InteractionUpdateResult result) {
        return (ProjectileInteractionState) result.nextState();
    }
}
