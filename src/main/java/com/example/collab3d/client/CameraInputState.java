package com.example.collab3d.client;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe bridge between JavaFX input events and the high-frequency local
 * camera simulation loop.
 *
 * <p>The JavaFX Application Thread is the producer: key handlers update the
 * current movement mask and mouse handlers accumulate raw look deltas. The
 * local motion scheduler is the consumer: once per motion step it snapshots
 * the key state and atomically drains the accumulated mouse delta.</p>
 *
 * <p>No JavaFX object is stored here. Keeping this class free of scene-graph
 * state is intentional: the 120 Hz motion loop must never read or mutate
 * JavaFX nodes from a background thread.</p>
 */
final class CameraInputState {

    private static final int FORWARD = 1 << 0;
    private static final int BACKWARD = 1 << 1;
    private static final int LEFT = 1 << 2;
    private static final int RIGHT = 1 << 3;
    private static final int UP = 1 << 4;
    private static final int DOWN = 1 << 5;
    private static final int FAST = 1 << 6;

    private static final LookDelta ZERO_LOOK = new LookDelta(0.0, 0.0);

    private final AtomicInteger movementMask = new AtomicInteger();
    private final AtomicReference<LookDelta> accumulatedLook =
            new AtomicReference<>(ZERO_LOOK);

    void setForward(boolean pressed) {
        setFlag(FORWARD, pressed);
    }

    void setBackward(boolean pressed) {
        setFlag(BACKWARD, pressed);
    }

    void setLeft(boolean pressed) {
        setFlag(LEFT, pressed);
    }

    void setRight(boolean pressed) {
        setFlag(RIGHT, pressed);
    }

    void setUp(boolean pressed) {
        setFlag(UP, pressed);
    }

    void setDown(boolean pressed) {
        setFlag(DOWN, pressed);
    }

    void setFast(boolean pressed) {
        setFlag(FAST, pressed);
    }

    /** Clears held movement controls, for example when the SubScene loses focus. */
    void clearMovement() {
        movementMask.set(0);
    }

    /**
     * Adds raw mouse movement in JavaFX scene-coordinate pixels.
     *
     * <p>A compare-and-set accumulation is used instead of a mutable pair so a
     * concurrent motion tick cannot lose a mouse event while draining input.</p>
     */
    void addLookDelta(double deltaX, double deltaY) {
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaY)) {
            return;
        }
        accumulatedLook.updateAndGet(previous -> new LookDelta(
                previous.deltaX() + deltaX,
                previous.deltaY() + deltaY));
    }

    /**
     * Returns the current held controls and consumes all look movement received
     * since the previous snapshot.
     */
    Snapshot snapshotAndConsumeLook() {
        int mask = movementMask.get();
        LookDelta look = accumulatedLook.getAndSet(ZERO_LOOK);
        return new Snapshot(
                (mask & FORWARD) != 0,
                (mask & BACKWARD) != 0,
                (mask & LEFT) != 0,
                (mask & RIGHT) != 0,
                (mask & UP) != 0,
                (mask & DOWN) != 0,
                (mask & FAST) != 0,
                look.deltaX(),
                look.deltaY());
    }

    private void setFlag(int flag, boolean enabled) {
        movementMask.updateAndGet(mask -> enabled
                ? mask | flag
                : mask & ~flag);
    }

    record Snapshot(
            boolean forward,
            boolean backward,
            boolean left,
            boolean right,
            boolean up,
            boolean down,
            boolean fast,
            double lookDeltaX,
            double lookDeltaY) {
    }

    private record LookDelta(double deltaX, double deltaY) {
    }
}
