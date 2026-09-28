package com.example.collab3d.simethereal;

import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.Quaterniond;
import com.jme3.network.Client;
import com.simsilica.ethereal.EtherealClient;
import com.simsilica.ethereal.SharedObject;
import com.simsilica.ethereal.SharedObjectListener;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Typed adapter around the SimEthereal client-side service.
 *
 * <p>The important timing contract for remote interpolation is that every pose
 * emitted from {@link SharedObjectListener#objectUpdated(SharedObject)} is stamped
 * with the current SimEthereal frame time received by
 * {@link SharedObjectListener#beginFrame(long)}. That timestamp is the shared
 * state/server-time basis used by the remote history buffer.</p>
 */
public final class SimEtherealClientAdapter {

    private final EtherealClient etherealClient;
    private final SharedObjectListener listener;

    private SimEtherealClientAdapter(
            EtherealClient etherealClient,
            SharedObjectListener listener) {
        this.etherealClient = Objects.requireNonNull(
                etherealClient,
                "etherealClient");
        this.listener = Objects.requireNonNull(
                listener,
                "listener");
    }

    public static SimEtherealClientAdapter install(
            Client client,
            Consumer<RemotePoseUpdate> poseConsumer,
            LongConsumer removalConsumer,
            Runnable frameConsumer,
            Consumer<String> diagnosticConsumer) {

        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(poseConsumer, "poseConsumer");
        Objects.requireNonNull(removalConsumer, "removalConsumer");
        Objects.requireNonNull(frameConsumer, "frameConsumer");
        Objects.requireNonNull(diagnosticConsumer, "diagnosticConsumer");

        SimEtherealConfig config = SimEtherealConfig.create();

        EtherealClient etherealClient = new EtherealClient(
                config.objectProtocol(),
                config.zoneGrid(),
                config.zoneRadius());

        SharedObjectListener listener = new SharedObjectListener() {

            private volatile long frameTimeNanos;

            @Override
            public void beginFrame(long time) {
                frameTimeNanos = time;
                frameConsumer.run();
            }

            @Override
            public void objectUpdated(SharedObject object) {
                if (object == null) {
                    return;
                }

                try {
                    Vec3d position = object.getWorldPosition();
                    Quatd rotation = object.getWorldRotation();

                    if (position == null || rotation == null) {
                        diagnosticConsumer.accept(
                                "SimEthereal returned an object without a pose: "
                                        + object.getEntityId());
                        return;
                    }

                    long sampleTimeNanos = frameTimeNanos;
                    if (sampleTimeNanos <= 0L) {
                        /*
                         * A valid beginFrame() should precede objectUpdated().
                         * Use local nanoTime only as a defensive startup fallback;
                         * buffered interpolation remains disabled until clock sync
                         * has an estimate, so this value should not drive the normal
                         * shared-timeline rendering path.
                         */
                        sampleTimeNanos = System.nanoTime();
                    }

                    Pose3d pose = new Pose3d(
                            position.x,
                            position.y,
                            position.z,
                            new Quaterniond(
                                    rotation.x,
                                    rotation.y,
                                    rotation.z,
                                    rotation.w).normalized(),
                            sampleTimeNanos);

                    poseConsumer.accept(new RemotePoseUpdate(
                            object.getEntityId(),
                            pose));

                } catch (RuntimeException exception) {
                    diagnosticConsumer.accept(
                            "Unable to decode SimEthereal object "
                                    + object.getEntityId()
                                    + ": "
                                    + exception.getMessage());
                }
            }

            @Override
            public void objectRemoved(SharedObject object) {
                if (object != null) {
                    removalConsumer.accept(object.getEntityId());
                }
            }

            @Override
            public void endFrame() {
                // No frame-level processing is required after object callbacks.
            }
        };

        /*
         * EtherealClient creates its SharedObjectSpace during service
         * initialization, so the service must be added before the listener.
         */
        client.getServices().addService(etherealClient);
        etherealClient.addObjectListener(listener);

        diagnosticConsumer.accept(
                "SimEthereal client service initialized and listener installed.");

        return new SimEtherealClientAdapter(
                etherealClient,
                listener);
    }

    public record RemotePoseUpdate(
            long objectId,
            Pose3d pose) {
    }

    EtherealClient etherealClient() {
        return etherealClient;
    }

    SharedObjectListener listener() {
        return listener;
    }
}
