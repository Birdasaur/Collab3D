package com.example.collab3d.client;

import com.example.collab3d.common.NetworkRuntimeStats;
import com.example.collab3d.common.ParticipantInfo;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Sidebar containing client connection state, runtime diagnostics, participant
 * information, and render controls.
 *
 * <p>This class owns presentation only. It does not query networking or motion
 * services directly.</p>
 */
final class ClientStatusPane extends VBox {

    /**
     * Callback for the Connect/Disconnect button. This pane owns presentation
     * only, so it hands raw field text back to the caller rather than parsing
     * or validating ports itself.
     */
    interface ConnectionRequestListener {
        void onConnectRequested(
                String host,
                String tcpPortText,
                String udpPortText,
                String displayName);

        void onDisconnectRequested();
    }

    private final ObservableList<String> participantItems =
            FXCollections.observableArrayList();

    private final TextField nameField = new TextField();
    private final TextField hostField = new TextField();
    private final TextField tcpPortField = new TextField();
    private final TextField udpPortField = new TextField();
    private final Button connectButton = new Button();

    private boolean sessionActive;

    private final Label statusLabel =
            new Label("Not connected");

    private final Label identityLabel =
            new Label("Object id: pending");

    private final Label updateCountLabel =
            new Label("Pose updates: 0");

    private final Label runtimeStatsLabel =
            new Label("Runtime rates: collecting...");

    private final Label clockSyncLabel =
            new Label("Clock sync: collecting...");

    private final CheckBox smoothingCheckBox =
            new CheckBox("Buffered interpolation + prediction");

    private final TextArea diagnostics =
            new TextArea();

    ClientStatusPane(
            String displayName,
            ClientConnectionConfig connectionConfig,
            ConnectionRequestListener listener) {

        super(8.0);

        Objects.requireNonNull(listener, "listener");

        Label title = new Label("Collaboration Test");

        nameField.setText(displayName);
        nameField.setPromptText("Display name");

        hostField.setText(connectionConfig.host());
        hostField.setPromptText("Host");

        tcpPortField.setText(Integer.toString(connectionConfig.tcpPort()));
        tcpPortField.setPromptText("TCP port");
        tcpPortField.setPrefWidth(80.0);

        udpPortField.setText(Integer.toString(connectionConfig.udpPort()));
        udpPortField.setPromptText("UDP port");
        udpPortField.setPrefWidth(80.0);

        HBox portRow = new HBox(6.0, tcpPortField, udpPortField);

        connectButton.setMaxWidth(Double.MAX_VALUE);
        connectButton.setOnAction(event -> {
            if (sessionActive) {
                listener.onDisconnectRequested();
            } else {
                listener.onConnectRequested(
                        hostField.getText(),
                        tcpPortField.getText(),
                        udpPortField.getText(),
                        nameField.getText());
            }
        });

        Label controls = new Label(
                "Controls\n"
                        + "Mouse drag: look\n"
                        + "W/A/S/D: move\n"
                        + "Q/E: down/up\n"
                        + "Shift: faster\n"
                        + "Spacebar: fire synchronized tracer\n"
                        + "Click viewport to focus");

        controls.setWrapText(true);

        smoothingCheckBox.setSelected(true);

        ListView<String> participantList =
                new ListView<>(participantItems);

        participantList.setPrefHeight(180.0);

        diagnostics.setEditable(false);
        diagnostics.setWrapText(true);
        diagnostics.setPrefRowCount(8);

        getChildren().addAll(
                title,
                nameField,
                hostField,
                portRow,
                connectButton,
                statusLabel,
                identityLabel,
                updateCountLabel,
                runtimeStatsLabel,
                clockSyncLabel,
                smoothingCheckBox,
                new Separator(),
                new Label("Participants"),
                participantList,
                new Separator(),
                controls,
                new Separator(),
                new Label("Diagnostics"),
                diagnostics);

        setAlignment(Pos.TOP_LEFT);
        setPadding(new Insets(12.0));
        setPrefWidth(280.0);
        setMinWidth(250.0);

        /*
         * ClientMain starts an initial connection attempt immediately after
         * constructing this pane, so it opens already reflecting an active
         * session rather than momentarily showing "Connect".
         */
        setSessionActive(true);
    }

    boolean bufferedRemoteMotionEnabled() {
        return smoothingCheckBox.isSelected();
    }

    /**
     * Reflects whether a connection session is currently active (connecting,
     * connected, or mid-teardown). Disables the editable fields and swaps the
     * button label so the fields always describe the session that would
     * result from pressing Connect.
     */
    void setSessionActive(boolean active) {
        sessionActive = active;
        connectButton.setText(active ? "Disconnect" : "Connect");
        nameField.setDisable(active);
        hostField.setDisable(active);
        tcpPortField.setDisable(active);
        udpPortField.setDisable(active);
    }

    void resetIdentity() {
        identityLabel.setText("Object id: pending");
    }

    void setConnectionStatus(String text) {
        statusLabel.setText(text);
    }

    void setIdentity(long objectId) {
        identityLabel.setText(
                "Object id: " + objectId);
    }

    void setPoseUpdateCount(long updateCount) {
        updateCountLabel.setText(
                "Pose updates: " + updateCount);
    }

    void setParticipants(
            Collection<ParticipantInfo> participants,
            long localObjectId) {

        participantItems.setAll(
                participants.stream()
                        .map(participant ->
                                participant.displayName()
                                        + " ["
                                        + participant.objectId()
                                        + "]"
                                        + (participant.objectId()
                                                == localObjectId
                                                ? " (local)"
                                                : ""))
                        .toList());
    }

    void updateRuntimeStats(
            NetworkRuntimeStats.Window rates,
            NetworkRuntimeStats.Snapshot totals) {

        if (rates != null) {
            runtimeStatsLabel.setText(
                    String.format(
                            Locale.ROOT,
                            "Motion %.1f | Send %.1f | Sim %.1f | Render %.1f Hz | "
                                    + "Remote I %.1f / E %.1f / H %.1f | "
                                    + "Buf %.1f ms / Jit %.1f ms",
                            rates.clientMotionHz(),
                            rates.clientPoseSendHz(),
                            rates.clientSimEtherealFrameHz(),
                            rates.clientRenderHz(),
                            rates.clientRemoteInterpolationHz(),
                            rates.clientRemoteExtrapolationHz(),
                            rates.clientRemoteHoldHz(),
                            totals == null
                                    ? 0.0
                                    : totals.clientRemoteInterpolationDelayNanos()
                                            / 1_000_000.0,
                            totals == null
                                    ? 0.0
                                    : totals.clientRemoteJitterNanos()
                                            / 1_000_000.0));
        }

        if (totals != null
                && totals.clientTimeSyncSamples() > 0L) {

            clockSyncLabel.setText(
                    String.format(
                            Locale.ROOT,
                            "Clock sync: RTT %.3f ms | Offset %+.3f ms | Samples %d",
                            totals.clientEstimatedRttNanos()
                                    / 1_000_000.0,
                            totals.clientEstimatedClockOffsetNanos()
                                    / 1_000_000.0,
                            totals.clientTimeSyncSamples()));
        }
    }

    void appendDiagnostic(String text) {
        diagnostics.appendText(
                text + System.lineSeparator());

        diagnostics.positionCaret(
                diagnostics.getLength());
    }
}