package com.example.collab3d.client;

import com.example.collab3d.common.ParticipantInfo;
import com.example.collab3d.common.Pose3d;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;

/**
 * JavaFX 3D visualization of a remote collaboration participant.
 *
 * <p>This class intentionally performs no prediction, interpolation, or
 * reconciliation. Those responsibilities belong to
 * {@link RemotePoseStore} and {@link RemoteParticipantMotionState}.</p>
 *
 * <p>The view therefore receives a fully resolved render pose and simply maps
 * that pose onto the JavaFX scene graph. Keeping the renderer unaware of
 * networking behavior preserves a clean boundary between the motion/network
 * model and JavaFX rendering.</p>
 *
 * <p>The participant display-name label is intentionally not part of this
 * 3D scene graph. Screen-space participant labels are managed separately by
 * {@link RemoteParticipantLabelOverlay}.</p>
 */
final class RemoteParticipantView {

    private static final Point3D LABEL_ANCHOR =
            new Point3D(0.0, 0.0, 0.0);

    private final ParticipantInfo info;
    private final Group root = new Group();

    RemoteParticipantView(ParticipantInfo info) {
        this.info = info;

        Color color = Color.hsb(
                info.hueDegrees(),
                0.85,
                1.0);

        PhongMaterial material = new PhongMaterial(
                color,
                null,
                null,
                null,
                null);

        /*
         * Small sphere representing the physical location of the remote
         * participant's camera.
         */
        Sphere cameraBody = new Sphere(0.28, 18);
        cameraBody.setMaterial(material);

        /*
         * Direction shaft extends along the participant's local +Z axis.
         * The full root Group receives the participant quaternion transform,
         * causing the shaft and arrowhead to rotate with the remote camera.
         */
        Cylinder directionShaft = new Cylinder(
                0.10,
                3.0,
                14);

        directionShaft.setMaterial(material);
        directionShaft.setTranslateZ(1.5);
        directionShaft.getTransforms().add(
                new Rotate(
                        90.0,
                        Rotate.X_AXIS));

        MeshView arrowHead = new MeshView(createTetrahedron());
        arrowHead.setMaterial(material);
        arrowHead.setCullFace(CullFace.NONE);

        /*
         * Only physical 3D participant geometry belongs in this group.
         * The display name is rendered separately in a 2D overlay.
         */
        root.getChildren().addAll(
                cameraBody,
                directionShaft,
                arrowHead);

        /*
         * Remote participant representations are informational only and
         * should not interfere with picking/input directed at the 3D scene.
         */
        root.setMouseTransparent(true);
    }

    Node node() {
        return root;
    }

    ParticipantInfo info() {
        return info;
    }

    /**
     * Returns the participant-local point that should be projected into
     * screen space for positioning the associated 2D label.
     *
     * <p>The participant origin is used deliberately. The visible label is
     * offset upward in screen space by the label overlay. This prevents
     * participant pitch, yaw, or roll from rotating the label offset around
     * the participant.</p>
     *
     * @return participant-local label anchor
     */
    Point3D labelAnchorLocal() {
        return LABEL_ANCHOR;
    }

    /**
     * Applies the already-resolved render pose to this JavaFX representation.
     *
     * <p>No smoothing is performed here. The supplied pose has already passed
     * through prediction/reconciliation if that feature is enabled.</p>
     *
     * @param pose pose that should be displayed for this render frame
     */
    void applyPose(Pose3d pose) {
        if (pose == null) {
            return;
        }

        root.getTransforms().setAll(
                FxPoseCodec.toAffine(pose));
    }

    /**
     * Creates a small tetrahedral arrowhead pointing along local +Z.
     *
     * @return arrowhead mesh
     */
    private static TriangleMesh createTetrahedron() {
        TriangleMesh mesh = new TriangleMesh();

        mesh.getPoints().addAll(
                0.0f, 0.0f, 4.2f,
                -0.75f, -0.60f, 3.0f,
                0.75f, -0.60f, 3.0f,
                0.0f, 0.82f, 3.0f);

        mesh.getTexCoords().addAll(
                0.0f,
                0.0f);

        mesh.getFaces().addAll(
                0, 0, 1, 0, 2, 0,
                0, 0, 2, 0, 3, 0,
                0, 0, 3, 0, 1, 0,
                1, 0, 3, 0, 2, 0);

        return mesh;
    }
}