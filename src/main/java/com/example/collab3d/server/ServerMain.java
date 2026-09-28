package com.example.collab3d.server;

import com.example.collab3d.common.NetworkConstants;
import com.example.collab3d.common.NetworkRuntimeStats;
import com.example.collab3d.common.NetworkSerializers;
import com.example.collab3d.common.NetworkStatsLogger;
import com.example.collab3d.common.PropertiesFileLoader;
import com.example.collab3d.common.ParticipantInfo;
import com.example.collab3d.common.ParticipantResultState;
import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.Quaterniond;
import com.example.collab3d.common.geometry.Vector3d;
import com.example.collab3d.common.TimedPose3d;
import com.example.collab3d.common.interactions.InteractionType;
import com.example.collab3d.common.messages.AssignedIdentityMessage;
import com.example.collab3d.common.messages.InteractionIntentMessage;
import com.example.collab3d.common.messages.ClientHelloMessage;
import com.example.collab3d.common.messages.ParticipantInfoMessage;
import com.example.collab3d.common.messages.ParticipantRemovedMessage;
import com.example.collab3d.common.messages.ParticipantResultStateMessage;
import com.example.collab3d.common.messages.PoseInputMessage;
import com.example.collab3d.common.messages.ServerNoticeMessage;
import com.example.collab3d.common.messages.TimeSyncRequestMessage;
import com.example.collab3d.common.messages.TimeSyncResponseMessage;
import com.example.collab3d.server.collision.CollisionService;
import com.example.collab3d.server.collision.ParticipantCollisionShapeProvider;
import com.example.collab3d.server.collision.WorldStateSampler;
import com.example.collab3d.server.interaction.FixedStepInteractionLoop;
import com.example.collab3d.server.interaction.InteractionContext;
import com.example.collab3d.server.interaction.InteractionCollisionEvent;
import com.example.collab3d.server.interaction.InteractionEvent;
import com.example.collab3d.server.interaction.InteractionStartedEvent;
import com.example.collab3d.server.interaction.InteractionManager;
import com.example.collab3d.server.interaction.InteractionReplicationService;
import com.example.collab3d.server.interaction.ProjectileInteractionConfiguration;
import com.example.collab3d.server.interaction.ProjectileInteractionHandler;
import com.example.collab3d.server.interaction.StartInteractionCommand;
import com.example.collab3d.simethereal.SimEtherealServerAdapter;
import com.jme3.network.ConnectionListener;
import com.jme3.network.HostedConnection;
import com.jme3.network.Message;
import com.jme3.network.MessageListener;
import com.jme3.network.Network;
import com.jme3.network.Server;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Standalone authoritative collaboration server. */
public final class ServerMain {

    private static final Logger LOG = LoggerFactory.getLogger(ServerMain.class);

    private ServerMain() {
    }

    public static void main(String[] args) throws Exception {
        /*
         * server.properties supplies defaults; any matching --key=value
         * command-line argument overrides it. Both use the same key names
         * (tcpPort, udpPort, maxClients).
         */
        Map<String, String> parameters =
                new LinkedHashMap<>(
                        PropertiesFileLoader.load("server.properties"));

        parameters.putAll(parseArgs(args));

        int tcpPort = intParameter(
                parameters, "tcpPort", NetworkConstants.TCP_PORT);

        int udpPort = intParameter(
                parameters, "udpPort", NetworkConstants.UDP_PORT);

        int maxClients = intParameter(
                parameters, "maxClients", NetworkConstants.MAX_CLIENTS);

        CollaborationServer collaborationServer =
                new CollaborationServer(tcpPort, udpPort, maxClients);

        collaborationServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(
                collaborationServer::close,
                "collaboration-server-shutdown"));
        System.out.println("Press Ctrl+C to stop the server.");
        new CountDownLatch(1).await();
    }

    /**
     * Parses {@code --key=value} arguments, matching the style already used
     * by the client's JavaFX named parameters.
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> parsed = new LinkedHashMap<>();

        for (String arg : args) {
            if (!arg.startsWith("--")) {
                continue;
            }

            int separator = arg.indexOf('=');
            if (separator < 0) {
                continue;
            }

            parsed.put(
                    arg.substring(2, separator),
                    arg.substring(separator + 1));
        }

        return parsed;
    }

    private static int intParameter(
            Map<String, String> parameters,
            String name,
            int defaultValue) {

        String value = parameters.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            int parsed = Integer.parseInt(value.strip());
            if (parsed < 1) {
                throw new IllegalArgumentException(
                        name + " must be positive: " + parsed);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid --" + name + " value: " + value, exception);
        }
    }

    private static final class CollaborationServer
            implements ConnectionListener,
            MessageListener<HostedConnection>,
            AutoCloseable {

        private final Map<Integer, ClientSession> sessions = new ConcurrentHashMap<>();
        private final AtomicLong nextObjectId = new AtomicLong(1L);
        private final NetworkRuntimeStats runtimeStats = new NetworkRuntimeStats();
        private final NetworkStatsLogger statsLogger = new NetworkStatsLogger(
                runtimeStats,
                LOG,
                NetworkStatsLogger.Role.SERVER);
        private final ServerRewindService rewindService =
                new ServerRewindService(runtimeStats);
        private final WorldStateSampler worldStateSampler = new WorldStateSampler(
                rewindService,
                new ParticipantCollisionShapeProvider());
        private final ServerParticipantStateStore resultStateStore =
                new ServerParticipantStateStore();

        private final int tcpPort;
        private final int udpPort;
        private final int maxClients;

        private Server server;
        private InteractionManager interactionManager;
        private FixedStepInteractionLoop interactionLoop;
        private SimEtherealServerAdapter ethereal;
        private ScheduledExecutorService publisher;

        CollaborationServer(int tcpPort, int udpPort, int maxClients) {
            this.tcpPort = tcpPort;
            this.udpPort = udpPort;
            this.maxClients = maxClients;
        }

        void start() throws IOException {
            NetworkSerializers.registerAll();

            server = Network.createServer(
                    NetworkConstants.APPLICATION_NAME,
                    NetworkConstants.PROTOCOL_VERSION,
                    tcpPort,
                    udpPort);

            ethereal = SimEtherealServerAdapter.install(server);
            server.addConnectionListener(this);
            server.addMessageListener(
                    this,
                    ClientHelloMessage.class,
                    PoseInputMessage.class,
                    TimeSyncRequestMessage.class,
                    InteractionIntentMessage.class);
            server.start();
            statsLogger.start();

            ProjectileInteractionConfiguration projectileConfig =
                    new ProjectileInteractionConfiguration(
                            NetworkConstants.TRACER_ROUND_SPEED_UNITS_PER_SECOND,
                            NetworkConstants.TRACER_ROUND_COLLISION_RADIUS,
                            NetworkConstants.TRACER_ROUND_MUZZLE_OFFSET,
                            NetworkConstants.TRACER_ROUND_LIFETIME_NANOS,
                            NetworkConstants.INTERACTION_MAX_CATCH_UP_NANOS,
                            NetworkConstants.INTERACTION_STEP_NANOS);
            interactionManager = new InteractionManager(
                    new InteractionContext(
                            worldStateSampler,
                            new CollisionService()),
                    new InteractionReplicationService(this::broadcast),
                    this::handleAuthoritativeInteractionEvent,
                    List.of(new ProjectileInteractionHandler(projectileConfig)));
            interactionLoop = new FixedStepInteractionLoop(interactionManager);
            interactionLoop.start();

            publisher = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "simethereal-state-publisher");
                thread.setDaemon(false);
                return thread;
            });

            long periodNanos = TimeUnit.SECONDS.toNanos(1L)
                    / NetworkConstants.SERVER_STATE_HZ;

            publisher.scheduleAtFixedRate(
                    this::publishFrameSafely,
                    0L,
                    periodNanos,
                    TimeUnit.NANOSECONDS);

            System.out.printf(
                    "Server listening TCP %d / UDP %d; max clients=%d; protocol=%d%n",
                    tcpPort,
                    udpPort,
                    maxClients,
                    NetworkConstants.PROTOCOL_VERSION);
        }

        @Override
        public void connectionAdded(Server source, HostedConnection connection) {
            System.out.println("Transport connection added: " + connection.getId());
        }

        @Override
        public void connectionRemoved(Server source, HostedConnection connection) {
            ClientSession removed = sessions.remove(connection.getId());
            if (removed == null) {
                return;
            }

            ethereal.stopHosting(connection);
            rewindService.removeParticipant(removed.info().objectId());
            resultStateStore.removeParticipant(removed.info().objectId());
            broadcast(new ParticipantRemovedMessage(removed.info().objectId()));
            System.out.printf(
                    "Participant left: %s [%d]%n",
                    removed.info().displayName(),
                    removed.info().objectId());
        }

        @Override
        public void messageReceived(HostedConnection source, Message message) {
            if (message instanceof ClientHelloMessage hello) {
                register(source, hello);

            } else if (message instanceof PoseInputMessage poseMessage) {
                handlePoseInput(source, poseMessage);

            } else if (message instanceof TimeSyncRequestMessage syncRequest) {
                handleTimeSync(source, syncRequest);

            } else if (message instanceof InteractionIntentMessage interaction) {
                handleInteractionIntent(source, interaction);
            }
        }

        private void handlePoseInput(
                HostedConnection source,
                PoseInputMessage poseMessage) {

            ClientSession session = sessions.get(source.getId());
            if (session == null) {
                return;
            }

            runtimeStats.recordServerPoseReceived();

            long serverReceiveTimeNanos = System.nanoTime();
            Pose3d pose = poseMessage
                    .toPose()
                    .clamped(NetworkConstants.WORLD_LIMIT);

            TimedPose3d timedPose = new TimedPose3d(
                    pose,
                    poseMessage.getClientSequence(),
                    poseMessage.getClientSampleTimeNanos(),
                    serverReceiveTimeNanos);

            if (session.offer(timedPose)) {
                runtimeStats.recordServerPoseAccepted();
            } else {
                runtimeStats.recordServerPoseRejected();
            }
        }

        /**
         * Transfers one client intent onto the authoritative interaction thread.
         * Actor identity comes exclusively from the transport connection.
         */
        private void handleInteractionIntent(
                HostedConnection source,
                InteractionIntentMessage message) {

            ClientSession session = sessions.get(source.getId());
            if (session == null
                    || message.getInteractionType()
                            != InteractionType.TRACER_PROJECTILE) {
                return;
            }

            InteractionManager manager = interactionManager;
            if (manager == null) {
                return;
            }

            manager.enqueue(new StartInteractionCommand(
                    session.info().objectId(),
                    message.getInteractionSequence(),
                    message.getEventServerTimeNanos(),
                    message.getInteractionType(),
                    new Vector3d(
                            message.getOriginX(),
                            message.getOriginY(),
                            message.getOriginZ()),
                    new Vector3d(
                            message.getDirectionX(),
                            message.getDirectionY(),
                            message.getDirectionZ())));
        }


        /**
         * Applies application-level persistent result policy to authoritative
         * interaction events without coupling that policy to an interaction handler.
         */
        private void handleAuthoritativeInteractionEvent(InteractionEvent event) {
            ParticipantResultState updated = null;

            if (event instanceof InteractionStartedEvent started
                    && started.interaction().type() == InteractionType.TRACER_PROJECTILE) {
                updated = resultStateStore.recordShot(started.actorObjectId());
            } else if (event instanceof InteractionCollisionEvent collision
                    && collision.targetObjectId() != collision.actorObjectId()
                    && resultStateStore.containsParticipant(
                            collision.targetObjectId())) {
                updated = resultStateStore.recordHit(collision.actorObjectId());
            }

            if (updated != null) {
                broadcastResultState(updated);
            }
        }

        private void broadcastResultState(ParticipantResultState state) {
            ParticipantResultStateMessage message =
                    new ParticipantResultStateMessage(state);
            message.setReliable(true);
            broadcast(message);
        }

        /**
         * Responds to one NTP-style synchronization probe.
         *
         * <p>The receive timestamp is captured immediately on entry. The send
         * timestamp is captured as late as practical, immediately before the
         * response object is submitted to SpiderMonkey. Both timestamps belong
         * to the server's monotonic clock domain.</p>
         */
        private void handleTimeSync(
                HostedConnection source,
                TimeSyncRequestMessage request) {

            long serverReceiveTimeNanos = System.nanoTime();
            runtimeStats.recordServerTimeSyncRequest();

            long serverSendTimeNanos = System.nanoTime();
            TimeSyncResponseMessage response = new TimeSyncResponseMessage(
                    request.getSequence(),
                    request.getClientSendTimeNanos(),
                    serverReceiveTimeNanos,
                    serverSendTimeNanos);
            response.setReliable(true);
            source.send(response);
        }

        private synchronized void register(
                HostedConnection connection,
                ClientHelloMessage hello) {

            if (sessions.containsKey(connection.getId())) {
                return;
            }

            if (sessions.size() >= maxClients) {
                connection.send(new ServerNoticeMessage("Server is full."));
                connection.close("Server is full.");
                return;
            }

            long objectId = nextObjectId.getAndIncrement();
            String displayName = sanitizeName(hello.getDisplayName(), objectId);
            double hue = (objectId * 137.50776405003785) % 360.0;
            ParticipantInfo info = new ParticipantInfo(objectId, displayName, hue);

            long initialServerTimeNanos = System.nanoTime();
            Pose3d initialPose = new Pose3d(
                    0.0,
                    -5.0,
                    -20.0,
                    Quaterniond.identity(),
                    initialServerTimeNanos);

            List<ClientSession> existing = new ArrayList<>(sessions.values());
            ClientSession session = new ClientSession(connection, info, initialPose);
            sessions.put(connection.getId(), session);
            rewindService.registerParticipant(
                    objectId,
                    initialServerTimeNanos,
                    initialPose);
            ethereal.startHosting(connection, objectId, initialPose);

            connection.send(new AssignedIdentityMessage(objectId, displayName));
            for (ClientSession other : existing) {
                connection.send(other.infoMessage());
            }

            /*
             * A joining client receives the current persistent state snapshot
             * before the new participant's zeroed state is broadcast. This is
             * what makes the result model reconstructable for late joiners.
             */
            for (ParticipantResultState state : resultStateStore.snapshot()) {
                ParticipantResultStateMessage resultMessage =
                        new ParticipantResultStateMessage(state);
                resultMessage.setReliable(true);
                connection.send(resultMessage);
            }

            ParticipantResultState initialResultState =
                    resultStateStore.registerParticipant(objectId);

            broadcast(session.infoMessage());
            broadcastResultState(initialResultState);

            connection.send(new ServerNoticeMessage(
                    "Connected. SimEthereal object id=" + objectId));

            System.out.printf(
                    "Participant joined: %s [%d], active=%d%n",
                    displayName,
                    objectId,
                    sessions.size());
        }

        private void publishFrameSafely() {
            try {
                long now = System.nanoTime();
                Map<Long, Pose3d> snapshotPoses = new java.util.LinkedHashMap<>();
                ethereal.beginFrame(now);
                try {
                    for (ClientSession session : sessions.values()) {
                        Pose3d authoritativePose = session.pose();
                        long objectId = session.info().objectId();

                        /*
                         * Record exactly the state published for this server
                         * frame, using the server frame time as the historical
                         * key. This is the authoritative rewind timeline.
                         */
                        rewindService.recordAuthoritativePose(
                                objectId,
                                now,
                                authoritativePose);

                        snapshotPoses.put(objectId, authoritativePose);
                        ethereal.updateEntity(
                                objectId,
                                authoritativePose);
                    }
                } finally {
                    ethereal.endFrame();
                }
                worldStateSampler.publish(now, snapshotPoses);
                runtimeStats.recordServerStateFrame();

            } catch (RuntimeException exception) {
                System.err.println("Fatal SimEthereal frame failure:");
                exception.printStackTrace(System.err);

                ScheduledExecutorService activePublisher = publisher;
                if (activePublisher != null) {
                    activePublisher.shutdownNow();
                }
            }
        }

        private void broadcast(Message message) {
            for (ClientSession session : sessions.values()) {
                session.connection().send(message);
            }
        }

        private static String sanitizeName(String requested, long objectId) {
            if (requested == null || requested.isBlank()) {
                return "Client-" + objectId;
            }

            String stripped = requested.strip();
            return stripped.length() <= 32
                    ? stripped
                    : stripped.substring(0, 32);
        }

        @Override
        public void close() {
            if (interactionLoop != null) {
                interactionLoop.close();
                interactionLoop = null;
            }

            if (publisher != null) {
                publisher.shutdownNow();
                publisher = null;
            }

            statsLogger.close();

            if (server != null) {
                server.close();
                server = null;
            }
        }
    }

    private static final class ClientSession {

        private final HostedConnection connection;
        private final ParticipantInfo info;
        private final AtomicReference<TimedPose3d> state;

        ClientSession(
                HostedConnection connection,
                ParticipantInfo info,
                Pose3d initialPose) {
            this.connection = connection;
            this.info = info;
            state = new AtomicReference<>(new TimedPose3d(
                    initialPose,
                    -1L,
                    initialPose.sampleTimeNanos(),
                    initialPose.sampleTimeNanos()));
        }

        HostedConnection connection() {
            return connection;
        }

        ParticipantInfo info() {
            return info;
        }

        Pose3d pose() {
            return state.get().pose();
        }

        boolean offer(TimedPose3d replacement) {
            while (true) {
                TimedPose3d previous = state.get();
                if (replacement.sequence() <= previous.sequence()) {
                    return false;
                }

                if (state.compareAndSet(previous, replacement)) {
                    return true;
                }
            }
        }

        ParticipantInfoMessage infoMessage() {
            return new ParticipantInfoMessage(
                    info.objectId(),
                    info.displayName(),
                    info.hueDegrees());
        }
    }
}