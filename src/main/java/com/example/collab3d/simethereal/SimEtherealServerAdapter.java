package com.example.collab3d.simethereal;

import com.example.collab3d.common.NetworkConstants;
import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.Quaterniond;
import com.simsilica.ethereal.EtherealHost;
import com.simsilica.ethereal.zone.ZoneManager;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.jme3.network.HostedConnection;
import com.jme3.network.Server;
import com.simsilica.mathd.AaBBox;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typed adapter around the SimEthereal server-side service.
 *
 * <p>This is the only server-side class in the application that needs to know
 * about SimEthereal's concrete API.</p>
 */
public final class SimEtherealServerAdapter {
    private final EtherealHost host;
    private final ZoneManager zones;
    private final Map<Long, AaBBox> entityBounds = new ConcurrentHashMap<>();

    private SimEtherealServerAdapter(
            EtherealHost host,
            ZoneManager zones) {
        this.host = Objects.requireNonNull(host, "host");
        this.zones = Objects.requireNonNull(zones, "zones");
    }

    public static SimEtherealServerAdapter install(Server server) {
        Objects.requireNonNull(server, "server");

        SimEtherealConfig config = SimEtherealConfig.create();
        EtherealHost host = new EtherealHost(
                config.objectProtocol(),
                config.zoneGrid(),
                config.zoneRadius());

        /*
         * SimEthereal's state collector controls how often collected state is
         * packaged for network transmission.  Keep this aligned with the
         * application test rate.  The public SimEthereal API expresses this
         * interval in milliseconds.
         */
        host.setStateCollectionInterval(
                1_000_000_000L  / NetworkConstants.SERVER_STATE_HZ);

        server.getServices().addService(host);

        return new SimEtherealServerAdapter(host, host.getZones());
    }

    public void startHosting(
            HostedConnection connection,
            long objectId,
            Pose3d initialPose) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(initialPose, "initialPose");

        Pose3d pose = initialPose.normalized();
        host.startHostingOnConnection(connection);
        host.getStateListener(connection).setSelf(
                objectId,
                toVec3d(pose));
    }

    public void stopHosting(HostedConnection connection) {
        if (connection != null) {
            host.stopHostingOnConnection(connection);
        }
    }

    public void beginFrame(long serverTimeNanos) {
        zones.beginUpdate(serverTimeNanos);
    }

    public void updateEntity(long objectId, Pose3d inputPose) {
        Objects.requireNonNull(inputPose, "inputPose");

        Pose3d pose = inputPose.normalized();

        Vec3d position = toVec3d(pose);
        Quatd orientation = toQuatd(pose.orientation());

        AaBBox bounds = entityBounds.computeIfAbsent(
                objectId,
                ignored -> new AaBBox(1));

        bounds.setCenter(position);

        zones.updateEntity(
                objectId,
                true,
                position,
                orientation,
                bounds);
    }

    public void endFrame() {
        zones.endUpdate();
    }

    private static Vec3d toVec3d(Pose3d pose) {
        return new Vec3d(pose.x(), pose.y(), pose.z());
    }

    private static Quatd toQuatd(Quaterniond quaternion) {
        Quaterniond normalized = quaternion == null
                ? Quaterniond.identity()
                : quaternion.normalized();
        return new Quatd(
                normalized.x(),
                normalized.y(),
                normalized.z(),
                normalized.w());
    }
}