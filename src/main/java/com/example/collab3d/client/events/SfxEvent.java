package com.example.collab3d.client.events;

import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.event.EventType;

/** Event type for low-latency sound effects control. */
public class SfxEvent extends Event {
    private static final long serialVersionUID = 1L;

    /** Base type for all SFX events. */
    public static final EventType<SfxEvent> ANY =
            new EventType<>(Event.ANY, "SFX_EVENT");

    /** Play a sound effect. object = idOrPath (String). object2 = SfxParams (optional). */
    public static final EventType<SfxEvent> PLAY_SFX =
            new EventType<>(ANY, "PLAY_SFX");

    /** Start looping a sound. object = idOrPath (String). object2 = SfxParams (optional). */
    public static final EventType<SfxEvent> START_LOOP_SFX =
            new EventType<>(ANY, "START_LOOP_SFX");

    /** Stop looping a sound. object = idOrPath (String). */
    public static final EventType<SfxEvent> STOP_LOOP_SFX =
            new EventType<>(ANY, "STOP_LOOP_SFX");

    /** Stop all currently playing SFX. */
    public static final EventType<SfxEvent> STOP_ALL_SFX =
            new EventType<>(ANY, "STOP_ALL_SFX");

    /** Set global SFX master volume (0..1). object = Double. */
    public static final EventType<SfxEvent> SET_SFX_VOLUME =
            new EventType<>(ANY, "SET_SFX_VOLUME");

    /** Register an alias mapping. object = alias (String), object2 = idOrPath (String). */
    public static final EventType<SfxEvent> REGISTER_SFX_ALIAS =
            new EventType<>(ANY, "REGISTER_SFX_ALIAS");

    /** Preload a single SFX into cache. object = idOrPath (String). */
    public static final EventType<SfxEvent> PRELOAD_SFX =
            new EventType<>(ANY, "PRELOAD_SFX");

    /** Preload all .wav/.mp3 in a directory. object = dirPath (String). */
    public static final EventType<SfxEvent> PRELOAD_SFX_DIR =
            new EventType<>(ANY, "PRELOAD_SFX_DIR");
    
    public static final EventType<SfxEvent> COMPLETED =
            new EventType<>(ANY, "SFX_COMPLETED");
    

    /** Generic payloads. */
    public final Object object;
    public final Object object2;

    public SfxEvent(EventType<? extends SfxEvent> type) {
        this(type, null, null);
    }

    public SfxEvent(EventType<? extends SfxEvent> type, Object object) {
        this(type, object, null);
    }

    public SfxEvent(EventType<? extends SfxEvent> type, Object object, Object object2) {
        super(type);
        this.object = object;
        this.object2 = object2;
    }

    public SfxEvent(Object source, EventTarget target,
                    EventType<? extends SfxEvent> type,
                    Object object, Object object2) {
        super(source, target, type);
        this.object = object;
        this.object2 = object2;
    }

    @Override
    public SfxEvent copyFor(Object newSource, EventTarget newTarget) {
        return new SfxEvent(newSource, newTarget, getEventType(), object, object2);
    }

    @Override
    public EventType<? extends SfxEvent> getEventType() {
        return (EventType<? extends SfxEvent>) super.getEventType();
    }

    /** Optional per-play parameters (volume/pan/rate). */
    public static final class SfxParams {
        /** 0..1 (applied over master). */
        public final double volume;
        /** -1..+1 (left..right), 0 = center. */
        public final double balance;
        /** 0.5..2.0 (slow..fast). */
        public final double rate;

        public SfxParams(double volume, double balance, double rate) {
            this.volume  = volume;
            this.balance = balance;
            this.rate    = rate;
        }

        public static SfxParams defaults() { return new SfxParams(1.0, 0.0, 1.0); }
    }
}
