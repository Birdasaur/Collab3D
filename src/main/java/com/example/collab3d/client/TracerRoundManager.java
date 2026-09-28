package com.example.collab3d.client;

import com.example.collab3d.common.NetworkConstants;
import com.example.collab3d.common.interactions.InteractionCollision;
import com.example.collab3d.common.interactions.InteractionType;
import com.example.collab3d.common.interactions.SharedInteraction;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** JavaFX renderer for deterministic synchronized tracer projectiles. */
final class TracerRoundManager {

    private static final double HEAD_RADIUS = 0.11;
    private static final double TRAIL_RADIUS = 0.035;
    private static final long IMPACT_FLASH_NANOS = 500_000_000L;

    private final Group layer = new Group();
    private final Map<TracerKey, TracerRoundView> active = new LinkedHashMap<>();

    Node node() {
        return layer;
    }

    /** Adds a provisional local shot or reconciles it with the server start. */
    void show(SharedInteraction interaction, Color color) {
        if (interaction == null
                || interaction.type() != InteractionType.TRACER_PROJECTILE) {
            return;
        }

        TracerKey key = new TracerKey(
                interaction.actorObjectId(),
                interaction.interactionSequence());
        TracerRoundView existing = active.get(key);
        if (existing != null) {
            existing.reconcile(interaction);
            return;
        }

        TracerRoundView created = new TracerRoundView(interaction, color);
        active.put(key, created);
        layer.getChildren().add(created.node());
    }

    /** Applies the authoritative collision event to the matching projectile. */
    void collide(InteractionCollision collision) {
        if (collision == null) {
            return;
        }
        TracerRoundView view = active.get(new TracerKey(
                collision.actorObjectId(),
                collision.clientSequence()));
        if (view != null) {
            view.collide(collision);
        }
    }

    void update(long serverNowNanos) {
        Iterator<Map.Entry<TracerKey, TracerRoundView>> iterator =
                active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TracerKey, TracerRoundView> entry = iterator.next();
            TracerRoundView view = entry.getValue();
            if (!view.update(serverNowNanos)) {
                layer.getChildren().remove(view.node());
                iterator.remove();
            }
        }
    }

    void removeActor(long actorObjectId) {
        Iterator<Map.Entry<TracerKey, TracerRoundView>> iterator =
                active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TracerKey, TracerRoundView> entry = iterator.next();
            if (entry.getKey().actorObjectId() == actorObjectId) {
                layer.getChildren().remove(entry.getValue().node());
                iterator.remove();
            }
        }
    }

    private record TracerKey(long actorObjectId, long interactionSequence) {
    }

    private static final class TracerRoundView {

        private final Group root = new Group();
        private final Cylinder trail;
        private final Sphere impactFlash;
        private SharedInteraction interaction;
        private InteractionCollision collision;

        TracerRoundView(SharedInteraction interaction, Color color) {
            this.interaction = interaction;
            Color tracerColor = color == null ? Color.CYAN : color;
            PhongMaterial material = new PhongMaterial(
                tracerColor, null, null, null, null
            );
            material.setSpecularColor(Color.WHITE);
            material.setSpecularPower(64.0);

            Sphere head = new Sphere(HEAD_RADIUS, 12);
            head.setMaterial(material);

            trail = new Cylinder(
                    TRAIL_RADIUS,
                    NetworkConstants.TRACER_ROUND_TRAIL_LENGTH,
                    10);
            trail.setMaterial(material);
            trail.setTranslateY(-NetworkConstants.TRACER_ROUND_TRAIL_LENGTH / 2.0);

            impactFlash = new Sphere(0.18, 12);
            impactFlash.setMaterial(new PhongMaterial(
                Color.ORANGE.deriveColor(1, 1, 1, 0.1), null, null, null, null
            ));
            impactFlash.setVisible(false);

            root.getChildren().addAll(trail, head, impactFlash);
            orientAlongDirection(interaction);
            root.setMouseTransparent(true);
        }

        Node node() {
            return root;
        }

        void reconcile(SharedInteraction authoritative) {
            interaction = authoritative;
            root.getTransforms().clear();
            orientAlongDirection(authoritative);
        }

        void collide(InteractionCollision authoritativeCollision) {
            collision = authoritativeCollision;
        }

        boolean update(long serverNowNanos) {
            if (collision != null
                    && serverNowNanos >= collision.eventServerTimeNanos()) {
                root.setTranslateX(collision.position().x());
                root.setTranslateY(collision.position().y());
                root.setTranslateZ(collision.position().z());
                trail.setVisible(false);
                impactFlash.setVisible(true);

                long impactAge = serverNowNanos - collision.eventServerTimeNanos();
                double amount = Math.min(1.0,
                        impactAge / (double) IMPACT_FLASH_NANOS);
                double scale = 1.0 + amount * 3.0;
                impactFlash.setScaleX(scale);
                impactFlash.setScaleY(scale);
                impactFlash.setScaleZ(scale);
                impactFlash.setOpacity(1.0 - amount);
                return impactAge <= IMPACT_FLASH_NANOS;
            }

            long ageNanos = Math.max(
                    0L,
                    serverNowNanos - interaction.eventServerTimeNanos());
            if (ageNanos > NetworkConstants.TRACER_ROUND_LIFETIME_NANOS) {
                return false;
            }

            double ageSeconds = ageNanos / 1_000_000_000.0;
            double distance = NetworkConstants.TRACER_ROUND_MUZZLE_OFFSET
                    + NetworkConstants.TRACER_ROUND_SPEED_UNITS_PER_SECOND
                    * ageSeconds;
            root.setTranslateX(
                    interaction.originX() + interaction.directionX() * distance);
            root.setTranslateY(
                    interaction.originY() + interaction.directionY() * distance);
            root.setTranslateZ(
                    interaction.originZ() + interaction.directionZ() * distance);
            return true;
        }

        private void orientAlongDirection(SharedInteraction value) {
            Point3D direction = new Point3D(
                    value.directionX(),
                    value.directionY(),
                    value.directionZ()).normalize();
            Point3D localYAxis = new Point3D(0.0, 1.0, 0.0);
            double dot = clamp(localYAxis.dotProduct(direction), -1.0, 1.0);
            double angleDegrees = Math.toDegrees(Math.acos(dot));
            Point3D axis = localYAxis.crossProduct(direction);

            if (axis.magnitude() < 1.0e-9) {
                if (dot < 0.0) {
                    root.getTransforms().add(new Rotate(180.0, Rotate.X_AXIS));
                }
                return;
            }
            root.getTransforms().add(new Rotate(angleDegrees, axis.normalize()));
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
