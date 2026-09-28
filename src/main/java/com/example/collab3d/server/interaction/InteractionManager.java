package com.example.collab3d.server.interaction;

import com.example.collab3d.common.interactions.InteractionType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Interaction-thread-owned authoritative lifecycle manager. */
public final class InteractionManager {

    private final ConcurrentLinkedQueue<StartInteractionCommand> commands =
            new ConcurrentLinkedQueue<>();
    private final Map<Long, ActiveInteraction> active = new LinkedHashMap<>();
    private final Map<InteractionType, InteractionHandler> handlers =
            new EnumMap<>(InteractionType.class);
    private final AtomicLong nextInteractionId = new AtomicLong(1L);
    private final InteractionContext context;
    private final InteractionReplicationService replication;
    private final Consumer<InteractionEvent> eventObserver;

    public InteractionManager(
            InteractionContext context,
            InteractionReplicationService replication,
            Consumer<InteractionEvent> eventObserver,
            List<InteractionHandler> handlers) {
        this.context = context;
        this.replication = replication;
        this.eventObserver = eventObserver;
        for (InteractionHandler handler : handlers) {
            this.handlers.put(handler.type(), handler);
        }
    }

    /** Safe to call from a SpiderMonkey network thread. */
    public void enqueue(StartInteractionCommand command) {
        commands.add(command);
    }

    /** Called exclusively by the fixed-step interaction simulation thread. */
    public void update(long previousServerTimeNanos, long currentServerTimeNanos) {
        drainCommands(currentServerTimeNanos);

        Iterator<ActiveInteraction> iterator = active.values().iterator();
        while (iterator.hasNext()) {
            ActiveInteraction interaction = iterator.next();
            InteractionHandler handler = handlers.get(interaction.type());
            if (handler == null) {
                iterator.remove();
                continue;
            }

            if (interaction.lastUpdateServerTimeNanos() >= currentServerTimeNanos) {
                continue;
            }
            InteractionUpdateResult result = handler.update(
                    interaction,
                    interaction.lastUpdateServerTimeNanos(),
                    currentServerTimeNanos,
                    context);
            apply(interaction, result, currentServerTimeNanos);
            replicate(result.events());
            if (result.complete()) {
                interaction.complete();
                iterator.remove();
            }
        }
    }

    private void drainCommands(long currentServerTimeNanos) {
        StartInteractionCommand command;
        while ((command = commands.poll()) != null) {
            InteractionHandler handler = handlers.get(command.type());
            if (handler == null) {
                continue;
            }

            long interactionId = nextInteractionId.getAndIncrement();
            InteractionStartResult start = handler.start(
                    interactionId,
                    command,
                    currentServerTimeNanos,
                    context);
            if (!start.accepted()) {
                continue;
            }

            ActiveInteraction interaction = new ActiveInteraction(
                    interactionId,
                    command.actorObjectId(),
                    command.clientSequence(),
                    command.type(),
                    command.eventServerTimeNanos(),
                    start.initialState());
            replicate(start.events());

            InteractionUpdateResult catchUp = handler.catchUp(
                    interaction,
                    currentServerTimeNanos,
                    context);
            apply(interaction, catchUp, currentServerTimeNanos);
            replicate(catchUp.events());
            if (!catchUp.complete()) {
                active.put(interaction.interactionId(), interaction);
            } else {
                interaction.complete();
            }
        }
    }

    private static void apply(
            ActiveInteraction interaction,
            InteractionUpdateResult result,
            long updateTimeNanos) {
        if (result.nextState() != null) {
            interaction.apply(result.nextState(), updateTimeNanos);
        }
    }

    private void replicate(List<InteractionEvent> events) {
        for (InteractionEvent event : new ArrayList<>(events)) {
            eventObserver.accept(event);
            replication.replicate(event);
        }
    }
}
