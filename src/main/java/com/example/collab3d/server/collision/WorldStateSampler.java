package com.example.collab3d.server.collision;

import com.example.collab3d.common.Pose3d;
import com.example.collab3d.server.RewindPoseResult;
import com.example.collab3d.server.ServerRewindService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Bridges immutable live snapshots and historical rewind sampling. */
public final class WorldStateSampler {

    private final ServerRewindService rewindService;
    private final CollisionShapeProvider shapeProvider;
    private final AtomicReference<AuthoritativeWorldSnapshot> latest =
            new AtomicReference<>(AuthoritativeWorldSnapshot.empty());

    public WorldStateSampler(
            ServerRewindService rewindService,
            CollisionShapeProvider shapeProvider) {
        this.rewindService = Objects.requireNonNull(rewindService, "rewindService");
        this.shapeProvider = Objects.requireNonNull(shapeProvider, "shapeProvider");
    }

    public void publish(long serverTimeNanos, Map<Long, Pose3d> poses) {
        Map<Long, AuthoritativeEntityState> states = new LinkedHashMap<>();
        poses.forEach((objectId, pose) -> states.put(
                objectId,
                new AuthoritativeEntityState(
                        objectId,
                        pose,
                        shapeProvider.createShape(objectId, pose))));
        latest.set(new AuthoritativeWorldSnapshot(serverTimeNanos, states));
    }

    public AuthoritativeWorldSnapshot latest() {
        return latest.get();
    }

    /** Reconstructs all currently known participants that have history at time T. */
    public AuthoritativeWorldSnapshot sampleAt(long serverTimeNanos) {
        Map<Long, AuthoritativeEntityState> states = new LinkedHashMap<>();
        for (long objectId : latest.get().entities().keySet()) {
            RewindPoseResult result = rewindService.poseAt(objectId, serverTimeNanos);
            if (!result.available()) {
                continue;
            }
            Pose3d pose = result.pose();
            states.put(objectId, new AuthoritativeEntityState(
                    objectId,
                    pose,
                    shapeProvider.createShape(objectId, pose)));
        }
        return new AuthoritativeWorldSnapshot(serverTimeNanos, states);
    }
}
