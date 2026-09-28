package com.example.collab3d.client.events;

import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.event.EventType;

/**
 * Media-layer events (music control).
 *
 * Payload conventions (event.object):
 * - PLAY_MUSIC_TRACK: String track name (e.g., "dock-01.mp3")
 * - PLAY_RANDOM_MUSIC_TRACK: (ignored) choose a random track immediately (standalone mode)
 * - ENABLE_MUSIC_TRACKS: Boolean
 * - SET_MUSIC_VOLUME: Number (0.0 - 1.0)
 * - ENABLE_FADE_TRACKS: Boolean
 * - CYCLE_MUSIC_TRACKS: Boolean
 * - SET_EXTERNAL_CYCLE: Boolean (true = MusicDirector controls cycling)
 *
 * Emitted (outbound) events:
 * - CURRENTLY_PLAYING_TRACK: String track name
 * - TRACK_ENDED: String track name that finished
 * - MUSIC_FILES_RELOADED: List<Media>
 *
 * Playhead-related:
 * - REQUEST_PLAYHEAD_STATUS: request JukeBox to emit a PLAYHEAD_STATUS once (no payload).
 * - PLAYHEAD_STATUS: emitted by JukeBox periodically while playing or in response to REQUEST_PLAYHEAD_STATUS.
 *     object  = Number currentMillis (Long/Number)
 *     object2 = Number totalMillis   (Long/Number, may be 0 if unknown)
 * - SEEK_TO_MILLIS: request JukeBox to seek to a given position.
 *     object = Number targetMillis (Long/Number)
 */
public class AudioEvent extends Event {
    private static final long serialVersionUID = 1L;

    /** Base type for all audio events. */
    public static final EventType<AudioEvent> ANY =
            new EventType<>(Event.ANY, "AUDIO_EVENT");

    // Music control (consumed by JukeBox)
    public static final EventType<AudioEvent> NEW_AUDIO_FILE =
            new EventType<>(ANY, "NEW_AUDIO_FILE");

    public static final EventType<AudioEvent> PLAY_MUSIC_TRACK =
            new EventType<>(ANY, "PLAY_MUSIC_TRACK");

    /** Play a random track from the entire loaded library (standalone mode). */
    public static final EventType<AudioEvent> PLAY_RANDOM_MUSIC_TRACK =
            new EventType<>(ANY, "PLAY_RANDOM_MUSIC_TRACK");

    public static final EventType<AudioEvent> RELOAD_MUSIC_FILES =
            new EventType<>(ANY, "RELOAD_MUSIC_FILES");

    public static final EventType<AudioEvent> MUSIC_FILES_RELOADED =
            new EventType<>(ANY, "MUSIC_FILES_RELOADED");

    public static final EventType<AudioEvent> ENABLE_MUSIC_TRACKS =
            new EventType<>(ANY, "ENABLE_MUSIC_TRACKS");

    public static final EventType<AudioEvent> SET_MUSIC_VOLUME =
            new EventType<>(ANY, "SET_MUSIC_VOLUME");

    public static final EventType<AudioEvent> ENABLE_FADE_TRACKS =
            new EventType<>(ANY, "ENABLE_FADE_TRACKS");

    /**
     * true  -> play track once, advance on end (policy dependent)
     * false -> loop current track indefinitely
     */
    public static final EventType<AudioEvent> CYCLE_MUSIC_TRACKS =
            new EventType<>(ANY, "CYCLE_MUSIC_TRACKS");

    /**
     * true  -> JukeBox emits TRACK_ENDED and does NOT choose next track
     * false -> JukeBox chooses next track internally (standalone mode)
     */
    public static final EventType<AudioEvent> SET_EXTERNAL_CYCLE =
            new EventType<>(ANY, "SET_EXTERNAL_CYCLE");

    // Playback status (emitted by JukeBox)
    public static final EventType<AudioEvent> CURRENTLY_PLAYING_TRACK =
            new EventType<>(ANY, "CURRENTLY_PLAYING_TRACK");

    /** Fired when a track completes and external cycling is enabled. */
    public static final EventType<AudioEvent> TRACK_ENDED =
            new EventType<>(ANY, "TRACK_ENDED");

    // Playhead support
    /** Request a single playhead status emission from JukeBox. No payload. */
    public static final EventType<AudioEvent> REQUEST_PLAYHEAD_STATUS =
            new EventType<>(ANY, "REQUEST_PLAYHEAD_STATUS");

    /**
     * Emitted by JukeBox periodically while playing, or in response to
     * REQUEST_PLAYHEAD_STATUS. Payload:
     *   object  = Number currentMillis
     *   object2 = Number totalMillis
     */
    public static final EventType<AudioEvent> PLAYHEAD_STATUS =
            new EventType<>(ANY, "PLAYHEAD_STATUS");

    /**
     * Seek request. object = Number targetMillis (Long/Number)
     */
    public static final EventType<AudioEvent> SEEK_TO_MILLIS =
            new EventType<>(ANY, "SEEK_TO_MILLIS");

    /** Generic payloads (optional). */
    public final Object object;
    public final Object object2;

    public AudioEvent(EventType<? extends AudioEvent> type) {
        this(type, null, null);
    }

    public AudioEvent(EventType<? extends AudioEvent> type, Object object) {
        this(type, object, null);
    }

    public AudioEvent(EventType<? extends AudioEvent> type, Object object, Object object2) {
        super(type);
        this.object = object;
        this.object2 = object2;
    }

    public AudioEvent(Object source, EventTarget target,
                      EventType<? extends AudioEvent> type,
                      Object object, Object object2) {
        super(source, target, type);
        this.object = object;
        this.object2 = object2;
    }

    @Override
    public AudioEvent copyFor(Object newSource, EventTarget newTarget) {
        return new AudioEvent(newSource, newTarget, getEventType(), object, object2);
    }

    @SuppressWarnings("unchecked")
    @Override
    public EventType<? extends AudioEvent> getEventType() {
        return (EventType<? extends AudioEvent>) super.getEventType();
    }
}
