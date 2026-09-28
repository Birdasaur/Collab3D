package com.example.collab3d.client.audio;

import com.example.collab3d.client.ResourceUtils;
import com.example.collab3d.client.events.AudioEvent;
import com.example.collab3d.client.events.GameEventBus;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.EventHandler;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simple music jukebox driven by AudioEvent. Supports enable/disable,
 * volume, random track cycling, fade transitions, playhead reporting and seek.
 *
 * Consumes AudioEvent via GameEventBus, and emits outbound AudioEvent back onto GameEventBus.
 */
public class JukeBox implements EventHandler<AudioEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(JukeBox.class);

    public static String DEFAULT_MUSIC_PATH = "music/";
    public static String SECONDARY_MUSIC_PATH = "app/music/";
    public static int FADE_OUT_SECONDS = 2;
    public static int FADE_IN_SECONDS = 2;

    private MediaPlayer currentMediaPlayer;

    private boolean transitioning = false;
    private boolean fade = true;
    private boolean cycle = true;
    private boolean enabled = false;
    private boolean externalCycle = false;

    private final List<Media> mediaFiles = new ArrayList<>();
    private final Random rng = new Random();

    private double currentVolume = 0.1;

    private Timeline playheadPoller;

    public JukeBox() {
        loadMusic();
        GameEventBus.addHandler(AudioEvent.ANY, this);
    }

    // ---------------------------
    // Public controls
    // ---------------------------

    public void setEnableMusic(boolean enabled) {
        this.enabled = enabled;

        if (!enabled) {
            stopPlayheadPoller();
            if (currentMediaPlayer != null) currentMediaPlayer.pause();
            return;
        }

        if (currentMediaPlayer != null) {
            tryPlay();
            fireNowPlaying();
            startPlayheadPoller();
            return;
        }

        if (!externalCycle) {
            setRandomTrack(true);
        }
    }

    public void setMusicVolume(double volume) {
        currentVolume = Math.max(0.0, Math.min(1.0, volume));
        if (currentMediaPlayer != null) {
            currentMediaPlayer.setVolume(currentVolume);
        }
    }

    public void setRandomTrack(boolean rightNow) {
        Media media = getRandomMedia();
        if (media == null) {
            LOG.warn("No media available to play.");
            return;
        }
        LOG.info("Now playing: {}", safeName(media));
        if (!rightNow && fade && currentMediaPlayer != null) {
            fadeOutThen(media);
        } else {
            setMedia(media);
        }
    }

    public void setMediaByName(String name) {
        if (name == null) return;
        Media media = getBySourceName(name);
        if (media == null) {
            LOG.warn("Requested track not found: {}", name);
            return;
        }
        if (fade && currentMediaPlayer != null) {
            fadeOutThen(media);
        } else {
            setMedia(media);
        }
    }

    public void setFadeEnabled(boolean fade) { this.fade = fade; }
    public void setCycleEnabled(boolean cycle) { this.cycle = cycle; }
    public void setExternalCycle(boolean externalCycle) { this.externalCycle = externalCycle; }

    // ---------------------------
    // Event handling
    // ---------------------------

    @Override
    public void handle(AudioEvent event) {
        if (event == null || event.getEventType() == null) return;

        if (event.getEventType() == AudioEvent.PLAY_MUSIC_TRACK) {
            setMediaByName((String) event.object);

        } else if (event.getEventType() == AudioEvent.PLAY_RANDOM_MUSIC_TRACK) {
            setRandomTrack(true);

        } else if (event.getEventType() == AudioEvent.RELOAD_MUSIC_FILES) {
            loadMusic();

        } else if (event.getEventType() == AudioEvent.ENABLE_MUSIC_TRACKS) {
            setEnableMusic(Boolean.TRUE.equals(event.object));

        } else if (event.getEventType() == AudioEvent.SET_MUSIC_VOLUME) {
            if (event.object instanceof Number n) setMusicVolume(n.doubleValue());

        } else if (event.getEventType() == AudioEvent.ENABLE_FADE_TRACKS) {
            setFadeEnabled(Boolean.TRUE.equals(event.object));

        } else if (event.getEventType() == AudioEvent.CYCLE_MUSIC_TRACKS) {
            setCycleEnabled(Boolean.TRUE.equals(event.object));

        } else if (event.getEventType() == AudioEvent.SET_EXTERNAL_CYCLE) {
            setExternalCycle(Boolean.TRUE.equals(event.object));

        } else if (event.getEventType() == AudioEvent.REQUEST_PLAYHEAD_STATUS) {
            emitPlayheadStatusOnce();

        } else if (event.getEventType() == AudioEvent.SEEK_TO_MILLIS) {
            if (event.object instanceof Number n) {
                long target = n.longValue();
                if (currentMediaPlayer != null) {
                    try {
                        currentMediaPlayer.seek(Duration.millis(target));
                        // After seeking, emit immediate playhead snapshot so UI updates quickly.
                        emitPlayheadStatusOnce();
                    } catch (Exception ex) {
                        LOG.warn("Seek failed to {} ms", target, ex);
                    }
                }
            }
        }
    }

    // ---------------------------
    // Internals
    // ---------------------------

    private void setMedia(Media media) {
        if (media == null) return;

        if (currentMediaPlayer != null) {
            try { currentMediaPlayer.stop(); } catch (Exception ignored) {}
            stopPlayheadPoller();
        }

        currentMediaPlayer = new MediaPlayer(media);
        fireNowPlaying();

        currentMediaPlayer.setVolume(currentVolume);
        currentMediaPlayer.setAutoPlay(true);
        currentMediaPlayer.setCycleCount(cycle ? 1 : MediaPlayer.INDEFINITE);

        currentMediaPlayer.setOnEndOfMedia(() -> {
            if (!cycle) return;

            if (externalCycle) {
                String endedName = ResourceUtils.getNameFromURI(currentMediaPlayer.getMedia().getSource());
                GameEventBus.fire(new AudioEvent(AudioEvent.TRACK_ENDED, endedName));
            } else {
                setRandomTrack(false);
            }
        });

        currentMediaPlayer.setOnReady(() -> {
            if (enabled) {
                tryPlay();
                startPlayheadPoller();
            }
        });

        if (enabled) {
            tryPlay();
            startPlayheadPoller();
        } else {
            currentMediaPlayer.pause();
        }
    }

    private void tryPlay() {
        if (currentMediaPlayer == null) return;
        try {
            currentMediaPlayer.play();
        } catch (MediaException mex) {
            LOG.error("Failed to play media: {}", safeName(currentMediaPlayer.getMedia()), mex);
        }
    }

    private void fireNowPlaying() {
        if (currentMediaPlayer == null) return;
        String sourceName = ResourceUtils.getNameFromURI(currentMediaPlayer.getMedia().getSource());
        GameEventBus.fire(new AudioEvent(AudioEvent.CURRENTLY_PLAYING_TRACK, sourceName));
    }

    private Media getRandomMedia() {
        if (mediaFiles.isEmpty()) return null;
        return mediaFiles.get(rng.nextInt(mediaFiles.size()));
    }

    private Media getBySourceName(String name) {
        if (name == null) return null;
        for (Media m : mediaFiles) {
            if (name.equals(ResourceUtils.getNameFromURI(m.getSource()))) return m;
        }
        return null;
    }

    private void fadeOutThen(Media nextMedia) {
        if (transitioning || currentMediaPlayer == null) {
            setMedia(nextMedia);
            return;
        }
        transitioning = true;
        Timeline tailOff = new Timeline(
            new KeyFrame(Duration.seconds(FADE_OUT_SECONDS),
                new KeyValue(currentMediaPlayer.volumeProperty(), 0.0))
        );
        tailOff.setOnFinished(fin -> {
            if (fade) {
                fadeInFromZero(nextMedia);
            } else {
                setMedia(nextMedia);
                transitioning = false;
            }
        });
        tailOff.play();
    }

    private void fadeInFromZero(Media nextMedia) {
        setMedia(nextMedia);
        if (currentMediaPlayer == null) {
            transitioning = false;
            return;
        }
        currentMediaPlayer.setVolume(0.1);
        Timeline tailOn = new Timeline(
            new KeyFrame(Duration.seconds(FADE_IN_SECONDS),
                new KeyValue(currentMediaPlayer.volumeProperty(), currentVolume))
        );
        tailOn.setOnFinished(fin -> transitioning = false);
        tailOn.play();
    }

    private void emitPlayheadStatusOnce() {
        if (currentMediaPlayer == null) {
            GameEventBus.fire(new AudioEvent(AudioEvent.PLAYHEAD_STATUS, Long.valueOf(0L), Long.valueOf(0L)));
            return;
        }
        long cur = (long) currentMediaPlayer.getCurrentTime().toMillis();
        long tot = currentMediaPlayer.getTotalDuration() == null ? 0L : (long) currentMediaPlayer.getTotalDuration().toMillis();
        GameEventBus.fire(new AudioEvent(AudioEvent.PLAYHEAD_STATUS, Long.valueOf(cur), Long.valueOf(tot)));
    }

    private void startPlayheadPoller() {
        stopPlayheadPoller();
        if (currentMediaPlayer == null) return;

        playheadPoller = new Timeline(new KeyFrame(Duration.millis(200), ae -> {
            if (currentMediaPlayer == null) return;
            long cur = (long) currentMediaPlayer.getCurrentTime().toMillis();
            long tot = currentMediaPlayer.getTotalDuration() == null ? 0L : (long) currentMediaPlayer.getTotalDuration().toMillis();
            GameEventBus.fire(new AudioEvent(AudioEvent.PLAYHEAD_STATUS, Long.valueOf(cur), Long.valueOf(tot)));
        }));
        playheadPoller.setCycleCount(Timeline.INDEFINITE);
        playheadPoller.play();
    }

    private void stopPlayheadPoller() {
        if (playheadPoller != null) {
            playheadPoller.stop();
            playheadPoller = null;
        }
    }

    private static String safeName(Media media) {
        try {
            return ResourceUtils.getNameFromURI(media.getSource());
        } catch (Exception e) {
            return String.valueOf(media);
        }
    }

    public Media loadMp3AsMedia(File file) {
        if (file == null) return null;
        try {
            URL url = getClass().getClassLoader().getResource(file.getPath());
            if (url == null) {
                if (file.exists()) url = file.toURI().toURL();
            }
            if (url == null) {
                LOG.warn("Could not resolve media URL for {}", file.getPath());
                return null;
            }
            return new Media(url.toString());
        } catch (MalformedURLException | MediaException ex) {
            LOG.error("Could not load music file: {}", file.getName(), ex);
            return null;
        }
    }

    private void loadMusic() {
        mediaFiles.clear();

        File folder = new File(DEFAULT_MUSIC_PATH);
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    if (name.endsWith(".mp3") || name.endsWith(".MP3")) {
                        Media media = loadMp3AsMedia(file);
                        if (media != null) mediaFiles.add(media);
                    }
                }
            }
        }
        folder = new File(SECONDARY_MUSIC_PATH);
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    if (name.endsWith(".mp3") || name.endsWith(".MP3")) {
                        Media media = loadMp3AsMedia(file);
                        if (media != null) mediaFiles.add(media);
                    }
                }
            }
        }

        GameEventBus.fire(new AudioEvent(AudioEvent.MUSIC_FILES_RELOADED, new ArrayList<>(mediaFiles)));
    }
}
