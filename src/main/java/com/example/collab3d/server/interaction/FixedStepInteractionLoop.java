package com.example.collab3d.server.interaction;

import com.example.collab3d.common.NetworkConstants;
import java.util.concurrent.locks.LockSupport;

/** Dedicated fixed-step 120 Hz interaction simulation loop. */
public final class FixedStepInteractionLoop implements AutoCloseable {

    private final InteractionManager manager;
    private volatile boolean running;
    private Thread thread;

    public FixedStepInteractionLoop(InteractionManager manager) {
        this.manager = manager;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::runLoop, "authoritative-interaction-loop");
        thread.setDaemon(false);
        thread.start();
    }

    private void runLoop() {
        long previousReal = System.nanoTime();
        long simulationTime = previousReal;
        long accumulator = 0L;

        while (running) {
            long now = System.nanoTime();
            long elapsed = Math.max(0L, now - previousReal);
            previousReal = now;
            accumulator += elapsed;

            int steps = 0;
            while (accumulator >= NetworkConstants.INTERACTION_STEP_NANOS
                    && steps < NetworkConstants.INTERACTION_MAX_STEPS_PER_WAKE) {
                long next = simulationTime + NetworkConstants.INTERACTION_STEP_NANOS;
                manager.update(simulationTime, next);
                simulationTime = next;
                accumulator -= NetworkConstants.INTERACTION_STEP_NANOS;
                steps++;
            }

            if (accumulator < NetworkConstants.INTERACTION_STEP_NANOS) {
                long remaining = NetworkConstants.INTERACTION_STEP_NANOS - accumulator;
                LockSupport.parkNanos(Math.min(remaining, 1_000_000L));
            } else {
                Thread.onSpinWait();
            }
        }
    }

    @Override
    public void close() {
        running = false;
        Thread active = thread;
        thread = null;
        if (active != null) {
            active.interrupt();
        }
    }
}
