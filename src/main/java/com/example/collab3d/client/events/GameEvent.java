package com.example.collab3d.client.events;

import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.event.EventType;

/** Base class for game-wide events. */
public class GameEvent extends Event {
    public static final EventType<GameEvent> ANY = new EventType<>(Event.ANY, "GAME_EVENT");
    public GameEvent(EventType<? extends Event> eventType) { super(eventType); }
    public GameEvent(Object source, EventTarget target, EventType<? extends Event> eventType) {
        super(source, target, eventType);
    }
}
