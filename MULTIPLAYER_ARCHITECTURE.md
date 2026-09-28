# Multiplayer Architecture

## 1. Scope

This prototype demonstrates a small-session, shared-3D-world networking architecture using Java, JavaFX, SpiderMonkey, and SimEthereal. It intentionally leans toward competitive-FPS networking techniques where they are useful—high update rates, a synchronized server timeline, interpolation-first remote rendering, bounded extrapolation, server rewind, and server-authoritative interaction results—while remaining a compact reference implementation rather than a complete game networking framework.

The current implementation supports up to 12 participants and protocol version 5.

## 2. Architectural goals

The design separates five concerns that are often incorrectly collapsed together:

1. **Local input and motion** — responsive client-side movement independent of JavaFX render rate.
2. **Shared pose replication** — transport and SimEthereal publication of participant transforms.
3. **Remote motion presentation** — history, adaptive interpolation, bounded prediction, and correction before rendering.
4. **Authoritative interactions** — synchronized client intent followed by server validation, historical catch-up, simulation, collision, and replication.
5. **Server-owned persistent state** — reliable replication of application state derived only from authoritative server events.

JavaFX is a presentation layer. SimEthereal is a shared-object replication layer. Neither is used as the application's general-purpose simulation model.

## 3. High-level topology

```text
                         +----------------------+
                         |  Authoritative Server |
                         |                      |
                         | participant sessions |
                         | latest pose inputs   |
                         | rewind history       |
                         | world snapshots      |
                         | interaction manager  |
                         | collision service    |
                         | result-state store   |
                         +----+------------+----+
                              |            |
                 SimEthereal  |            | reliable app messages
                 state stream |            | interactions/results/lifecycle
                              |            |
          +-------------------+--+      +--+-------------------+
          | Client A             |      | Client B             |
          |                      |      |                      |
          | local motion 120 Hz  |      | local motion 120 Hz  |
          | pose input 120 Hz ---+----->| server (not peer)    |
          | clock sync           |      | clock sync           |
          | remote pose history  |      | remote pose history  |
          | interpolation/pred.  |      | interpolation/pred.  |
          | JavaFX presentation  |      | JavaFX presentation  |
          +----------------------+      +----------------------+
```

Clients never exchange application messages directly. Shared state is mediated by the server.

## 4. Authority model

### 4.1 Local motion

`CameraMotionModel` integrates local input at 120 Hz on the `local-camera-motion` thread. JavaFX renders the latest immutable `Pose3d` snapshot at its own frame rate.

The current prototype does **not** run a second server-side movement simulation that validates acceleration, velocity, obstacles, or movement commands. The server receives client pose samples, rejects stale sequence numbers, normalizes/clamps the pose, and uses the newest accepted pose for publication.

Therefore:

- local responsiveness is immediate;
- remote clients consume a server-published pose timeline;
- interaction/collision logic is server-authoritative;
- movement itself is not yet an anti-cheat authoritative simulation.

### 4.2 Interaction authority

Interaction authority is stronger:

```text
client input
    -> synchronized InteractionIntentMessage
    -> actor identity derived from HostedConnection
    -> server time/origin/parameter validation
    -> historical world sample
    -> authoritative interaction start
    -> historical catch-up
    -> fixed-step simulation
    -> authoritative collision
    -> reliable replication
```

The client may render a provisional tracer immediately, but the server broadcast reconciles that presentation by `(actorObjectId, interactionSequence)`.

### 4.3 Persistent application state

`ParticipantResultState` is server-owned. Clients never submit `shotsFired`, `hitCount`, or `score`. The server mutates these values only in response to authoritative interaction events and sends complete state snapshots reliably.

## 5. Neutral data boundary

The key cross-layer model is `Pose3d`:

```text
record Pose3d(
    double x,
    double y,
    double z,
    Quaterniond orientation,
    long sampleTimeNanos)
```

It contains no JavaFX, jME scene graph, SimEthereal, or SimMath type.

The conversion boundaries are explicit:

```text
JavaFX <-> FxPoseCodec <-> Pose3d <-> SimEthereal adapters <-> SimEthereal/SimMath
```

Renderer-neutral collision geometry follows the same pattern through `Vector3d`, `OrientedBox3d`, `CompoundShape3d`, and `SweptSphere3d`.

## 6. Client architecture

### 6.1 Composition root

`ClientMain` creates and wires:

- `NetworkRuntimeStats` / `NetworkStatsLogger`
- `RemotePoseStore`
- `SharedInteractionStore`
- `ParticipantResultStore`
- `TracerRoundManager`
- `RemoteParticipantManager`
- `ClientScoreboardOverlay`
- `CameraInputState`
- `CameraMotionModel`
- `ClientWorldView`
- `ClientStatusPane`
- `CollaborationNetworkClient`
- `ClientInteractionController`
- `LocalPosePublisher`

`ClientMain` is intentionally mostly orchestration.

### 6.2 Local path

```text
JavaFX key/mouse events
    -> CameraController
    -> CameraInputState (thread-safe neutral input)
    -> LocalPosePublisher @ 120 Hz
    -> CameraMotionModel.update()
    -> immutable Pose3d
       +-> latestPose() for JavaFX rendering
       +-> PoseInputMessage over unreliable transport
```

The network pose publication rate and motion simulation rate are separate constants even though both are currently 120 Hz.

### 6.3 Remote path

```text
SimEthereal beginFrame(serverTime)
    -> objectUpdated(SharedObject)
    -> SimEtherealClientAdapter
    -> Pose3d stamped with SimEthereal frame/server time
    -> RemotePoseStore
    -> RemoteParticipantMotionState
       -> RemotePoseHistory
       -> RemoteInterpolationController
       -> RemotePosePredictor when necessary
       -> PoseReconciler
    -> resolved Pose3d
    -> RemoteParticipantView
```

The renderer never performs interpolation or prediction itself.

## 7. Server architecture

`ServerMain.CollaborationServer` is the current server composition root. It owns:

- SpiderMonkey `Server`
- `SimEtherealServerAdapter`
- connection/session map
- `ServerRewindService`
- `WorldStateSampler`
- `CollisionService`
- `InteractionManager`
- `FixedStepInteractionLoop`
- `ServerParticipantStateStore`
- 120 Hz SimEthereal state publisher
- runtime statistics

### 7.1 Pose ingestion

For each `PoseInputMessage`:

1. resolve the session from the transport connection;
2. capture server receive time;
3. reconstruct and normalize the `Pose3d`;
4. clamp position to `WORLD_LIMIT` (+/-100 on each axis);
5. retain both client sample time and server receive time in `TimedPose3d`;
6. accept only if the client sequence is newer than the current sequence.

Client and server `System.nanoTime()` values remain distinct clock domains.

### 7.2 State publication

At `SERVER_STATE_HZ` (120 Hz):

1. begin a SimEthereal zone update using the server frame time;
2. for every participant, read the latest accepted pose;
3. record exactly that pose in the server rewind history under the frame time;
4. publish the same pose through SimEthereal;
5. end the SimEthereal zone update;
6. publish an immutable `AuthoritativeWorldSnapshot` for server interaction/collision use.

This keeps replication, rewind history, and live collision state aligned to the same authoritative server frame.

## 8. Clock synchronization

Every client maintains an NTP-style estimate relating its monotonic clock to the server's monotonic clock.

```text
client t0 -> request -> server t1
                     server t2 -> response -> client t3

roundTrip = (t3 - t0) - (t2 - t1)
offset    = ((t1 - t0) + (t2 - t3)) / 2
```

`offset` is `serverTime - clientTime`.

The client retains a rolling 32-sample window, selects up to the 8 lowest-RTT samples, and uses the median selected offset. The best RTT is retained as the current RTT estimate.

Clock synchronization is required for:

- rendering remote state against a meaningful server-time `now`;
- assigning server-time timestamps to client interaction intents.

It is deliberately separate from high-frequency pose traffic.

## 9. Remote motion policy

Each remote participant owns independent motion state:

- 500 ms authoritative pose history;
- delivery-jitter estimator;
- adaptive interpolation delay;
- bounded constant-velocity position/orientation extrapolation;
- reconciliation of small corrections;
- snap thresholds for large corrections.

Current policy:

| Parameter | Value |
|---|---:|
| Interpolation delay minimum | 8 ms |
| Interpolation delay default | 12 ms |
| Interpolation delay maximum | 30 ms |
| Jitter EWMA alpha | 1/16 |
| Jitter multiplier | 2.0 |
| Delay increase alpha | 0.35 |
| Delay decrease alpha | 0.02 |
| Maximum prediction | 25 ms |
| Reconciliation half-life | 8 ms |
| Snap distance | 5 units |
| Snap angle | 45 degrees |

Interpolation is preferred. Prediction is a short fallback. Long gaps hold the latest authoritative pose rather than extrapolating indefinitely.

## 10. Server rewind and collision

The server retains one second of published pose history per participant. `ServerRewindService.poseAt()` returns:

- exact sample;
- interpolated sample between authoritative frames;
- `TOO_OLD`;
- `TOO_NEW`;
- `PARTICIPANT_NOT_FOUND`;
- `NO_HISTORY`.

It never extrapolates.

`WorldStateSampler.sampleAt(T)` rebuilds a historical world snapshot from all participants that have valid history at `T`, using a `CollisionShapeProvider` to create renderer-neutral collision proxies.

The current participant collision proxy mirrors the visible camera arrow using three oriented boxes: body, shaft, and arrowhead.

## 11. Interaction framework

The reusable server framework consists of:

- `StartInteractionCommand`
- `InteractionManager`
- `InteractionHandler`
- `ActiveInteraction`
- `InteractionState`
- `InteractionContext`
- start/update result records
- interaction domain events
- `InteractionReplicationService`
- `FixedStepInteractionLoop`

`ProjectileInteractionHandler` is the reference implementation for `TRACER_PROJECTILE`.

Although `InteractionType` also contains `POINTER_RAY`, `SELECTION_CLEAR`, and `SELECTION_SET`, the current server only accepts and handles `TRACER_PROJECTILE`. Enum presence is not evidence of implemented behavior.

## 12. Thread ownership

### Client

| Thread/context | Owns/does |
|---|---|
| JavaFX Application Thread | Scene graph, overlays, render loop, participant view creation/removal |
| `local-camera-motion` | `CameraMotionModel.update()`, 120 Hz local pose generation/publication |
| SpiderMonkey networking threads | application message callbacks |
| SimEthereal callback thread | incoming shared-object frames/pose offers |
| `client-time-sync` | periodic reliable time-sync requests |
| `client-network-stats` | diagnostics/logging |
| `collaboration-client-connect` | initial connection startup |

Network callbacks that affect JavaFX are marshalled through `Platform.runLater`. `RemotePoseStore` and state stores are thread-safe bridges.

### Server

| Thread/context | Owns/does |
|---|---|
| SpiderMonkey networking threads | hello, pose, time sync, interaction-intent ingestion |
| `simethereal-state-publisher` | 120 Hz authoritative shared-state publication and rewind sampling |
| `authoritative-interaction-loop` | 120 Hz interaction lifecycle/simulation |
| `server-network-stats` | diagnostics/logging |

`InteractionManager.enqueue()` is the explicit transfer from network threads to the interaction thread.

## 13. Protocol boundary

All application message types are registered in one deterministic order by `NetworkSerializers.registerAll()`. The protocol version is 5.

Serializer order is shared wire state. Do not reorder or insert classes casually. See `NETWORK_PROTOCOL.md`.

Interaction type values are currently serialized as enum ordinals. Reordering `InteractionType` values is therefore also a wire-protocol change.

## 14. Rendering boundary

The JavaFX scene graph contains presentation only:

- local camera rig;
- remote participant geometry;
- tracer visuals;
- 2D projected participant labels;
- 2D scoreboard overlay;
- status/diagnostics pane.

Remote labels are not children of transformed 3D participant groups. JavaFX projects a participant-local anchor to screen coordinates and positions a normal 2D `Label` in an overlay. This keeps labels readable regardless of 3D rotation, perspective, and depth-buffer behavior.

## 15. Current completed capabilities

- multi-client localhost/LAN-addressable connection configuration;
- up to 12 participants;
- 120 Hz local motion independent of JavaFX pulse;
- 120 Hz unreliable pose input;
- 120 Hz server shared-state update loop;
- typed SimEthereal client/server adapters;
- synchronized monotonic clock mapping;
- remote history/interpolation/prediction/reconciliation;
- adaptive jitter buffering;
- server rewind history;
- renderer-neutral authoritative world snapshots;
- fixed-step authoritative interaction framework;
- synchronized tracer projectile;
- historical catch-up for delayed interaction intents;
- swept-sphere vs oriented/compound collision;
- authoritative collision replication;
- server-owned shots/hits/score state;
- late-join result-state snapshot;
- JavaFX 3D remote participant rendering;
- screen-space participant labels;
- scoreboard overlay;
- runtime rate/timing diagnostics;
- Maven development profiles and jlink/jpackage packaging profiles.

## 16. Explicitly out of scope / not implemented

The current source does not provide:

- authentication or authorization;
- encryption beyond what the underlying transport/environment supplies;
- matchmaking, lobby, room, or shard management;
- reconnection/resume semantics;
- durable persistence/database state;
- NAT traversal or relay infrastructure;
- server-simulated movement/anti-cheat movement validation;
- health, damage, inventory, objectives, or respawn systems;
- generic entity replication beyond the demonstrated participant objects;
- implemented pointer/selection interaction handlers despite enum placeholders;
- arbitrary long-horizon remote prediction;
- server rewind extrapolation.

## 17. Known integration constraint: SimEthereal at 120 Hz

The prototype intentionally runs the server's SimEthereal zone update loop at 120 Hz. Prior testing showed SimEthereal can emit watchdog/frequency warnings at this intentionally high rate. That warning concerns the library's zone-update watchdog behavior and should not be “fixed” by reducing the application's intended 120 Hz rate or by suppressing/filtering warnings. The desired library-level direction is a configurable, backward-compatible watchdog expectation while preserving upstream defaults.

Also keep SimEthereal's state-collection configuration conceptually separate from the application's `beginUpdate()/endUpdate()` zone-update cadence; they are not interchangeable timing controls.

## 18. Architecture invariants

The most important invariants are:

1. JavaFX nodes are never the authoritative network/simulation state.
2. Client and server monotonic timestamps are never compared without clock mapping.
3. Remote rendering prefers interpolation and bounds prediction.
4. Server rewind never extrapolates.
5. Interaction actor identity comes from the transport connection, not client payload.
6. Authoritative interaction simulation/collision runs on the server.
7. Persistent result state is mutated only by the server.
8. Serializer registration order and protocol version are controlled together.
9. The 120 Hz simulation/network design is intentional and should not be silently reduced to match JavaFX render rate.
10. Existing demo presentation classes may be replaced; the neutral models and timing/authority boundaries should be preserved.
