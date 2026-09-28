package com.example.collab3d.client.events;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Group;

/**
 * Tiny event hub. Any code can fire() without knowing the HUD.
 * Uses a hidden JavaFX Node as the dispatcher; safe from any thread.
 */
public final class GameEventBus {
    private static final Group HUB = new Group(); // not added to scene graph
    private GameEventBus() {}

    public static <T extends Event> void addHandler(EventType<T> type, EventHandler<? super T> handler) {
        HUB.addEventHandler(type, handler);
    }
    public static <T extends Event> void removeHandler(EventType<T> type, EventHandler<? super T> handler) {
        HUB.removeEventHandler(type, handler);
    }
    public static void fire(Event event) {
        if (Platform.isFxApplicationThread()) {
            Event.fireEvent(HUB, event);
        } else {
            Platform.runLater(() -> Event.fireEvent(HUB, event));
        }
    }
}
