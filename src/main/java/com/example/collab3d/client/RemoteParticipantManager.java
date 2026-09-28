package com.example.collab3d.client;

import com.example.collab3d.common.ParticipantInfo;
import com.example.collab3d.common.Pose3d;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

/**
 * Owns the client-side visual representation of all remote participants.
 *
 * <p>This class coordinates the remote participant motion store, 3D
 * participant views, and their screen-space labels. ClientMain therefore only
 * needs to notify this manager when participants join or leave and invoke one
 * update operation per JavaFX render frame.</p>
 */
final class RemoteParticipantManager {

    private final RemotePoseStore poseStore;
    private final Group remoteLayer = new Group();
    private final RemoteParticipantLabelOverlay labelOverlay =
            new RemoteParticipantLabelOverlay();

    private final Map<Long, RemoteParticipantView> remoteViews =
            new LinkedHashMap<>();

    RemoteParticipantManager(RemotePoseStore poseStore) {
        this.poseStore = poseStore;

        /*
         * Participant geometry is informational and must not intercept input
         * intended for the 3D viewport.
         */
        remoteLayer.setMouseTransparent(true);
    }

    /**
     * Returns the 3D node containing all remote participant geometry.
     *
     * @return remote participant 3D layer
     */
    Node node() {
        return remoteLayer;
    }

    /**
     * Returns the transparent 2D overlay containing participant labels.
     *
     * @return participant label overlay
     */
    Pane overlayNode() {
        return labelOverlay.node();
    }

    /**
     * Adds a remote participant if it is not already represented.
     *
     * @param participant participant metadata
     */
    void addOrUpdateParticipant(ParticipantInfo participant) {
        RemoteParticipantView view =
                remoteViews.get(participant.objectId());

        if (view == null) {
            view = new RemoteParticipantView(participant);
            remoteViews.put(participant.objectId(), view);
            remoteLayer.getChildren().add(view.node());
        }

        labelOverlay.addOrUpdateParticipant(participant);
    }

    /**
     * Removes all client-side render state associated with a participant.
     *
     * @param objectId participant identifier
     */
    void removeParticipant(long objectId) {
        poseStore.remove(objectId);

        RemoteParticipantView removed =
                remoteViews.remove(objectId);

        if (removed != null) {
            remoteLayer.getChildren().remove(removed.node());
        }

        labelOverlay.removeParticipant(objectId);
    }

    /**
     * Updates every remote participant for the current JavaFX render frame.
     *
     * <p>The pose store determines the resolved render pose. This manager then
     * applies that pose to the 3D representation and immediately reprojects
     * the corresponding screen-space label.</p>
     *
     * @param serverNowNanos current time expressed in server time
     * @param bufferedRemoteMotion true to use buffered interpolation/prediction
     */
    void update(
            long serverNowNanos,
            boolean bufferedRemoteMotion) {

        for (Map.Entry<Long, RemoteParticipantView> entry
                : remoteViews.entrySet()) {

            long objectId = entry.getKey();
            RemoteParticipantView view = entry.getValue();

            Pose3d renderPose = poseStore.getRenderPose(
                    objectId,
                    serverNowNanos,
                    bufferedRemoteMotion);

            view.applyPose(renderPose);
            labelOverlay.update(objectId, view);
        }
    }
}