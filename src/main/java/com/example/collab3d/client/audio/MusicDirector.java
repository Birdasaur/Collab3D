package com.example.collab3d.client.audio;

import com.example.collab3d.client.ResourceUtils;
import com.example.collab3d.client.events.AudioEvent;
import com.example.collab3d.client.events.GameEventBus;
import javafx.event.EventHandler;
import javafx.scene.media.Media;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MusicDirector {

    private enum State {
        INITIALIZATION,
        AMBIENT
    }

    private final Random rng = new Random();

    private State currentState = State.INITIALIZATION; // force initial load of music

    // Prefix rules
    private final String ambientPrefix = "ambience-";
    private final String initPrefix = "init-";

    // Library snapshot (names only)
    private final List<String> allTrackNames = new ArrayList<>();

    // Current category pool (derived from prefixes + library)
    private List<String> currentPool = new ArrayList<>();
    private String lastTrack = null;

    /**
     * Boot-time behavior:
     * We do NOT want to fade out whatever JukeBox started with (often defaultMedia).
     * The first directed track selection should be an immediate cut to the target track.
     */
    private boolean firstSelection = true;

    public MusicDirector() {
    }

    public void register() {
        GameEventBus.addHandler(AudioEvent.ANY, audioHandler);

        // Make sure the jukebox is in externally-driven mode whenever the director exists.
        GameEventBus.fire(new AudioEvent(AudioEvent.SET_EXTERNAL_CYCLE, true));
    }

    private final EventHandler<AudioEvent> audioHandler = e -> {

        if (e.getEventType() == AudioEvent.MUSIC_FILES_RELOADED) {
            // JukeBox publishes List<Media>
            allTrackNames.clear();

            if (e.object instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Media m) {
                        String name = ResourceUtils.getNameFromURI(m.getSource());
                        if (name != null) allTrackNames.add(name);
                    }
                }
            }

            // Keep JukeBox in externally-driven mode.
            GameEventBus.fire(new AudioEvent(AudioEvent.SET_EXTERNAL_CYCLE, true));

            // Refresh pool (based on current state), but do NOT auto-start during INITIALIZATION.
            refreshPoolForState(currentState);

            boolean shouldCycle = currentPool.size() > 1;
            GameEventBus.fire(new AudioEvent(AudioEvent.CYCLE_MUSIC_TRACKS, shouldCycle));

            // Key fix:
            // Do not pick/play any track while the app is still in INITIALIZATION.
            // We only start playback once a real state (menu/docked/ambient/market) is chosen.
            if (currentState != State.INITIALIZATION) {
                ensurePlaying();
            }
        }

        // Emitted by JukeBox when a track finishes and external-cycle is enabled.
        if (e.getEventType() == AudioEvent.TRACK_ENDED) {
            // Only advance if current pool indicates cycling (size > 1)
            if (currentPool.size() > 1) {
                playRandomFromCurrentPool(true);
            }
        }
    };

    /* ------------------------------------------------------------ */
    /* Policy                                                       */
    /* ------------------------------------------------------------ */

    private void recompute() {

        State next;

        next = State.AMBIENT;

        if (next == currentState) {
            return; // no thrash
        }

        currentState = next;
        refreshPoolForState(currentState);

        // Ensure we are driving track advancement from the director.
        GameEventBus.fire(new AudioEvent(AudioEvent.SET_EXTERNAL_CYCLE, true));

        // If there are 2+ tracks in the category, cycle through them.
        // If only 1, loop indefinitely (no cycling).
        boolean shouldCycle = currentPool.size() > 1;
        GameEventBus.fire(new AudioEvent(AudioEvent.CYCLE_MUSIC_TRACKS, shouldCycle));

        // Pick something appropriate for the new state.
        playRandomFromCurrentPool(false);
    }

    private void refreshPoolForState(State state) {
        String prefix = switch (state) {
            case AMBIENT -> ambientPrefix;
            default -> initPrefix;
        };

        List<String> pool = new ArrayList<>();
        for (String name : allTrackNames) {
            if (name != null && name.startsWith(prefix)) {
                pool.add(name);
            }
        }

        // If no matches, fall back to all tracks (better than silence).
        currentPool = pool.isEmpty() ? new ArrayList<>(allTrackNames) : pool;
    }

    private void ensurePlaying() {
        // If we don’t know what’s playing, just start something in the current state.
        if (lastTrack == null) {
            playRandomFromCurrentPool(false);
        }
    }

    private void playRandomFromCurrentPool(boolean avoidImmediateRepeat) {
        if (currentPool.isEmpty()) return;

        String next = pickRandom(currentPool, avoidImmediateRepeat ? lastTrack : null);
        if (next == null) return;

        lastTrack = next;

        // One-time boot behavior: no fade on the very first directed selection.
        if (firstSelection) {
            firstSelection = false;
            GameEventBus.fire(new AudioEvent(AudioEvent.ENABLE_FADE_TRACKS, false));
            GameEventBus.fire(new AudioEvent(AudioEvent.PLAY_MUSIC_TRACK, next));
            GameEventBus.fire(new AudioEvent(AudioEvent.ENABLE_FADE_TRACKS, true));
        } else {
            GameEventBus.fire(new AudioEvent(AudioEvent.PLAY_MUSIC_TRACK, next));
        }
    }

    private String pickRandom(List<String> pool, String avoid) {
        if (pool.isEmpty()) return null;
        if (avoid == null || pool.size() == 1) {
            return pool.get(rng.nextInt(pool.size()));
        }

        // Try a few times to avoid repeating the same track
        for (int i = 0; i < 6; i++) {
            String candidate = pool.get(rng.nextInt(pool.size()));
            if (!candidate.equals(avoid)) return candidate;
        }
        // If random keeps hitting the same, just return something
        return pool.get(rng.nextInt(pool.size()));
    }
}
