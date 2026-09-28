package com.example.collab3d.client;

import com.example.collab3d.client.audio.JukeBox;
import com.example.collab3d.client.audio.MusicDirector;
import com.example.collab3d.client.audio.SfxPlayer;
import com.example.collab3d.client.css.StyleResourceProvider;
import com.example.collab3d.client.events.AudioEvent;
import com.example.collab3d.client.events.GameEventBus;
import com.example.collab3d.client.events.SfxEvent;
import com.example.collab3d.common.NetworkRuntimeStats;
import com.example.collab3d.common.NetworkStatsLogger;
import com.example.collab3d.common.ParticipantInfo;
import com.example.collab3d.common.PropertiesFileLoader;
import com.example.collab3d.common.ParticipantResultState;
import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.Quaterniond;
import com.example.collab3d.common.interactions.InteractionCollision;
import com.example.collab3d.common.interactions.SharedInteraction;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX collaboration client.
 *
 * <p>This class acts primarily as the client application's composition root.
 * Detailed scene construction, participant rendering, result presentation,
 * status presentation, and interaction input are delegated to focused
 * collaborators.</p>
 */
public final class ClientMain extends Application {

    private static final Logger LOG =
            LoggerFactory.getLogger(ClientMain.class);

    private final NetworkRuntimeStats runtimeStats =
            new NetworkRuntimeStats();

    private final NetworkStatsLogger statsLogger =
            new NetworkStatsLogger(
                    runtimeStats,
                    LOG,
                    NetworkStatsLogger.Role.CLIENT);

    private final RemotePoseStore poseStore =
            new RemotePoseStore(runtimeStats);

    private final SharedInteractionStore interactionStore =
            new SharedInteractionStore();

    private final ParticipantResultStore resultStore =
            new ParticipantResultStore();

    private final TracerRoundManager tracerRoundManager =
            new TracerRoundManager();

    private final RemoteParticipantManager participantManager =
            new RemoteParticipantManager(poseStore);

    private final ClientScoreboardOverlay scoreboardOverlay =
            new ClientScoreboardOverlay(resultStore);

    private final CameraInputState cameraInputState =
            new CameraInputState();

    private final Map<Long, ParticipantInfo> participants =
            new LinkedHashMap<>();

    private CollaborationNetworkClient networkClient;
    private LocalPosePublisher localPosePublisher;
    private CameraMotionModel cameraMotionModel;
    private ClientWorldView worldView;
    private ClientStatusPane statusPane;
    private ClientInteractionController interactionController;
    private AnimationTimer animationTimer;

    private long lastStatsUpdateNanos;
    private long localObjectId = -1L;
    private String displayName;
    private boolean timeSyncDiagnosticsEnabled;

    /*
     * Incremented on every connectSession() call. Captured by each session's
     * network listener so a disconnect notification arriving after that
     * session has already been superseded (e.g. the old connection's async
     * teardown completing after the user reconnected) can recognize itself
     * as stale and do nothing, rather than tearing down the new session.
     */
    private long sessionToken;

    private SfxPlayer sfx;
    private JukeBox jukeBox;
    
    @Override
    public void start(Stage stage) {
        sfx = new SfxPlayer();
        GameEventBus.addHandler(SfxEvent.ANY, sfx);

        MusicDirector md = new MusicDirector();
        md.register();

        jukeBox = new JukeBox();
        GameEventBus.addHandler(AudioEvent.ANY, jukeBox);        
        
        /*
         * client.properties supplies defaults; any matching command-line
         * named parameter overrides it. Both use the same key names
         * (host, tcpPort, udpPort, name, timeSyncDiagnostics).
         */
        Map<String, String> parameters =
                new LinkedHashMap<>(
                        PropertiesFileLoader.load("client.properties"));

        parameters.putAll(getParameters().getNamed());

        displayName = requestedName(parameters);

        ClientConnectionConfig connectionConfig =
                ClientConnectionConfig.from(parameters);

        timeSyncDiagnosticsEnabled =
                booleanNamedParameter(
                        parameters,
                        "timeSyncDiagnostics",
                        false);

        Pose3d initialCameraPose = new Pose3d(
                0.0,
                -5.0,
                -20.0,
                Quaterniond.identity(),
                System.nanoTime());

        cameraMotionModel =
                new CameraMotionModel(initialCameraPose);

        worldView = new ClientWorldView(
                cameraInputState,
                participantManager.node(),
                participantManager.overlayNode(),
                scoreboardOverlay.node(),
                tracerRoundManager.node(),
                initialCameraPose);

        statusPane = new ClientStatusPane(
                displayName,
                connectionConfig,
                createConnectionRequestListener());

        BorderPane layout = new BorderPane();
        layout.setCenter(worldView.node());
        layout.setRight(statusPane);
        layout.setBackground(new Background(
            new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
        Scene scene = new Scene(
                layout,
                1280.0,
                760.0,
                true);
        scene.setFill(Color.BLACK);
        String css = StyleResourceProvider.getResource("styles.css").toExternalForm();
        scene.getStylesheets().add(css);
        
        stage.setTitle(
                "Collaborative 3D Camera Test - "
                        + displayName
                        + " @ "
                        + connectionConfig.host());
        
        stage.setScene(scene);
        stage.show();

        Platform.runLater(
                () -> worldView.subScene().requestFocus());

        statsLogger.start();

        connectSession(connectionConfig, displayName);

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateFrame(now);
            }
        };

        animationTimer.start();
    }

    @Override
    public void stop() {
        if (animationTimer != null) {
            animationTimer.stop();
        }

        stopSessionIfActive();
        statsLogger.close();
    }

    /**
     * Tears down any existing session, then constructs and starts a new one
     * against {@code config}. Called both for the initial launch-time
     * connection and for a user-initiated reconnect from the status pane.
     */
    private void connectSession(
            ClientConnectionConfig config,
            String newDisplayName) {

        stopSessionIfActive();

        displayName = newDisplayName;
        statusPane.setSessionActive(true);

        long thisSessionToken = ++sessionToken;

        networkClient = new CollaborationNetworkClient(
                config,
                displayName,
                poseStore,
                interactionStore,
                resultStore,
                runtimeStats,
                timeSyncDiagnosticsEnabled,
                createNetworkListener(thisSessionToken));

        interactionController =
                new ClientInteractionController(
                        worldView.subScene(),
                        cameraMotionModel,
                        networkClient,
                        tracerRoundManager,
                        this::colorForActor);

        interactionController.install();

        localPosePublisher = new LocalPosePublisher(
                cameraInputState,
                cameraMotionModel,
                networkClient,
                runtimeStats);

        localPosePublisher.start();

        Thread connectionThread = new Thread(
                () -> connectSafely(thisSessionToken),
                "collaboration-client-connect");

        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    /**
     * Closes the current session's networking/publishing objects (all
     * single-use by design; see {@link CollaborationNetworkClient} and
     * {@link LocalPosePublisher}) and clears every participant so a
     * reconnect, or app shutdown, starts from a clean slate. Safe to call
     * when no session is active.
     */
    private void stopSessionIfActive() {
        if (localPosePublisher != null) {
            localPosePublisher.close();
            localPosePublisher = null;
        }

        if (interactionController != null) {
            interactionController.uninstall();
            interactionController = null;
        }

        if (networkClient != null) {
            networkClient.close();
            networkClient = null;
        }

        for (long objectId : List.copyOf(participants.keySet())) {
            removeParticipant(objectId);
        }

        localObjectId = -1L;

        if (statusPane != null) {
            statusPane.resetIdentity();
            statusPane.setSessionActive(false);
        }
    }

    private ClientStatusPane.ConnectionRequestListener
            createConnectionRequestListener() {

        return new ClientStatusPane.ConnectionRequestListener() {

            @Override
            public void onConnectRequested(
                    String host,
                    String tcpPortText,
                    String udpPortText,
                    String requestedDisplayName) {

                try {
                    ClientConnectionConfig config =
                            new ClientConnectionConfig(
                                    host,
                                    Integer.parseInt(tcpPortText.strip()),
                                    Integer.parseInt(udpPortText.strip()));

                    String nextDisplayName =
                            requestedDisplayName == null
                                    || requestedDisplayName.isBlank()
                                    ? displayName
                                    : requestedDisplayName.strip();

                    connectSession(config, nextDisplayName);
                } catch (IllegalArgumentException exception) {

                    statusPane.appendDiagnostic(
                            "Invalid connection settings: "
                                    + exception.getMessage());
                }
            }

            @Override
            public void onDisconnectRequested() {
                stopSessionIfActive();
                statusPane.setConnectionStatus("Disconnected");
            }
        };
    }

    private CollaborationNetworkClient.Listener createNetworkListener(
            long token) {

        return new CollaborationNetworkClient.Listener() {

            @Override
            public void connectionStatus(String text) {
                Platform.runLater(
                        () -> statusPane.setConnectionStatus(text));
            }

            @Override
            public void identityAssigned(
                    long objectId,
                    String displayName) {

                Platform.runLater(() -> {
                    localObjectId = objectId;
                    statusPane.setIdentity(objectId);

                    /*
                     * Participant metadata can arrive before identity
                     * assignment. Remove any accidentally-created remote view
                     * once this object is known to be the local participant.
                     */
                    participantManager.removeParticipant(objectId);
                    refreshParticipantList();
                });
            }

            @Override
            public void participantAdded(
                    ParticipantInfo participant) {

                Platform.runLater(
                        () -> addOrUpdateParticipant(participant));
            }

            @Override
            public void participantRemoved(long objectId) {
                Platform.runLater(
                        () -> removeParticipant(objectId));
            }

            @Override
            public void participantResultUpdated(
                    ParticipantResultState state) {

                Platform.runLater(
                        () -> scoreboardOverlay.resultStateUpdated(state));
            }

            @Override
            public void interactionUpdated(
                    SharedInteraction interaction) {

                Platform.runLater(() -> {
                    if (interactionController != null) {
                        interactionController
                                .interactionUpdated(interaction);
                    }
                });
            }

            @Override
            public void interactionCollision(
                    InteractionCollision collision) {

                Platform.runLater(() -> {
                    if (interactionController != null) {
                        interactionController
                                .interactionCollision(collision);
                    }
                });
            }

            @Override
            public void diagnostic(String text) {
                Platform.runLater(
                        () -> statusPane.appendDiagnostic(text));
            }

            @Override
            public void sessionEnded() {
                Platform.runLater(() -> {
                    /*
                     * Ignore a disconnect notification from a session that
                     * has since been superseded by a newer connect/reconnect
                     * — otherwise a late-arriving async teardown from the
                     * old connection would tear down the new one.
                     */
                    if (token != sessionToken) {
                        return;
                    }

                    stopSessionIfActive();
                });
            }
        };
    }

    private void connectSafely(long token) {
        try {
            networkClient.connect();
        } catch (IOException | RuntimeException exception) {
            Platform.runLater(() -> {
                /*
                 * A failed connect attempt (e.g. connection refused) never
                 * reaches CollaborationNetworkClient's clientConnected/
                 * clientDisconnected transition, so it does not go through
                 * sessionEnded(). Handle it here instead, with the same
                 * stale-session guard, so the status pane's fields/button
                 * do not stay stuck as if a session were still active.
                 */
                if (token != sessionToken) {
                    return;
                }

                stopSessionIfActive();
                statusPane.setConnectionStatus("Connection failed");
                statusPane.appendDiagnostic(exception.toString());
            });

            exception.printStackTrace(System.err);
        }
    }

    private void updateFrame(long now) {
        runtimeStats.recordClientRenderFrame();

        Pose3d localPose = cameraMotionModel.latestPose();
        worldView.applyLocalPose(localPose);

        boolean clockSynchronized =
                networkClient != null
                        && networkClient.hasClockEstimate();

        boolean bufferedRemoteMotion =
                statusPane.bufferedRemoteMotionEnabled()
                        && clockSynchronized;

        long serverNowNanos = clockSynchronized
                ? networkClient.toServerTime(now)
                : now;

        participantManager.update(
                serverNowNanos,
                bufferedRemoteMotion);

        if (interactionController != null) {
            interactionController.update(serverNowNanos);
        }

        updateStatusIfDue(now);
    }

    private void updateStatusIfDue(long now) {
        if (now - lastStatsUpdateNanos
                < TimeUnit.MILLISECONDS.toNanos(250L)) {
            return;
        }

        statusPane.setPoseUpdateCount(
                poseStore.updateCount());

        statusPane.updateRuntimeStats(
                statsLogger.latestWindow(),
                statsLogger.latestSnapshot());

        lastStatsUpdateNanos = now;
    }

    private void addOrUpdateParticipant(
            ParticipantInfo participant) {

        participants.put(
                participant.objectId(),
                participant);

        scoreboardOverlay.addOrUpdateParticipant(participant);

        if (participant.objectId() != localObjectId) {
            participantManager.addOrUpdateParticipant(participant);
        }

        refreshParticipantList();
    }

    private void removeParticipant(long objectId) {
        participants.remove(objectId);
        participantManager.removeParticipant(objectId);
        resultStore.remove(objectId);
        scoreboardOverlay.removeParticipant(objectId);
        interactionStore.remove(objectId);

        if (interactionController != null) {
            interactionController.removeActor(objectId);
        }

        refreshParticipantList();
    }

    private void refreshParticipantList() {
        statusPane.setParticipants(
                participants.values(),
                localObjectId);
    }

    private Color colorForActor(long actorObjectId) {
        ParticipantInfo participant =
                participants.get(actorObjectId);

        if (participant != null) {
            return Color.hsb(
                    participant.hueDegrees(),
                    0.85,
                    1.0);
        }

        return actorObjectId == localObjectId
                ? Color.CYAN
                : Color.WHITE;
    }

    private String requestedName(Map<String, String> parameters) {
        String requested = parameters.get("name");

        if (requested != null
                && !requested.isBlank()) {
            return requested.strip();
        }

        return "Client-"
                + ThreadLocalRandom.current()
                        .nextInt(1000, 10_000);
    }

    private static boolean booleanNamedParameter(
            Map<String, String> namedParameters,
            String name,
            boolean defaultValue) {

        String value = namedParameters.get(name);

        return value == null
                ? defaultValue
                : Boolean.parseBoolean(value.strip());
    }

    public static void main(String[] args) {
        launch(ClientMain.class, args);
    }
}