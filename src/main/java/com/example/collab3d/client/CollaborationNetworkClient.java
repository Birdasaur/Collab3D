package com.example.collab3d.client;

import com.example.collab3d.common.NetworkConstants;
import com.example.collab3d.common.NetworkRuntimeStats;
import com.example.collab3d.common.NetworkSerializers;
import com.example.collab3d.common.ParticipantInfo;
import com.example.collab3d.common.ParticipantResultState;
import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.interactions.InteractionCollision;
import com.example.collab3d.common.interactions.InteractionType;
import com.example.collab3d.common.interactions.SharedInteraction;
import com.example.collab3d.common.messages.AssignedIdentityMessage;
import com.example.collab3d.common.messages.InteractionCollisionMessage;
import com.example.collab3d.common.messages.InteractionIntentMessage;
import com.example.collab3d.common.messages.ClientHelloMessage;
import com.example.collab3d.common.messages.ParticipantInfoMessage;
import com.example.collab3d.common.messages.ParticipantRemovedMessage;
import com.example.collab3d.common.messages.ParticipantResultStateMessage;
import com.example.collab3d.common.messages.PoseInputMessage;
import com.example.collab3d.common.messages.ServerNoticeMessage;
import com.example.collab3d.common.messages.SharedInteractionMessage;
import com.example.collab3d.common.messages.TimeSyncRequestMessage;
import com.example.collab3d.common.messages.TimeSyncResponseMessage;
import com.example.collab3d.simethereal.SimEtherealClientAdapter;
import com.jme3.network.Client;
import com.jme3.network.ClientStateListener;
import com.jme3.network.Message;
import com.jme3.network.MessageListener;
import com.jme3.network.Network;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SpiderMonkey client transport facade for the collaboration application.
 *
 * <p>High-frequency pose traffic remains unreliable UDP. Time synchronization
 * uses a separate low-frequency reliable exchange so clock estimation cannot be
 * confused with dropped pose packets.</p>
 */
final class CollaborationNetworkClient
        implements ClientStateListener,
        MessageListener<Client>,
        AutoCloseable {

    private static final Logger LOG =
            LoggerFactory.getLogger(CollaborationNetworkClient.class);

    interface Listener {
        void connectionStatus(String text);
        void identityAssigned(long objectId, String displayName);
        void participantAdded(ParticipantInfo participant);
        void participantRemoved(long objectId);
        void participantResultUpdated(ParticipantResultState state);
        void interactionUpdated(SharedInteraction interaction);
        void interactionCollision(InteractionCollision collision);
        void diagnostic(String text);

        /**
         * Fired when the underlying transport connection ends, whether the
         * server dropped it, the network failed, or this side closed it.
         * Distinct from {@link #connectionStatus(String)} (which is only a
         * status-text update): this is the signal a caller should act on to
         * tear down session-scoped state, e.g. re-enabling reconnect
         * controls that a previous, successful connection had disabled.
         */
        void sessionEnded();
    }

    private final ClientConnectionConfig connectionConfig;
    private final String displayName;
    private final RemotePoseStore poseStore;
    private final SharedInteractionStore interactionStore;
    private final ParticipantResultStore resultStore;
    private final NetworkRuntimeStats runtimeStats;
    private final boolean timeSyncDiagnosticsEnabled;
    private final Listener listener;
    private final AtomicLong poseSequence = new AtomicLong();
    private final AtomicLong interactionSequence = new AtomicLong();
    private final ClockSynchronizer clockSynchronizer = new ClockSynchronizer();
    private final AtomicBoolean timeSyncStarted = new AtomicBoolean();
    private final ScheduledExecutorService timeSyncScheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "client-time-sync");
                thread.setDaemon(true);
                return thread;
            });

    private volatile Client client;
    private volatile SimEtherealClientAdapter ethereal;
    private volatile long localObjectId = -1L;
    private volatile long lastTimeSyncDiagnosticNanos;

    CollaborationNetworkClient(
            ClientConnectionConfig connectionConfig,
            String displayName,
            RemotePoseStore poseStore,
            SharedInteractionStore interactionStore,
            ParticipantResultStore resultStore,
            NetworkRuntimeStats runtimeStats,
            boolean timeSyncDiagnosticsEnabled,
            Listener listener) {
        this.connectionConfig = Objects.requireNonNull(connectionConfig, "connectionConfig");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.poseStore = Objects.requireNonNull(poseStore, "poseStore");
        this.interactionStore = Objects.requireNonNull(interactionStore, "interactionStore");
        this.resultStore = Objects.requireNonNull(resultStore, "resultStore");
        this.runtimeStats = Objects.requireNonNull(runtimeStats, "runtimeStats");
        this.timeSyncDiagnosticsEnabled = timeSyncDiagnosticsEnabled;
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    void connect() throws IOException {
        NetworkSerializers.registerAll();
        listener.connectionStatus(
                "Connecting to " + connectionConfig.endpointDescription() + "...");

        Client newClient = Network.connectToServer(
                NetworkConstants.APPLICATION_NAME,
                NetworkConstants.PROTOCOL_VERSION,
                connectionConfig.host(),
                connectionConfig.tcpPort(),
                connectionConfig.udpPort());

        newClient.addClientStateListener(this);
        newClient.addMessageListener(
                this,
                AssignedIdentityMessage.class,
                ParticipantInfoMessage.class,
                ParticipantRemovedMessage.class,
                ServerNoticeMessage.class,
                TimeSyncResponseMessage.class,
                SharedInteractionMessage.class,
                InteractionCollisionMessage.class,
                ParticipantResultStateMessage.class);

        ethereal = SimEtherealClientAdapter.install(
                newClient,
                update -> {
                    runtimeStats.recordClientRemoteObjectUpdate();
                    poseStore.offer(update.objectId(), update.pose());
                },
                objectId -> poseStore.remove(objectId),
                runtimeStats::recordClientSimEtherealFrame,
                listener::diagnostic);

        client = newClient;
        newClient.start();
    }

    void sendPose(Pose3d pose) {
        runtimeStats.recordClientPoseSendAttempt();

        Client active = client;
        if (active == null || !active.isConnected() || localObjectId < 0L) {
            runtimeStats.recordClientPoseSendSkipped();
            return;
        }

        PoseInputMessage message = new PoseInputMessage(
                pose,
                poseSequence.incrementAndGet());
        message.setReliable(false);
        active.send(message);
        runtimeStats.recordClientPoseSent();
    }

    /**
     * Sends one synchronized tracer-projectile interaction and returns the provisional
     * local event used for zero-wait local rendering.
     *
     * <p>The event is only emitted after clock synchronization is available;
     * otherwise there is no defensible server-time timestamp to attach to the
     * interaction.</p>
     */
    SharedInteraction sendTracerProjectile(
            double originX,
            double originY,
            double originZ,
            double directionX,
            double directionY,
            double directionZ) {

        Client active = client;
        if (active == null
                || !active.isConnected()
                || localObjectId < 0L
                || !clockSynchronizer.hasEstimate()) {
            return null;
        }

        double length = Math.sqrt(
                directionX * directionX
                + directionY * directionY
                + directionZ * directionZ);
        if (!Double.isFinite(length) || length < 1.0e-9) {
            return null;
        }

        double dx = directionX / length;
        double dy = directionY / length;
        double dz = directionZ / length;
        long sequence = interactionSequence.incrementAndGet();
        long eventServerTimeNanos =
                clockSynchronizer.toServerTime(System.nanoTime());

        InteractionIntentMessage message = new InteractionIntentMessage(
                sequence,
                eventServerTimeNanos,
                InteractionType.TRACER_PROJECTILE,
                originX,
                originY,
                originZ,
                dx,
                dy,
                dz,
                -1L);
        message.setReliable(true);
        active.send(message);

        return new SharedInteraction(
                -1L,
                localObjectId,
                sequence,
                eventServerTimeNanos,
                0L,
                InteractionType.TRACER_PROJECTILE,
                originX,
                originY,
                originZ,
                dx,
                dy,
                dz,
                -1L);
    }

    long localObjectId() {
        return localObjectId;
    }

    /** Maps a local monotonic timestamp onto the estimated server timeline. */
    long toServerTime(long clientTimeNanos) {
        return clockSynchronizer.toServerTime(clientTimeNanos);
    }

    /** Maps a server monotonic timestamp onto the estimated local timeline. */
    long toClientTime(long serverTimeNanos) {
        return clockSynchronizer.toClientTime(serverTimeNanos);
    }

    boolean hasClockEstimate() {
        return clockSynchronizer.hasEstimate();
    }

    @Override
    public void clientConnected(Client connectedClient) {
        listener.connectionStatus("Connected; registering participant...");
        connectedClient.send(new ClientHelloMessage(displayName));
        startTimeSynchronization();
    }

    @Override
    public void clientDisconnected(Client disconnectedClient, DisconnectInfo info) {
        String reason = info == null ? "" : " (" + info + ")";
        listener.connectionStatus("Disconnected" + reason);
        localObjectId = -1L;
        clockSynchronizer.reset();
        listener.sessionEnded();
    }

    @Override
    public void messageReceived(Client source, Message message) {
        if (message instanceof AssignedIdentityMessage identity) {
            localObjectId = identity.getObjectId();
            listener.identityAssigned(identity.getObjectId(), identity.getDisplayName());
            listener.connectionStatus("Connected as " + identity.getDisplayName());

        } else if (message instanceof ParticipantInfoMessage participant) {
            listener.participantAdded(new ParticipantInfo(
                    participant.getObjectId(),
                    participant.getDisplayName(),
                    participant.getHueDegrees()));

        } else if (message instanceof ParticipantRemovedMessage removed) {
            poseStore.remove(removed.getObjectId());
            interactionStore.remove(removed.getObjectId());
            resultStore.remove(removed.getObjectId());
            listener.participantRemoved(removed.getObjectId());

        } else if (message instanceof ServerNoticeMessage notice) {
            listener.diagnostic("Server: " + notice.getText());

        } else if (message instanceof SharedInteractionMessage sharedMessage) {
            SharedInteraction interaction = sharedMessage.toInteraction();
            interactionStore.offer(interaction);
            listener.interactionUpdated(interaction);

        } else if (message instanceof InteractionCollisionMessage collisionMessage) {
            listener.interactionCollision(collisionMessage.toCollision());

        } else if (message instanceof ParticipantResultStateMessage resultMessage) {
            ParticipantResultState state = resultMessage.toState();
            resultStore.offer(state);
            listener.participantResultUpdated(state);

        } else if (message instanceof TimeSyncResponseMessage response) {
            long clientReceiveTimeNanos = System.nanoTime();
            ClockSynchronizer.Estimate estimate = clockSynchronizer.acceptResponse(
                    response,
                    clientReceiveTimeNanos);

            if (estimate != null) {
                runtimeStats.recordClientTimeSyncSample(
                        estimate.offsetNanos(),
                        estimate.roundTripNanos());

                logTimeSyncDiagnosticIfEnabled(
                        response,
                        clientReceiveTimeNanos,
                        estimate);
            }
        }
    }

    /**
     * Emits a low-frequency diagnostic validating the active clock mapping.
     *
     * <p>The diagnostic deliberately runs only after a successful time-sync
     * sample and only when the JavaFX named application parameter
     * {@code --timeSyncDiagnostics=true} was supplied at startup. It is therefore
     * completely absent from the normal hot path.</p>
     *
     * <p>The reported values are:</p>
     * <ul>
     *   <li>filtered round-trip time,</li>
     *   <li>filtered server-minus-client clock offset,</li>
     *   <li>retained synchronization sample count,</li>
     *   <li>client -&gt; server -&gt; client conversion error, and</li>
     *   <li>estimated age of the server response when it reached the client.</li>
     * </ul>
     *
     * <p>The conversion error should normally be exactly zero because the two
     * conversion functions are algebraic inverses using the same current
     * offset. The server-response age is a more useful end-to-end sanity check:
     * on localhost it should normally be very small, while on a LAN it should
     * approximately reflect one-way network transit plus scheduler jitter.</p>
     */
    private void logTimeSyncDiagnosticIfEnabled(
            TimeSyncResponseMessage response,
            long clientReceiveTimeNanos,
            ClockSynchronizer.Estimate estimate) {

        if (!timeSyncDiagnosticsEnabled) {
            return;
        }

        long diagnosticIntervalNanos = TimeUnit.SECONDS.toNanos(
                NetworkConstants.TIME_SYNC_DIAGNOSTICS_INTERVAL_SECONDS);

        long previousDiagnostic = lastTimeSyncDiagnosticNanos;
        if (previousDiagnostic != 0L
                && clientReceiveTimeNanos - previousDiagnostic < diagnosticIntervalNanos) {
            return;
        }

        lastTimeSyncDiagnosticNanos = clientReceiveTimeNanos;

        long estimatedServerReceiveTime =
                clockSynchronizer.toServerTime(clientReceiveTimeNanos);
        long roundTripClientTime =
                clockSynchronizer.toClientTime(estimatedServerReceiveTime);
        long conversionErrorNanos = roundTripClientTime - clientReceiveTimeNanos;

        long responseAgeNanos =
                estimatedServerReceiveTime - response.getServerSendTimeNanos();

        double rttMillis = estimate.roundTripNanos() / 1_000_000.0;
        double offsetMillis = estimate.offsetNanos() / 1_000_000.0;
        double responseAgeMillis = responseAgeNanos / 1_000_000.0;

        LOG.info(
                "TIME-SYNC-DIAG rtt={} ms offset={} ms samples={} "
                        + "conversionError={} ns responseAge={} ms",
                String.format(java.util.Locale.ROOT, "%.3f", rttMillis),
                String.format(java.util.Locale.ROOT, "%+.3f", offsetMillis),
                estimate.retainedSampleCount(),
                conversionErrorNanos,
                String.format(java.util.Locale.ROOT, "%+.3f", responseAgeMillis));
    }

    private void startTimeSynchronization() {
        if (!timeSyncStarted.compareAndSet(false, true)) {
            return;
        }

        timeSyncScheduler.scheduleAtFixedRate(
                this::sendTimeSyncSafely,
                0L,
                NetworkConstants.TIME_SYNC_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    private void sendTimeSyncSafely() {
        try {
            Client active = client;
            if (active == null || !active.isConnected()) {
                return;
            }

            TimeSyncRequestMessage request = clockSynchronizer.createRequest();
            request.setReliable(true);
            active.send(request);

        } catch (RuntimeException exception) {
            /*
             * Clock synchronization is advisory. A failed sync sample must not
             * destabilize the collaboration connection or pose stream.
             */
            listener.diagnostic(
                    "Time synchronization request failed: " + exception.getMessage());
        }
    }

    @Override
    public void close() {
        timeSyncScheduler.shutdownNow();
        clockSynchronizer.reset();

        Client active = client;
        client = null;
        ethereal = null;
        if (active != null) {
            try {
                active.close();
            } catch (IllegalStateException exception) {
                /*
                 * The transport can already have terminated itself (jME3
                 * DefaultClient does this internally after a fatal read
                 * error, e.g. the server process dying mid-session) before
                 * this close() runs. DefaultClient.close() then throws
                 * "Client is not started" instead of behaving as a no-op,
                 * so treat an already-stopped client as successfully
                 * closed rather than propagating this.
                 */
            }
        }
    }
}