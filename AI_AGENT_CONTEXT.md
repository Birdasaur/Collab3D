# AI Agent Context: Java/JavaFX Multiplayer Prototype

## Mission

This repository is a compact Java/JavaFX multiplayer networking reference implementation using SpiderMonkey and SimEthereal. Preserve the existing architecture while extending it. Do not “simplify” timing, authority, or threading boundaries merely because the demo currently runs on localhost.

This file is written for coding agents. Treat it as a constraint/context file, not a tutorial.

## Source baseline

Current source snapshot:

- Java 21
- JavaFX 21.0.9
- SpiderMonkey / `jme3-networking` 3.9.0-stable
- `jme3-core` 3.9.0-stable
- SimEthereal 1.8.0
- SimMath 1.6.0
- protocol version 5
- maximum 12 clients
- 91 Java source files

The repository's older `README.md` and `VALIDATION.md` contain historical statements that are no longer authoritative. In particular, do **not** restore 30 Hz behavior, simple smoothing, or reflection-based SimEthereal adapters based on those files. The Java source and this documentation set describe the current architecture.

## Primary architectural invariants

### Do not collapse the loops

Keep these concepts separate even when their configured rates happen to match:

- local motion simulation (`CLIENT_MOTION_HZ = 120`)
- client pose publication (`CLIENT_POSE_HZ = 120`)
- server shared-state publication (`SERVER_STATE_HZ = 120`)
- authoritative interaction simulation (`SERVER_INTERACTION_HZ = 120`)
- JavaFX render pulse (independent and not assumed to be 120 Hz)

`LocalPosePublisher` intentionally runs outside JavaFX. Do not move local simulation back into `AnimationTimer`.

### Do not use JavaFX as simulation/network state

The network/simulation model must remain renderer-neutral:

- `Pose3d`
- `Quaterniond`
- `Vector3d`
- collision shapes
- interaction records
- participant result records

JavaFX conversion belongs at `FxPoseCodec` and presentation classes.

### Do not mix clock domains

`System.nanoTime()` is process-local. A client's `nanoTime` and the server's `nanoTime` are not directly comparable.

- `PoseInputMessage.clientSampleTimeNanos` stays in client clock domain.
- `TimedPose3d.serverReceiveTimeNanos` stays in server clock domain.
- SimEthereal remote poses are stamped with the SimEthereal frame/server time in `SimEtherealClientAdapter.beginFrame()`.
- `ClockSynchronizer` maps client monotonic time to estimated server monotonic time.
- Interaction intent times must be mapped through that synchronization.

Never replace this with wall-clock timestamps unless the architecture is deliberately redesigned.

### Interpolation first; prediction only as bounded fallback

Remote motion path:

```text
authoritative sample
 -> RemotePoseHistory
 -> adaptive delayed render time
 -> interpolate when bracketed
 -> predict only after newest sample and <= 25 ms
 -> hold after prediction horizon
 -> reconcile small changes / snap large changes
 -> renderer
```

Do not render the newest network sample directly when buffered remote motion is enabled. Do not implement unbounded dead reckoning.

### Server rewind is separate from client render history

- Client history retention: 500 ms.
- Server rewind retention: 1 s.
- Server rewind uses server publication timestamps.
- Server rewind never extrapolates.

Do not reuse client interpolation state as server lag-compensation history.

### Interaction authority stays on the server

Client sends intent, not a hit result.

Actor identity is derived from `HostedConnection`; do not trust a client-supplied actor ID.

Current tracer validation includes:

- type must be `TRACER_PROJECTILE` in `ServerMain`;
- event time must not be too far in the future (`20 ms` tolerance);
- event time must not exceed historical catch-up window (`500 ms`);
- direction must be finite/nonzero after normalization;
- actor must exist in historical/latest authoritative world state;
- submitted origin must be within `2.0` world units of rewound authoritative actor origin.

The server computes collision. Clients only present replicated results.

### Persistent result state stays server-owned

`ParticipantResultState` fields:

- `objectId`
- `shotsFired`
- `hitCount`
- `score`

Accuracy is derived, not independently replicated.

The server mutates counters from authoritative interaction events. Do not add client messages that directly set these counters.

### Serializer order is protocol state

`NetworkSerializers.registerAll()` order is deliberate. Client and server call the same method.

Current order:

1. `ClientHelloMessage`
2. `AssignedIdentityMessage`
3. `ParticipantInfoMessage`
4. `ParticipantRemovedMessage`
5. `PoseInputMessage`
6. `ServerNoticeMessage`
7. `TimeSyncRequestMessage`
8. `TimeSyncResponseMessage`
9. `InteractionIntentMessage`
10. `SharedInteractionMessage`
11. `InteractionCollisionMessage`
12. `ParticipantResultStateMessage`

Do not reorder existing registrations. A protocol-affecting change requires deliberate compatibility handling and normally a `PROTOCOL_VERSION` increment.

### Interaction enum ordinal is on the wire

`InteractionIntentMessage` and `SharedInteractionMessage` serialize `InteractionType.ordinal()`.

Current enum order:

1. `POINTER_RAY`
2. `SELECTION_CLEAR`
3. `SELECTION_SET`
4. `TRACER_PROJECTILE`

Do not reorder these values. Adding a value in the middle changes existing ordinals. Prefer append-only changes unless the protocol is redesigned to use explicit stable codes.

## Authority model: do not overstate it

The server is fully authoritative for interaction acceptance, interaction simulation, collision results, rewind queries, and participant result state.

The movement path is different. The server currently accepts client pose samples subject to:

- sequence monotonicity;
- pose normalization;
- finite-value cleanup;
- world position clamp to +/-100.

It does **not** independently simulate movement commands or validate speed/acceleration/obstacle traversal. It then publishes the latest accepted pose as shared state. Do not describe this as complete server-authoritative movement or anti-cheat movement validation.

Correspondingly, there is no classic local-player prediction + correction loop against a separately simulated authoritative server player. The prediction/reconciliation classes in this code are for **remote presentation**.

## Thread model

### JavaFX Application Thread

Owns all scene-graph mutation and presentation:

- `ClientMain.updateFrame()`
- local camera transform application
- `RemoteParticipantView` transforms
- label projection and movement
- tracer visual update
- scoreboard/status UI
- participant view add/remove

Network listener callbacks use `Platform.runLater()` before touching UI.

### `local-camera-motion`

Single writer to mutable fields inside `CameraMotionModel`.

Reads `CameraInputState.snapshotAndConsumeLook()` and publishes immutable `Pose3d` through `AtomicReference`.

### SpiderMonkey/SimEthereal threads

May call message and shared-object listeners. Do not touch JavaFX nodes directly.

Use stores/queues/immutable objects to cross into the render thread.

### `client-time-sync`

Sends reliable time-sync probes once per second.

### `simethereal-state-publisher`

Server 120 Hz loop. For each frame it:

1. begins SimEthereal update;
2. reads each session's latest pose;
3. records the same pose to rewind history with frame time;
4. publishes entity state;
5. ends SimEthereal update;
6. publishes `AuthoritativeWorldSnapshot`.

### `authoritative-interaction-loop`

Dedicated fixed-step 120 Hz server interaction thread. `InteractionManager` owns active interaction mutation here. Network threads only enqueue immutable `StartInteractionCommand` objects.

## Timing constants that should be treated as intentional policy

```text
CLIENT_MOTION_HZ                         120
CLIENT_POSE_HZ                           120
SERVER_STATE_HZ                          120
SERVER_INTERACTION_HZ                    120
REMOTE_HISTORY_DURATION                  500 ms
SERVER_REWIND_HISTORY_DURATION           1000 ms
REMOTE_INTERPOLATION_MIN_DELAY           8 ms
REMOTE_INTERPOLATION_DEFAULT_DELAY       12 ms
REMOTE_INTERPOLATION_MAX_DELAY           30 ms
MAX_PREDICTION                           25 ms
RECONCILIATION_HALF_LIFE                 8 ms
RECONCILIATION_SNAP_DISTANCE             5.0
RECONCILIATION_SNAP_ANGLE                45 deg
TIME_SYNC_INTERVAL                       1 s
TIME_SYNC_SAMPLE_WINDOW                  32
TIME_SYNC_BEST_SAMPLE_COUNT              8
TRACER_SPEED                             60 units/s
TRACER_LIFETIME                          500 ms
TRACER_COLLISION_RADIUS                  0.06
INTERACTION_MAX_FUTURE_TOLERANCE         20 ms
INTERACTION_ORIGIN_VALIDATION_TOLERANCE  2.0
INTERACTION_MAX_CATCH_UP                 500 ms
```

Do not change these as an incidental side effect of another feature.

## Important class ownership

### Reusable neutral/common layer

- `Pose3d`, `Quaterniond`, `TimedPose3d`
- `ParticipantInfo`, `ParticipantResultState`
- common geometry records/interfaces
- `SharedInteraction`, `InteractionCollision`, `InteractionType`
- message classes
- `NetworkConstants`, `NetworkSerializers`

### Reusable client motion/timing infrastructure

- `ClockSynchronizer`
- `LocalPosePublisher` pattern
- `RemotePoseStore`
- `RemoteParticipantMotionState`
- `RemotePoseHistory`
- `RemoteInterpolationController`
- `RemotePosePredictor`
- `PoseReconciler`

Most are currently package-private because this is a prototype. Do not interpret package visibility as evidence that the concepts are demo-only.

### Reusable server infrastructure

- `ServerRewindService`
- `WorldStateSampler`
- collision package
- `InteractionManager`
- `InteractionHandler`
- `FixedStepInteractionLoop`
- interaction lifecycle records/events
- `InteractionReplicationService`

### Adapters/integration seams

- `SimEtherealClientAdapter`
- `SimEtherealServerAdapter`
- `SimEtherealConfig`
- `CollaborationNetworkClient`

### Reference/application-specific implementations

- `CameraMotionModel`
- `ParticipantCollisionShapeProvider`
- `ProjectileInteractionHandler` and projectile state/config
- `ServerParticipantStateStore`

These demonstrate patterns but encode application-specific semantics.

### Demo/presentation classes

- `ClientMain`, `ClientLauncher`
- `CameraController`, `ClientWorldView`
- `RemoteParticipantManager`, `RemoteParticipantView`, `RemoteParticipantLabelOverlay`
- `TracerRoundManager`
- `ClientScoreboardOverlay`
- `ClientStatusPane`
- `ServerMain` as current composition/bootstrapping application

## Data flows

### Local motion

```text
FX input -> CameraInputState -> LocalPosePublisher -> CameraMotionModel
                                      |                  |
                                      |                  +-> latest immutable Pose3d -> FX
                                      +-> sendPose(Pose3d) -> PoseInputMessage UDP
```

### Remote motion

```text
Server pose -> SimEthereal -> beginFrame(serverTime) -> objectUpdated
 -> RemotePoseStore -> history/jitter/interpolation/prediction/reconciliation
 -> RemoteParticipantView
```

### Clock sync

```text
ClockSynchronizer.createRequest()
 -> reliable TimeSyncRequestMessage
 -> server captures receive/send times
 -> reliable TimeSyncResponseMessage
 -> ClockSynchronizer.acceptResponse()
 -> rolling low-RTT median offset estimate
```

### Tracer interaction

```text
Space key
 -> local pose + forward vector
 -> sendTracerProjectile()
 -> provisional SharedInteraction rendered immediately
 -> reliable InteractionIntentMessage
 -> server enqueue
 -> rewind validation
 -> InteractionStartedEvent
 -> reliable SharedInteractionMessage
 -> provisional tracer reconciles by actor+sequence
 -> server fixed-step swept collision
 -> reliable InteractionCollisionMessage
 -> client impact presentation
```

### Result state

```text
accepted tracer start -> recordShot(actor)
authoritative participant collision -> recordHit(actor)
 -> ParticipantResultStateMessage reliable broadcast
 -> ParticipantResultStore
 -> ClientScoreboardOverlay
```

## Extension rules

### Adding a new interaction type

1. Define a stable type identifier. If using `InteractionType`, do not reorder existing values.
2. Implement immutable type-specific `InteractionState` if the interaction is active over time.
3. Implement `InteractionHandler`.
4. Add it to the `InteractionManager` handler list.
5. Update server transport admission in `ServerMain.handleInteractionIntent()` if the current type filter remains.
6. Decide whether existing start/collision messages are sufficient. Add new messages only when the domain event requires new replicated data.
7. If protocol changes, register new message deterministically and bump protocol version.
8. Add client presentation separately from server simulation.
9. Derive application state from authoritative server events, not client claims.

### Adding persistent server state

1. Define an immutable state value.
2. Store/mutate it only server-side.
3. Change it in response to authoritative events.
4. Replicate reliable state snapshots or revisioned deltas.
5. Send a complete snapshot to late joiners.
6. Keep derived values derived when possible.
7. Make client UI a pure consumer.

### Replacing JavaFX

Preserve neutral models and remote resolution logic. Replace only the renderer-facing classes and conversion boundary. Do not move prediction or authority into the renderer.

## Things that must not be “simplified”

Do not:

- tie motion/network update rate to JavaFX `AnimationTimer`;
- remove client/server clock synchronization because localhost appears stable;
- compare raw client and server `nanoTime` values;
- replace history/interpolation with “latest packet wins” for normal remote rendering;
- extrapolate indefinitely;
- reuse server rewind history as a client render buffer or vice versa;
- let clients submit collision/hit results;
- trust a payload actor ID instead of transport identity;
- make `ParticipantResultState` client-authoritative;
- replicate accuracy as an independent mutable field when hits/shots already define it;
- put JavaFX `Node`, `Transform`, `Point3D`, or SimEthereal/SimMath types into common protocol models;
- mutate JavaFX nodes from SpiderMonkey, SimEthereal, or scheduled-executor threads;
- reorder serializer registrations;
- reorder `InteractionType` enum values;
- lower the intentional 120 Hz server zone-update rate merely to silence SimEthereal watchdog warnings;
- filter/suppress that warning as an architectural fix.

## Known SimEthereal issue

At the intentional 120 Hz server update rate, SimEthereal 1.8.0 may report frequency/watchdog warnings. Prior analysis determined this is associated with ZoneManager `beginUpdate()/endUpdate()` cadence rather than being a reason to reduce the application's target rate.

The desired library fix is a backward-compatible configurable watchdog/expected-rate setting that preserves upstream defaults and accepts arbitrary expected rates. This repository does not currently contain that upstream patch.

## Current implementation limitations

Do not invent capabilities that are not present:

- no authentication;
- no encryption layer added by this application;
- no room/lobby/matchmaking model;
- no reconnection state restoration;
- no durable persistence;
- no damage/health/inventory/objective system;
- no server-simulated player locomotion;
- no pointer/selection handlers even though enum values exist;
- no generic replicated world entity framework beyond this demonstration;
- no long-horizon prediction;
- no rewind extrapolation.

## Before modifying protocol code

Read, in order:

1. `NetworkConstants`
2. `NetworkSerializers`
3. every affected message class
4. sender call sites
5. receiver call sites
6. `NETWORK_PROTOCOL.md`

Then determine whether `PROTOCOL_VERSION` must change.

## Before modifying motion code

Read:

- `Pose3d`
- `ClockSynchronizer`
- `LocalPosePublisher`
- `RemoteParticipantMotionState`
- `RemotePoseHistory`
- `RemoteInterpolationController`
- `RemotePosePredictor`
- `PoseReconciler`
- `SimEtherealClientAdapter`
- server publication loop in `ServerMain`

Any change that alters timestamp meaning must be traced end-to-end.

## Before modifying interaction/collision code

Read:

- `ServerRewindService`
- `WorldStateSampler`
- `InteractionManager`
- `InteractionHandler`
- `ProjectileInteractionHandler`
- `CollisionService`
- `InteractionReplicationService`
- server event observer in `ServerMain`

Keep server simulation and client presentation separate.
