package com.example.collab3d.client;

import javafx.scene.SubScene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import java.util.Objects;

/**
 * JavaFX input adapter for the local first-person camera.
 *
 * <p>This class no longer moves a Group or computes camera transforms. Its only
 * responsibility is to translate JavaFX keyboard/mouse events into the neutral,
 * thread-safe {@link CameraInputState}. Local motion is integrated by
 * {@link CameraMotionModel} on the dedicated high-frequency scheduler.</p>
 */
final class CameraController {

    private final CameraInputState inputState;
    private double previousMouseX;
    private double previousMouseY;

    CameraController(CameraInputState inputState, SubScene subScene) {
        this.inputState = Objects.requireNonNull(inputState, "inputState");
        Objects.requireNonNull(subScene, "subScene");

        subScene.setFocusTraversable(true);
        subScene.setOnMousePressed(this::mousePressed);
        subScene.setOnMouseDragged(this::mouseDragged);
        subScene.setOnMouseClicked(event -> subScene.requestFocus());
        subScene.setOnKeyPressed(event -> setKey(event.getCode(), true));
        subScene.setOnKeyReleased(event -> setKey(event.getCode(), false));
        subScene.focusedProperty().addListener((observable, oldValue, focused) -> {
            if (!focused) {
                // Prevent a key from remaining logically held when focus is lost
                // before JavaFX can deliver its corresponding release event.
                inputState.clearMovement();
            }
        });
    }

    private void setKey(KeyCode code, boolean pressed) {
        switch (code) {
            case W -> inputState.setForward(pressed);
            case S -> inputState.setBackward(pressed);
            case A -> inputState.setLeft(pressed);
            case D -> inputState.setRight(pressed);
            case E -> inputState.setUp(pressed);
            case Q -> inputState.setDown(pressed);
            case SHIFT -> inputState.setFast(pressed);
            default -> {
                // Other keys are not part of the camera control contract.
            }
        }
    }

    private void mousePressed(MouseEvent event) {
        previousMouseX = event.getSceneX();
        previousMouseY = event.getSceneY();
        ((SubScene) event.getSource()).requestFocus();
    }

    private void mouseDragged(MouseEvent event) {
        double deltaX = event.getSceneX() - previousMouseX;
        double deltaY = event.getSceneY() - previousMouseY;
        previousMouseX = event.getSceneX();
        previousMouseY = event.getSceneY();

        // Preserve every JavaFX-delivered mouse delta. The motion loop drains the
        // accumulated total at 120 Hz, independent of the JavaFX render pulse.
        inputState.addLookDelta(deltaX, deltaY);
    }
}
