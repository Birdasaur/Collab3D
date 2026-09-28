package com.example.collab3d.client;

import com.example.collab3d.common.Pose3d;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;

/**
 * Owns construction and rendering state for the client's 3D world viewport.
 *
 * <p>This class is deliberately concerned only with JavaFX scene composition.
 * Networking, remote-pose resolution, interaction state, and application
 * lifecycle remain outside this class.</p>
 */
final class ClientWorldView {

    private static final double INITIAL_WIDTH = 1000.0;
    private static final double INITIAL_HEIGHT = 760.0;

    private final Group cameraRig = new Group();
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final SubScene subScene;
    private final StackPane viewport;

    @SuppressWarnings("FieldCanBeLocal")
    private final CameraController cameraController;

    ClientWorldView(
            CameraInputState cameraInputState,
            Node remoteParticipantLayer,
            Node participantLabelOverlay,
            Node scoreboardOverlay,
            Node interactionLayer,
            Pose3d initialCameraPose) {

        Group sceneRoot3d = create3dRoot(
                remoteParticipantLayer,
                interactionLayer);

        cameraRig.getChildren().add(camera);
        sceneRoot3d.getChildren().add(cameraRig);

        camera.setNearClip(0.05);
        camera.setFarClip(2_000.0);
        camera.setFieldOfView(55.0);

        applyLocalPose(initialCameraPose);

        subScene = new SubScene(
                sceneRoot3d,
                INITIAL_WIDTH,
                INITIAL_HEIGHT,
                true,
                SceneAntialiasing.BALANCED);

        subScene.setFill(Color.rgb(10, 14, 22));
        subScene.setCamera(camera);

        cameraController = new CameraController(
                cameraInputState,
                subScene);

        /*
         * Keep participant labels and the scoreboard in one logical 2D overlay
         * layer while preserving their independent implementation classes.
         */
        StackPane overlayRoot = new StackPane(
                participantLabelOverlay,
                scoreboardOverlay);
        overlayRoot.setMouseTransparent(true);
        overlayRoot.setPickOnBounds(false);
        StackPane.setAlignment(scoreboardOverlay, Pos.TOP_RIGHT);
        StackPane.setMargin(scoreboardOverlay, new Insets(12.0));

        viewport = new StackPane(
                subScene,
                overlayRoot);

        subScene.widthProperty().bind(viewport.widthProperty());
        subScene.heightProperty().bind(viewport.heightProperty());
    }

    Node node() {
        return viewport;
    }

    SubScene subScene() {
        return subScene;
    }

    void applyLocalPose(Pose3d pose) {
        if (pose == null) {
            return;
        }

        cameraRig.getTransforms().setAll(
                FxPoseCodec.toAffine(pose));
    }

    private static Group create3dRoot(
            Node remoteParticipantLayer,
            Node interactionLayer) {

        Group root = new Group();
        root.getChildren().add(createGrid());
        root.getChildren().add(remoteParticipantLayer);
        root.getChildren().add(interactionLayer);

        PointLight pointLight = new PointLight(Color.WHITE);
        pointLight.setTranslateX(-20.0);
        pointLight.setTranslateY(-30.0);
        pointLight.setTranslateZ(-30.0);
        root.getChildren().add(pointLight);

        return root;
    }

    private static Group createGrid() {
        Group grid = new Group();

        PhongMaterial minorMaterial = new PhongMaterial(
                Color.rgb(42, 50, 66),
                null,
                null,
                null,
                null);

        PhongMaterial majorMaterial = new PhongMaterial(
                Color.rgb(72, 85, 110),
                null,
                null,
                null,
                null);

        int halfExtent = 50;
        int spacing = 5;

        for (int coordinate = -halfExtent;
                coordinate <= halfExtent;
                coordinate += spacing) {

            boolean major = coordinate == 0;
            PhongMaterial material = major ? majorMaterial : minorMaterial;

            Box lineX = new Box(
                    halfExtent * 2.0,
                    0.025,
                    major ? 0.09 : 0.04);
            lineX.setTranslateX(0.0);
            lineX.setTranslateY(5.0);
            lineX.setTranslateZ(coordinate);
            lineX.setMaterial(material);

            Box lineZ = new Box(
                    major ? 0.09 : 0.04,
                    0.025,
                    halfExtent * 2.0);
            lineZ.setTranslateX(coordinate);
            lineZ.setTranslateY(5.0);
            lineZ.setTranslateZ(0.0);
            lineZ.setMaterial(material);

            grid.getChildren().addAll(lineX, lineZ);
        }

        Box originMarker = new Box(1.0, 1.0, 1.0);
        originMarker.setTranslateY(4.5);
        originMarker.setMaterial(new PhongMaterial(
                Color.ORANGE,
                null,
                null,
                null,
                null));
        grid.getChildren().add(originMarker);

        return grid;
    }
}
