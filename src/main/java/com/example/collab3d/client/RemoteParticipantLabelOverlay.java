package com.example.collab3d.client;

import com.example.collab3d.common.ParticipantInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

/**
 * Screen-space overlay for remote participant display-name labels.
 *
 * <p>Participant labels are ordinary JavaFX 2D controls rather than nodes
 * embedded in the 3D scene. Each render frame, the corresponding participant
 * view supplies a 3D anchor that JavaFX projects into screen coordinates.
 * Those coordinates are then converted into this overlay's local coordinate
 * system.</p>
 *
 * <p>This keeps labels readable regardless of participant orientation,
 * camera orientation, perspective scaling, or 3D depth-buffer behavior.</p>
 */
final class RemoteParticipantLabelOverlay {

    private static final double LABEL_OFFSET_Y = 12.0;

    private final Pane overlayPane = new Pane();
    private final Map<Long, Label> labels = new LinkedHashMap<>();

    RemoteParticipantLabelOverlay() {
        overlayPane.setMouseTransparent(true);
        overlayPane.setPickOnBounds(false);
    }

    /**
     * Returns the transparent pane that should be stacked above the 3D
     * SubScene.
     *
     * @return participant label overlay pane
     */
    Pane node() {
        return overlayPane;
    }

    /**
     * Adds a screen-space label for the supplied remote participant.
     *
     * <p>If a label already exists for the participant, its displayed
     * information is refreshed.</p>
     *
     * @param participant remote participant metadata
     */
    void addOrUpdateParticipant(ParticipantInfo participant) {
        Label existing = labels.get(participant.objectId());

        if (existing != null) {
            configureLabel(existing, participant);
            return;
        }

        Label label = new Label();
        configureLabel(label, participant);

        label.setManaged(false);
        label.setMouseTransparent(true);
        label.setVisible(false);

        labels.put(participant.objectId(), label);
        overlayPane.getChildren().add(label);
    }

    /**
     * Removes the label associated with a remote participant.
     *
     * @param objectId remote participant identifier
     */
    void removeParticipant(long objectId) {
        Label removed = labels.remove(objectId);

        if (removed != null) {
            overlayPane.getChildren().remove(removed);
        }
    }

    /**
     * Updates the screen position of a participant's label.
     *
     * <p>The participant's resolved render pose must already have been applied
     * before this method is called. This ensures that the label follows the
     * exact same interpolated/predicted pose that is visible in 3D.</p>
     *
     * @param objectId remote participant identifier
     * @param view rendered remote participant view
     */
    void update(long objectId, RemoteParticipantView view) {
        Label label = labels.get(objectId);

        if (label == null || view == null) {
            return;
        }

        /*
         * localToScreen() lets JavaFX perform the PerspectiveCamera/SubScene
         * projection. It returns physical screen coordinates for the supplied
         * point in the participant's local coordinate system.
         */
        Point2D screenPoint = view.node().localToScreen(
                view.labelAnchorLocal());

        if (screenPoint == null) {
            label.setVisible(false);
            return;
        }

        /*
         * Convert from desktop/screen coordinates back into this overlay's
         * local coordinate system.
         */
        Point2D overlayPoint = overlayPane.screenToLocal(screenPoint);

        if (overlayPoint == null
                || !Double.isFinite(overlayPoint.getX())
                || !Double.isFinite(overlayPoint.getY())) {

            label.setVisible(false);
            return;
        }

        double overlayWidth = overlayPane.getWidth();
        double overlayHeight = overlayPane.getHeight();

        /*
         * A projected anchor outside the viewport should not leave a floating
         * label around the edge of the UI.
         */
        if (overlayPoint.getX() < 0.0
                || overlayPoint.getX() > overlayWidth
                || overlayPoint.getY() < 0.0
                || overlayPoint.getY() > overlayHeight) {

            label.setVisible(false);
            return;
        }

        double labelWidth = label.prefWidth(-1.0);
        double labelHeight = label.prefHeight(labelWidth);

        label.resize(labelWidth, labelHeight);

        /*
         * Center horizontally over the 3D participant and hover a small,
         * constant number of screen pixels above the projected anchor.
         */
        label.relocate(
                overlayPoint.getX() - labelWidth * 0.5,
                overlayPoint.getY() - labelHeight - LABEL_OFFSET_Y);

        label.setVisible(true);
    }

    /**
     * Applies participant-specific presentation to a label.
     */
    private void configureLabel(
            Label label,
            ParticipantInfo participant) {

        Color participantColor = Color.hsb(
                participant.hueDegrees(),
                0.85,
                1.0);

        label.setText(participant.displayName());
        label.setTextFill(participantColor.brighter());
        label.setPadding(new Insets(2.0, 6.0, 2.0, 6.0));

        label.setBackground(
                new Background(
                        new BackgroundFill(
                                Color.rgb(8, 12, 20, 0.78),
                                new CornerRadii(4.0),
                                Insets.EMPTY)));
    }
}