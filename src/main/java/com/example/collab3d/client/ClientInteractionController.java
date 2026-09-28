package com.example.collab3d.client;

import com.example.collab3d.client.events.GameEventBus;
import com.example.collab3d.client.events.SfxEvent;
import com.example.collab3d.common.NetworkConstants;
import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.Quaterniond;
import com.example.collab3d.common.interactions.InteractionCollision;
import com.example.collab3d.common.interactions.SharedInteraction;
import java.util.Objects;
import java.util.function.LongFunction;
import javafx.event.EventHandler;
import javafx.scene.SubScene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;

/**
 * Client-side controller for user interaction input and visible interaction
 * rendering.
 *
 * <p>The controller currently exposes the synchronized tracer projectile as
 * the first interaction type. Interaction transport and authoritative
 * simulation remain in the networking/server layers.</p>
 */
final class ClientInteractionController {

    private final SubScene subScene;
    private final CameraMotionModel cameraMotionModel;
    private final CollaborationNetworkClient networkClient;
    private final TracerRoundManager tracerRoundManager;
    private final LongFunction<Color> actorColorProvider;

    /*
     * Stored so uninstall() can remove the exact handler instance install()
     * registered. A fresh method reference is not equal to the one already
     * added, so subScene.removeEventHandler needs this specific instance.
     */
    private final EventHandler<KeyEvent> keyPressedHandler = this::handleKeyPressed;

    /*
     * Seeded one full interval in the past (rather than Long.MIN_VALUE) so
     * the first shot is never blocked and the subtraction below can never
     * overflow: System.nanoTime() has no defined origin and can itself be
     * negative, so subtracting a sentinel like Long.MIN_VALUE from it is not
     * safe.
     */
    private long lastTracerFireNanos =
            System.nanoTime() - NetworkConstants.TRACER_FIRE_MIN_INTERVAL_NANOS;

    ClientInteractionController(
            SubScene subScene,
            CameraMotionModel cameraMotionModel,
            CollaborationNetworkClient networkClient,
            TracerRoundManager tracerRoundManager,
            LongFunction<Color> actorColorProvider) {

        this.subScene = Objects.requireNonNull(
                subScene,
                "subScene");

        this.cameraMotionModel = Objects.requireNonNull(
                cameraMotionModel,
                "cameraMotionModel");

        this.networkClient = Objects.requireNonNull(
                networkClient,
                "networkClient");

        this.tracerRoundManager = Objects.requireNonNull(
                tracerRoundManager,
                "tracerRoundManager");

        this.actorColorProvider = Objects.requireNonNull(
                actorColorProvider,
                "actorColorProvider");
    }

    /**
     * Installs interaction input handlers on the 3D viewport.
     */
    void install() {
        subScene.addEventHandler(
                KeyEvent.KEY_PRESSED,
                keyPressedHandler);
    }

    /**
     * Removes the input handlers installed by {@link #install()}.
     *
     * <p>Required before this controller is discarded on disconnect;
     * otherwise a reconnect's new controller would leave this one's handler
     * still attached to the (shared, reused) subScene, firing tracer sends
     * against a closed network client.</p>
     */
    void uninstall() {
        subScene.removeEventHandler(
                KeyEvent.KEY_PRESSED,
                keyPressedHandler);
    }

    /**
     * Advances client-side interaction visuals to the supplied server time.
     */
    void update(long serverNowNanos) {
        tracerRoundManager.update(serverNowNanos);
    }

    /**
     * Applies an authoritative or reconciled interaction update received from
     * the server.
     */
    void interactionUpdated(
            SharedInteraction interaction) {

        tracerRoundManager.show(
                interaction,
                actorColorProvider.apply(
                        interaction.actorObjectId()));
        GameEventBus.fire(new SfxEvent(SfxEvent.PLAY_SFX, "pew"));
    }

    /**
     * Applies an authoritative collision event received from the server.
     */
    void interactionCollision(
            InteractionCollision collision) {

        tracerRoundManager.collide(collision);
        GameEventBus.fire(new SfxEvent(SfxEvent.PLAY_SFX, "powf"));
    }

    /**
     * Removes any interaction visuals associated with a disconnected actor.
     */
    void removeActor(long objectId) {
        tracerRoundManager.removeActor(objectId);
    }

    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.SPACE) {
            fireTracerRound();
        }

        subScene.requestFocus();
    }

    private void fireTracerRound() {
        long now = System.nanoTime();
        if (now - lastTracerFireNanos
                < NetworkConstants.TRACER_FIRE_MIN_INTERVAL_NANOS) {
            return;
        }
        lastTracerFireNanos = now;

        Pose3d pose =
                cameraMotionModel.latestPose();

        if (pose == null) {
            return;
        }

        double[] forward =
                forwardVector(
                        pose.orientation());

        SharedInteraction provisional =
                networkClient.sendTracerProjectile(
                        pose.x(),
                        pose.y(),
                        pose.z(),
                        forward[0],
                        forward[1],
                        forward[2]);

        /*
         * Render immediately for local responsiveness. The authoritative
         * server broadcast carries the same actor/sequence key and reconciles
         * this view rather than creating a duplicate tracer.
         */
        if (provisional != null) {
            tracerRoundManager.show(
                    provisional,
                    actorColorProvider.apply(
                            provisional.actorObjectId()));
        }
        GameEventBus.fire(new SfxEvent(SfxEvent.PLAY_SFX, "pew"));        
    }

    /**
     * Rotates JavaFX camera-local +Z by the normalized camera quaternion.
     */
    private static double[] forwardVector(
            Quaterniond orientation) {

        Quaterniond q = orientation == null
                ? Quaterniond.identity()
                : orientation.normalized();

        double dx =
                2.0 * (q.x() * q.z() + q.w() * q.y());

        double dy =
                2.0 * (q.y() * q.z() - q.w() * q.x());

        double dz =
                1.0
                        - 2.0
                        * (q.x() * q.x()
                                + q.y() * q.y());

        double length =
                Math.sqrt(
                        dx * dx
                                + dy * dy
                                + dz * dz);

        if (!Double.isFinite(length)
                || length < 1.0e-9) {

            return new double[]{
                0.0,
                0.0,
                1.0
            };
        }

        return new double[]{
            dx / length,
            dy / length,
            dz / length
        };
    }
}