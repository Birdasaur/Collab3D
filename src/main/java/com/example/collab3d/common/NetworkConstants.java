package com.example.collab3d.common;

/** Shared networking, timing, and motion policy for the collaboration prototype. */
public final class NetworkConstants {

    public static final String APPLICATION_NAME = "JavaFx3dCollaborationDemo";

    /*
     * Version 5 adds authoritative participant result-state replication (shots fired,
     * hit count, and score) to the
     * SpiderMonkey serializer registry. Client and server must use the same
     * protocol version because serializer registration order is shared state.
     */
    public static final int PROTOCOL_VERSION = 5;

    public static final String DEFAULT_HOST = "localhost";
    public static final int TCP_PORT = 6143;
    public static final int UDP_PORT = 6144;
    public static final int MAX_CLIENTS = 12;

    public static final int CLIENT_MOTION_HZ = 120;
    public static final int CLIENT_POSE_HZ = 120;
    public static final int SERVER_STATE_HZ = 120;
    public static final int SERVER_INTERACTION_HZ = 120;

    public static final int RUNTIME_STATS_LOG_INTERVAL_SECONDS = 2;

    public static final int TIME_SYNC_INTERVAL_SECONDS = 1;
    public static final int TIME_SYNC_SAMPLE_WINDOW = 32;
    public static final int TIME_SYNC_BEST_SAMPLE_COUNT = 8;
    public static final int TIME_SYNC_MAX_PENDING = 8;
    public static final int TIME_SYNC_DIAGNOSTICS_INTERVAL_SECONDS = 5;

    public static final double WORLD_LIMIT = 100.0;

    /*
     * Authoritative remote history retained for interpolation, diagnostics, and
     * future historical/rewind queries. At 120 Hz, 500 ms is about 60 samples.
     */
    public static final long REMOTE_HISTORY_DURATION_NANOS = 500_000_000L;

    /*
     * Server authoritative history retained for rewind / lag compensation.
     * This is intentionally independent of the client render-history window.
     * At 120 Hz and 12 participants, one second is only about 1,440 samples.
     */
    public static final long SERVER_REWIND_HISTORY_DURATION_NANOS = 1_000_000_000L;

    /*
     * Adaptive interpolation/jitter-buffer policy.
     *
     * The normal low-latency target is 12 ms. A clean stream may approach the
     * 8 ms floor, while measured delivery jitter can temporarily expand the
     * buffer as far as 30 ms. Buffer growth is deliberately faster than buffer
     * shrinkage to avoid oscillating the remote render timeline.
     */
    public static final long REMOTE_INTERPOLATION_MIN_DELAY_NANOS = 8_000_000L;
    public static final long REMOTE_INTERPOLATION_DEFAULT_DELAY_NANOS = 12_000_000L;
    public static final long REMOTE_INTERPOLATION_MAX_DELAY_NANOS = 30_000_000L;

    /*
     * RFC-style inter-arrival jitter EWMA. The target interpolation buffer adds
     * two jitter estimates above the default delay. Individual jitter samples
     * are capped so a debugger pause or scheduling stall cannot poison the
     * estimator for a long period.
     */
    public static final double REMOTE_JITTER_EWMA_ALPHA = 1.0 / 16.0;
    public static final double REMOTE_JITTER_BUFFER_MULTIPLIER = 2.0;
    public static final long REMOTE_JITTER_SAMPLE_CAP_NANOS = 100_000_000L;

    /* Fast attack, slow release for the adaptive interpolation delay. */
    public static final double REMOTE_INTERPOLATION_INCREASE_ALPHA = 0.35;
    public static final double REMOTE_INTERPOLATION_DECREASE_ALPHA = 0.02;

    /*
     * Prediction remains a short fallback for temporary gaps beyond the newest
     * authoritative sample. Longer gaps hold the latest known pose.
     */
    public static final long MAX_PREDICTION_NANOS = 25_000_000L;
    public static final double RECONCILIATION_HALF_LIFE_SECONDS = 0.008;
    public static final double RECONCILIATION_SNAP_DISTANCE = 5.0;
    public static final double RECONCILIATION_SNAP_ANGLE_DEGREES = 45.0;



    /*
     * Shared tracer-round visualization policy. These values intentionally
     * describe the visual/network test round only; collision and hit validation
     * are added separately so this first interaction remains easy to diagnose.
     */
    public static final double TRACER_ROUND_SPEED_UNITS_PER_SECOND = 60.0;
    public static final long TRACER_ROUND_LIFETIME_NANOS = 500_000_000L;
    public static final double TRACER_ROUND_MUZZLE_OFFSET = 0.60;
    public static final double TRACER_ROUND_TRAIL_LENGTH = 2.50;
    public static final double TRACER_ROUND_COLLISION_RADIUS = 0.06;

    /*
     * Client-side fire-rate limit. Bounds how often a held-down key can
     * trigger sendTracerProjectile(), independent of how fast the OS/JavaFX
     * reports repeated KEY_PRESSED events for a held key. This is a
     * client-side courtesy limit only, not anti-cheat: a modified client can
     * still send at any rate, so it does not replace server-side validation.
     */
    public static final long TRACER_FIRE_MIN_INTERVAL_NANOS = 200_000_000L;

    public static final long INTERACTION_STEP_NANOS =
            1_000_000_000L / SERVER_INTERACTION_HZ;
    public static final int INTERACTION_MAX_STEPS_PER_WAKE = 8;
    public static final long INTERACTION_MAX_CATCH_UP_NANOS = 500_000_000L;

    /*
     * Interaction timestamps can be a few milliseconds ahead of the most recent
     * 120 Hz rewind sample. A small future tolerance accommodates clock-estimate
     * error while still rejecting implausibly future-dated client events.
     */
    public static final long INTERACTION_MAX_FUTURE_TOLERANCE_NANOS = 20_000_000L;
    public static final double INTERACTION_ORIGIN_VALIDATION_TOLERANCE = 2.0;

    private NetworkConstants() {
    }
}