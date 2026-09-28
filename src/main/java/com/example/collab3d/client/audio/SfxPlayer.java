package com.example.collab3d.client.audio;

import com.example.collab3d.client.events.SfxEvent;
import javafx.event.EventHandler;
import javafx.scene.media.AudioClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Low-latency SFX player using AudioClip.
 *
 * Features:
 *  - Alias support (short ids → file/resource paths).
 *  - Caching of AudioClips.
 *  - Per-play params (volume/balance/rate).
 *  - Directory preloading.
 *  - Looping start/stop.
 *
 * IMPORTANT CACHE FIX:
 *  - Cache is now keyed by the *resolved URL string* (file:/... or jar:file:/...),
 *    so preloadDirectory() and resolveClip() use the same canonical key.
 *
 * This class is typically registered as an EventHandler<SfxEvent> on the
 * GameEventBus (or Scene, historically) and reacts to SfxEvent types.
 */
public class SfxPlayer implements EventHandler<SfxEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(SfxPlayer.class);

    /** Base folder where you store SFX (optional). */
    public static String DEFAULT_SFX_PATH = "sfx/";
    public static String SECONDARY_SFX_PATH = "app/sfx/";

    /**
     * Cache of resolved AudioClips by canonical URL string key.
     * Example keys:
     *  - file:/C:/game/sfx/beat1.wav
     *  - jar:file:/app.jar!/sfx/beat1.wav
     */
    private final Map<String, AudioClip> cache = new HashMap<>();

    /** Map of alias → idOrPath (can be resource or file path). */
    private final Map<String, String> aliasMap = new HashMap<>();

    /** Tracks currently looping clips by canonical URL string key. */
    private final Map<String, AudioClip> loopingClips = new HashMap<>();

    /** Master SFX volume [0..1]; multiplied by per-play volume. */
    private double masterVolume = 0.5;

    public SfxPlayer() {
        File dir = new File(DEFAULT_SFX_PATH);
        final String base;
        if (dir.exists() && dir.isDirectory()) {
            base = DEFAULT_SFX_PATH;
        } else {
            LOG.warn("SFX preload dir not found: {}", DEFAULT_SFX_PATH);
            LOG.warn("SFX preload dir resolved to : {}", dir.getAbsolutePath());

            // try secondary
            base = SECONDARY_SFX_PATH;
        }

        preloadDirectory(base);

        // Alias registration
        registerAlias("pew", base + "pew.wav");
        registerAlias("powf", base + "powf.wav");
        registerAlias("thrust", base + "thrust.wav");

    }

    // -------------------------------------------------------------------------
    // Configuration / aliasing
    // -------------------------------------------------------------------------

    /** Register a short alias (e.g., "dock" → "sfx/docking-mechanism-01.wav"). */
    public void registerAlias(String alias, String idOrPath) {
        if (alias == null || idOrPath == null) return;
        aliasMap.put(alias, idOrPath);
    }

    /** Preload a single SFX into the cache by id or path (alias allowed). */
    public void preload(String idOrPath) {
        if (idOrPath == null) return;
        String resolved = aliasMap.getOrDefault(idOrPath, idOrPath);
        resolveClip(resolved, /*preloadOnly=*/true);
    }

    /** Preload all .wav/.mp3/etc in a directory (filesystem). */
    public void preloadDirectory(String dirPath) {
        if (dirPath == null) return;

        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            LOG.warn("SFX preload dir not found: {}", dirPath);
            LOG.warn("SFX preload dir resolved to : {}", dir.getAbsolutePath());
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            String name = f.getName();
            if (name.endsWith(".wav") ||
                name.endsWith(".mp3") ||
                name.endsWith(".aiff") ||
                name.endsWith(".m4a")) {

                try {
                    URL url = f.toURI().toURL();
                    String urlStr = url.toExternalForm();
                    cache.computeIfAbsent(urlStr, AudioClip::new);
                } catch (MalformedURLException e) {
                    LOG.warn("Bad SFX file URL: {}", f, e);
                }
            }
        }
    }

    /** Set master SFX volume [0..1]. */
    public void setMasterVolume(double v) {
        masterVolume = Math.max(0.0, Math.min(1.0, v));
    }

    /** Stop all playing AudioClips that were created by this player. */
    public void stopAll() {
        // Stop any explicitly looping clips
        for (AudioClip loop : loopingClips.values()) {
            try { loop.stop(); } catch (Exception ignore) { }
        }
        loopingClips.clear();

        // Also stop anything else we have cached
        for (AudioClip clip : cache.values()) {
            try { clip.stop(); } catch (Exception ignore) { }
        }
    }

    // -------------------------------------------------------------------------
    // Event Handler
    // -------------------------------------------------------------------------

    @Override
    public void handle(SfxEvent e) {
        if (e == null || e.getEventType() == null) return;

        if (e.getEventType() == SfxEvent.PLAY_SFX) {
            String id = (e.object instanceof String s) ? s : null;
            SfxEvent.SfxParams p = (e.object2 instanceof SfxEvent.SfxParams sp)
                    ? sp : SfxEvent.SfxParams.defaults();
            play(id, p);

        } else if (e.getEventType() == SfxEvent.START_LOOP_SFX) {
            String id = (e.object instanceof String s) ? s : null;
            SfxEvent.SfxParams p = (e.object2 instanceof SfxEvent.SfxParams sp)
                    ? sp : SfxEvent.SfxParams.defaults();
            startLoop(id, p);

        } else if (e.getEventType() == SfxEvent.STOP_LOOP_SFX) {
            String id = (e.object instanceof String s) ? s : null;
            stopLoop(id);

        } else if (e.getEventType() == SfxEvent.SET_SFX_VOLUME) {
            if (e.object instanceof Number n) {
                setMasterVolume(n.doubleValue());
            }

        } else if (e.getEventType() == SfxEvent.STOP_ALL_SFX) {
            stopAll();

        } else if (e.getEventType() == SfxEvent.REGISTER_SFX_ALIAS) {
            String alias = (e.object instanceof String s) ? s : null;
            String path  = (e.object2 instanceof String s2) ? s2 : null;
            registerAlias(alias, path);

        } else if (e.getEventType() == SfxEvent.PRELOAD_SFX) {
            String id = (e.object instanceof String s) ? s : null;
            preload(id);

        } else if (e.getEventType() == SfxEvent.PRELOAD_SFX_DIR) {
            String dir = (e.object instanceof String s) ? s : null;
            preloadDirectory(dir);
        }
    }

    // -------------------------------------------------------------------------
    // Core play / loop
    // -------------------------------------------------------------------------

    /** Play by id or path. id can be an alias or a resource/file path. */
    public void play(String idOrPath, SfxEvent.SfxParams params) {
        if (idOrPath == null) return;

        // Resolve alias → real id/path
        String resolved = aliasMap.getOrDefault(idOrPath, idOrPath);

        AudioClip clip = resolveClip(resolved, /*preloadOnly=*/false);
        if (clip == null) return;

        double vol  = clamp01(params != null ? params.volume  : 1.0) * masterVolume;
        double bal  = clamp(-1.0, +1.0, params != null ? params.balance : 0.0);
        double rate = clamp(0.5, 2.0,  params != null ? params.rate    : 1.0);

        try {
            // Overlapping playback is allowed (AudioClip supports concurrent plays).
            clip.play(vol, /*balance*/ bal, /*rate*/ rate, /*pan*/ 0.0, /*priority*/ 0);
        } catch (Exception ex) {
            LOG.warn("Failed to play SFX '{}'", resolved, ex);
        }
    }

    /** Start looping a sound until explicitly stopped. */
    public void startLoop(String idOrPath, SfxEvent.SfxParams params) {
        if (idOrPath == null) return;

        String resolved = aliasMap.getOrDefault(idOrPath, idOrPath);

        String urlKey = resolveUrlString(resolved);
        if (urlKey == null) {
            LOG.warn("SFX not found (loop): {}", resolved);
            return;
        }

        // If already looping this URL, don't start another
        if (loopingClips.containsKey(urlKey)) return;

        AudioClip clip = cache.computeIfAbsent(urlKey, AudioClip::new);

        double vol  = clamp01(params != null ? params.volume  : 1.0) * masterVolume;
        double bal  = clamp(-1.0, +1.0, params != null ? params.balance : 0.0);
        double rate = clamp(0.5, 2.0,  params != null ? params.rate    : 1.0);

        // Configure the base properties on the looping AudioClip
        clip.setVolume(vol);
        clip.setBalance(bal);
        clip.setRate(rate);
        clip.setCycleCount(AudioClip.INDEFINITE);

        try {
            clip.play(vol, bal, rate, 0.0, Integer.MAX_VALUE);
            loopingClips.put(urlKey, clip);
        } catch (Exception ex) {
            LOG.warn("Failed to start looping SFX '{}'", resolved, ex);
        }
    }

    /** Stop looping a sound previously started with startLoop. */
    public void stopLoop(String idOrPath) {
        if (idOrPath == null) return;

        String resolved = aliasMap.getOrDefault(idOrPath, idOrPath);

        String urlKey = resolveUrlString(resolved);
        if (urlKey == null) return;

        AudioClip clip = loopingClips.remove(urlKey);
        if (clip != null) {
            try {
                clip.stop();
                // Optional: reset cycle count back to 1 for future non-looping plays
                clip.setCycleCount(1);
            } catch (Exception ex) {
                LOG.warn("Failed to stop looping SFX '{}'", resolved, ex);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolve an idOrPath to a canonical URL string.
     * This is the canonical cache key.
     */
    private String resolveUrlString(String idOrPath) {
        if (idOrPath == null || idOrPath.isEmpty()) return null;

        // Try as classpath resource first
        URL res = getClass().getClassLoader().getResource(idOrPath);
        if (res == null) {
            // Try under default sfx folder (classpath)
            res = getClass().getClassLoader().getResource(DEFAULT_SFX_PATH + idOrPath);
        }
        if (res != null) {
            return res.toExternalForm();
        }

        // Fallback: filesystem
        File f = new File(idOrPath);
        if (!f.exists()) f = new File(DEFAULT_SFX_PATH, idOrPath);
        if (f.exists()) {
            try {
                return f.toURI().toURL().toString();
            } catch (MalformedURLException ex) {
                LOG.warn("Bad SFX file path: {}", f, ex);
                return null;
            }
        }

        return null;
    }

    private AudioClip resolveClip(String idOrPath, boolean preloadOnly) {
        if (idOrPath == null || idOrPath.isEmpty()) return null;

        String urlStr = resolveUrlString(idOrPath);
        if (urlStr == null) {
            if (!preloadOnly) LOG.warn("SFX not found: {}", idOrPath);
            return null;
        }

        return cache.computeIfAbsent(urlStr, AudioClip::new);
    }

    private static double clamp01(double v) {
        return (v < 0.0) ? 0.0 : (v > 1.0) ? 1.0 : v;
    }

    private static double clamp(double lo, double hi, double v) {
        return (v < lo) ? lo : (v > hi) ? hi : v;
    }
}
