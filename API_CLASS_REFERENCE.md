# API Class Reference

## 1. Purpose

This is a subsystem-oriented reference for the current Java source tree. It is intentionally **not a duplication of Javadoc**. It answers:

- What is each source class for?
- Which subsystem owns it?
- Is it reusable infrastructure or demo/application code?
- Which classes should an integrator normally depend on directly?

The current snapshot contains **91 Java source files**.

---

## 2. Classification legend

| Label | Meaning |
|---|---|
| **Core model** | Renderer-neutral shared data/math intended to survive a port |
| **Protocol** | SpiderMonkey application wire message or serializer contract |
| **Infrastructure** | Reusable implementation of timing, synchronization, simulation, storage, collision, etc. |
| **Integration** | Boundary/composition/adaptation code likely to be adapted for a host application |
| **Demo/presentation** | JavaFX demo UI, scene, controls, or visual reference implementation |
| **Internal** | Implementation detail that normally should not be a public application API |
| **Diagnostics** | Runtime measurement/logging support |

### Visibility note

Many client classes are currently package-private. That reflects the compact prototype packaging, not necessarily their conceptual importance.

When extracting a library, do **not** make every class public. Prefer a small public facade over exposing all implementation classes.

---

# 3. Client package

Package: `com.example.collab3d.client`

## Client class table

| Class | Classification | Responsibility |
|---|---|---|
| `CameraController` | Demo/presentation + integration | Converts JavaFX key/mouse events into neutral `CameraInputState`; does not simulate motion itself. |
| `CameraInputState` | Integration | Thread-safe neutral local input state shared between FX input handling and the motion scheduler. |
| `CameraMotionModel` | Demo/reference | Plain-Java first-person motion integration producing the latest `Pose3d`; replace if the host application already owns movement. |
| `ClientConnectionConfig` | Integration | Immutable host/TCP/UDP runtime connection configuration parsed from JavaFX named parameters. |
| `ClientInteractionController` | Integration + presentation | Connects local input (Space/fire), network interaction requests, provisional tracer presentation, authoritative start/collision updates, and per-frame tracer updates. |
| `ClientLauncher` | Demo/packaging | Non-`Application` launcher entry point useful for packaged JavaFX startup; delegates to `ClientMain`. |
| `ClientMain` | Demo/composition | JavaFX application composition root wiring world, input, networking, local motion, remote rendering, interactions, diagnostics, scoreboard, and lifecycle. |
| `ClientScoreboardOverlay` | Demo/presentation | 2D overlay that renders replicated `ParticipantResultState` data and derives accuracy/sort order. |
| `ClientStatusPane` | Demo/presentation | JavaFX diagnostics/controls/status UI for the demo client. |
| `ClientWorldView` | Demo/presentation | Builds the demo 3D `SubScene`, camera rig, participant/effect layers, and 2D overlay stack. |
| `ClockSynchronizer` | Infrastructure | NTP-like client/server monotonic clock offset and RTT estimator with low-RTT sample filtering. |
| `CollaborationNetworkClient` | Integration | Client networking facade for SpiderMonkey connection, app messages, SimEthereal client adapter, clock sync, pose send, interaction intent, callbacks, and lifecycle. |
| `FxPoseCodec` | Integration | Converts neutral pose/quaternion data to/from JavaFX transforms; renderer boundary. |
| `LocalPosePublisher` | Infrastructure + integration | Dedicated scheduled local motion loop plus phase-controlled high-rate pose publication; independent from JavaFX pulse. |
| `ParticipantResultStore` | Infrastructure | Thread-safe client replica of latest server-owned `ParticipantResultState` values. |
| `PoseReconciler` | Infrastructure | Smooths small remote correction errors and snaps large position/orientation discontinuities. |
| `RemoteInterpolationController` | Infrastructure | Per-participant adaptive interpolation-delay controller using arrival-vs-sample timing jitter. |
| `RemoteParticipantLabelOverlay` | Demo/presentation | Projects remote 3D participant anchors into a 2D JavaFX overlay for readable name labels. |
| `RemoteParticipantManager` | Integration + presentation | Owns remote participant views/labels and applies resolved remote poses every frame. |
| `RemoteParticipantMotionState` | Infrastructure | Per-remote-participant composition of history, adaptive delay, interpolation, bounded extrapolation, hold, and reconciliation. |
| `RemoteParticipantView` | Demo/presentation | JavaFX 3D reference avatar/direction visualization; applies final poses but owns no network smoothing. |
| `RemotePoseHistory` | Infrastructure | Bounded ordered history of server-timestamped remote poses with bracket lookup and stale-sample handling. |
| `RemotePosePredictor` | Infrastructure | Short-horizon constant-velocity and quaternion-delta extrapolator for remote rendering. |
| `RemotePoseStore` | Infrastructure | Thread-safe mapping from SimEthereal object IDs to remote participant motion state; records local arrival time separately from server sample time. |
| `SharedInteractionStore` | Infrastructure | Client-side latest shared-interaction state keyed/filtered by actor interaction sequence. |
| `TracerRoundManager` | Demo/presentation + integration | JavaFX tracer/impact presentation, provisional-authoritative reconciliation, deterministic server-time-based tracer position, collision visualization. |

---

## 3.1 Key client integration API: `CollaborationNetworkClient`

Treat this class as the current **reference client facade**, even though its visibility is package-local in the demo.

Primary responsibilities:

```text
connect/disconnect
identity/session callbacks
participant metadata callbacks
pose publication
clock synchronization
time mapping
interaction intent send
interaction/collision receive
participant result-state receive
SimEthereal state integration
```

For a reusable library, a public facade should expose these capabilities while hiding SpiderMonkey/SimEthereal lifecycle details.

---

## 3.2 Key timing API: `ClockSynchronizer`

Important conceptual operations:

- create/send time-sync request state,
- accept `t0/t1/t2/t3` sample,
- filter rolling samples,
- expose whether an estimate exists,
- expose current RTT/offset diagnostics,
- convert client monotonic time to server time,
- convert server time back to client time,
- reset on reconnect.

Do not expose raw internal sample arrays as application API.

---

## 3.3 Remote motion stack

The classes are intentionally layered:

```text
RemotePoseStore
    |
RemoteParticipantMotionState
    +-- RemotePoseHistory
    +-- RemoteInterpolationController
    +-- RemotePosePredictor
    +-- PoseReconciler
```

Do not collapse this into `RemoteParticipantView` unless renderer coupling is an explicit new design goal.

---

## 3.4 `LocalPosePublisher`

This is more than a timer around `sendPose()`.

It establishes the pattern:

```text
thread-safe input
   -> high-rate local simulation
   -> latest neutral pose
   -> immediate render consumption
   -> independently rate-controlled network publication
```

A target application can replace `CameraMotionModel` while retaining this scheduling/publication pattern.

---

# 4. Common package

Package: `com.example.collab3d.common`

| Class | Classification | Responsibility |
|---|---|---|
| `NetworkConstants` | Core configuration | Shared protocol version, ports, rate/timing constants, world bounds, interpolation/rewind/tracer defaults. |
| `NetworkRuntimeStats` | Diagnostics | Thread-safe passive hot-path counters/gauges for client/server timing, pose, remote render, clock, and rewind behavior. |
| `NetworkSerializers` | Protocol | Registers all application messages in a fixed shared order; idempotent per JVM. |
| `NetworkStatsLogger` | Diagnostics | Periodically snapshots runtime stats and logs calculated operational rates from its own daemon thread. |
| `ParticipantInfo` | Core model | Immutable participant metadata: object ID, display name, hue. |
| `ParticipantResultState` | Core model | Immutable server-owned result snapshot: object ID, shots, hits, score; accuracy derived. |
| `Pose3d` | Core model | Immutable neutral position/orientation/sample-time pose with normalization/clamping/interpolation helpers. |
| `Quaterniond` | Core model | Immutable renderer-neutral quaternion math used by pose interpolation/prediction/conversion. |
| `TimedPose3d` | Core model | Server-side accepted client pose plus sequence and separate client-sample/server-receive clock-domain times. |

---

## 4.1 `Pose3d`

This is one of the most important reusable types.

Use it at subsystem boundaries instead of renderer-native transforms.

Expected roles:

- local simulated pose,
- server participant pose,
- remote replicated pose,
- rewind pose,
- renderer input.

Its `sampleTimeNanos` must always be interpreted according to the path that produced it. Remote SimEthereal samples use the server/shared timeline.

---

## 4.2 `TimedPose3d`

Do not reduce this to one timestamp. The separation is deliberate:

```text
clientSampleTimeNanos  -> client's monotonic domain
serverReceiveTimeNanos -> server's monotonic domain
```

That prevents accidental cross-clock comparisons and preserves diagnostics/validation options.

---

## 4.3 `NetworkRuntimeStats`

This class is passive instrumentation. It must not become a hidden control loop.

The current nested `Snapshot`/window data supports rate calculations for:

- client motion ticks,
- pose send attempts/sends/skips,
- SimEthereal frames/object updates,
- render frames,
- interpolation/extrapolation/hold counts,
- extrapolation durations,
- history depth/span,
- interpolation delay/jitter,
- time sync RTT/offset,
- server pose receive/accept/reject,
- server state frames,
- rewind query outcomes/history.

---

# 5. Neutral geometry package

Package: `com.example.collab3d.common.geometry`

| Class | Classification | Responsibility |
|---|---|---|
| `CollisionResult` | Core model | Earliest collision result including hit identity/geometry/fraction as defined by collision service. |
| `CollisionShape3d` | Core model/API | Marker/contract for renderer-neutral server collision shapes. |
| `CompoundShape3d` | Core model | Aggregate collision shape composed of child shapes. |
| `OrientedBox3d` | Core model | Renderer-neutral oriented box hit volume. |
| `SweptSphere3d` | Core model | Moving sphere segment used for continuous projectile/tracer collision tests. |
| `Vector3d` | Core model | Small immutable neutral 3D vector math type used by interaction/collision code. |

These classes intentionally do not depend on JavaFX `Point3D`, `Bounds`, `Box`, or `MeshView`.

---

# 6. Shared interaction model package

Package: `com.example.collab3d.common.interactions`

| Class | Classification | Responsibility |
|---|---|---|
| `InteractionCollision` | Core model | Immutable client-consumable authoritative collision fact. |
| `InteractionType` | Core model + protocol-sensitive | Enumerates interaction kinds; current ordinal is encoded on the wire. |
| `SharedInteraction` | Core model | Immutable accepted/replicated interaction descriptor independent of renderer/server handler implementation. |

### `InteractionType` compatibility warning

Current order:

```text
POINTER_RAY
SELECTION_CLEAR
SELECTION_SET
TRACER_PROJECTILE
```

Because the wire protocol currently serializes `ordinal()`, do not reorder these values.

---

# 7. Application message package

Package: `com.example.collab3d.common.messages`

All classes in this package are **Protocol** classes.

| Message | Responsibility |
|---|---|
| `ClientHelloMessage` | Client registration request carrying requested display name. |
| `AssignedIdentityMessage` | Server response assigning the client's object ID and approved display name. |
| `ParticipantInfoMessage` | Server replication of participant metadata. |
| `ParticipantRemovedMessage` | Server participant lifecycle removal notification. |
| `PoseInputMessage` | High-rate client pose input with client sequence/sample time; sent unreliably. |
| `ServerNoticeMessage` | Human-readable server status/notice text. |
| `TimeSyncRequestMessage` | Client `t0`/sequence clock-sync request. |
| `TimeSyncResponseMessage` | Server `t1/t2` response preserving client request information. |
| `InteractionIntentMessage` | Reliable client interaction intent with synchronized event time and request parameters. |
| `SharedInteractionMessage` | Reliable server replication of an accepted authoritative interaction. |
| `InteractionCollisionMessage` | Reliable server replication of authoritative interaction collision. |
| `ParticipantResultStateMessage` | Reliable server replication of current participant shots/hits/score state. |

See `NETWORK_PROTOCOL.md` for exact fields, direction, and reliability.

---

# 8. Server root package

Package: `com.example.collab3d.server`

| Class | Classification | Responsibility |
|---|---|---|
| `RewindPoseResult` | Core/server API | Explicit result of historical pose lookup, including exact/interpolated/unavailable status and available time bounds. |
| `ServerMain` | Demo/composition + integration | Executable server composition root; owns transport, sessions, SimEthereal, pose ingestion/publication, interactions, result policy, and lifecycle. |
| `ServerParticipantStateStore` | Infrastructure/Internal | Server-only mutable store for participant shots/hits/score and immutable snapshots. |
| `ServerPoseHistory` | Infrastructure/Internal | Per-participant bounded authoritative server-time pose history used by rewind. |
| `ServerRewindService` | Infrastructure | Public service coordinating per-participant history registration, recording, historical queries, cleanup, and diagnostics. |

---

## 8.1 `RewindPoseResult`

Statuses:

```text
EXACT
INTERPOLATED
TOO_OLD
TOO_NEW
PARTICIPANT_NOT_FOUND
NO_HISTORY
```

`available()` is true only for `EXACT` or `INTERPOLATED`.

This explicit result type is preferable to returning a guessed/extrapolated authoritative pose.

---

## 8.2 `ServerRewindService`

This is a strong reusable server API candidate.

Its conceptual contract:

```text
register participant
record authoritative pose at server frame time
query pose at historical server time
remove participant
report history diagnostics
```

The service deliberately does not extrapolate beyond recorded history.

---

# 9. Server collision package

Package: `com.example.collab3d.server.collision`

| Class | Classification | Responsibility |
|---|---|---|
| `AuthoritativeEntityState` | Core/server model | Entity ID + authoritative pose + neutral collision shape used by interaction simulation. |
| `AuthoritativeWorldSnapshot` | Core/server model | Immutable authoritative world sample at a server timestamp. |
| `CollisionFilter` | Core/server API | Predicate/strategy controlling which candidate entities can be hit by a collision query. |
| `CollisionService` | Infrastructure | Renderer-neutral collision engine; finds earliest swept-sphere hit against supported shapes. |
| `CollisionShapeProvider` | Integration/API | Maps authoritative entity/participant pose into application-specific neutral collision geometry. |
| `ParticipantCollisionShapeProvider` | Demo/reference | Collision shape provider approximating the demo remote participant arrow/body model with compound boxes. |
| `WorldStateSampler` | Infrastructure | Publishes latest immutable server world snapshot and reconstructs historical snapshots through rewind + shape provider. |

---

## 9.1 `CollisionShapeProvider`

This is the primary replacement point when porting to a new application's avatars/entities.

Keep collision geometry independent from renderer nodes.

Possible target implementations:

- capsule approximation,
- oriented body/head boxes,
- compound hit zones,
- application-specific static geometry.

---

## 9.2 `WorldStateSampler`

This class is the bridge among:

```text
authoritative participant pose
        +
server rewind
        +
collision shape provider
        |
        v
AuthoritativeWorldSnapshot
```

Interaction handlers should use this service instead of querying server session internals directly.

---

# 10. Server interaction package

Package: `com.example.collab3d.server.interaction`

| Class | Classification | Responsibility |
|---|---|---|
| `ActiveInteraction` | Infrastructure/Internal | Interaction-manager-owned active instance combining identity/type/state/lifecycle needed for fixed-step updates. |
| `FixedStepInteractionLoop` | Infrastructure | Dedicated authoritative fixed-step loop driving `InteractionManager`; `AutoCloseable`. |
| `InteractionCollisionEvent` | Core/server event | Internal authoritative event describing an interaction collision. |
| `InteractionCompletedEvent` | Core/server event | Internal authoritative event describing interaction completion and reason. |
| `InteractionCompletionReason` | Core/server model | Completion reason enum: expired, collision, actor cancel, invalidated, server shutdown. |
| `InteractionContext` | Infrastructure/API | Dependency bundle/services made available to interaction handlers. |
| `InteractionEvent` | Core/server API | Common contract for authoritative internal interaction lifecycle events. |
| `InteractionHandler` | Core/server extension API | Strategy contract for type-specific start/catch-up/fixed-step interaction semantics. |
| `InteractionLifecycleState` | Internal model | Active/completed/cancelled lifecycle classification. |
| `InteractionManager` | Infrastructure | Command queue, handler registry, server ID allocation, active interaction ownership, updates, event dispatch, completion removal. |
| `InteractionRejectReason` | Core/server model | Rejection enum: none, invalid parameters, too old, too far future, actor not found, unsupported type. |
| `InteractionReplicationService` | Integration | Observes authoritative interaction events and maps selected events to reliable network messages. |
| `InteractionStartResult` | Core/server model | Handler start result: accepted flag, initial state, reject reason, emitted events. |
| `InteractionStartedEvent` | Core/server event | Internal authoritative accepted-start event carrying `SharedInteraction`. |
| `InteractionState` | Core/server extension API | Marker/contract for type-specific authoritative interaction state. |
| `InteractionUpdateResult` | Core/server model | Handler update result: next state, completion flag/reason, emitted events. |
| `ProjectileInteractionConfiguration` | Integration/config | Immutable tracer/projectile server simulation parameters. |
| `ProjectileInteractionHandler` | Infrastructure/reference | Full authoritative tracer handler: timing validation, rewind-origin validation, historical catch-up, live swept collision, completion. |
| `ProjectileInteractionState` | Core/server model | Immutable active projectile state used by the projectile handler. |
| `StartInteractionCommand` | Core/server command | Transport-to-simulation command carrying one requested interaction start without a SpiderMonkey connection dependency. |

---

## 10.1 `InteractionHandler`

This is the main interaction extension point.

A handler owns one interaction type's semantics, including:

- start validation,
- initial state creation,
- historical catch-up,
- fixed-step updates,
- emitted authoritative events.

It should not own:

- SpiderMonkey connection lifecycle,
- JavaFX nodes,
- scoreboard/HUD presentation.

---

## 10.2 `InteractionManager`

This is the central runtime owner of active interactions.

Important design properties:

- incoming starts cross threads through a concurrent command queue,
- active map is interaction-thread-owned,
- handler selection is by `InteractionType`,
- server interaction IDs are allocated centrally,
- internal events can be observed by multiple policies/services,
- completed interactions are removed after lifecycle processing.

---

## 10.3 `InteractionStartResult`

Fields:

```text
accepted
initialState
rejectReason
events
```

Convenience rejection construction keeps failed starts from becoming active interactions.

---

## 10.4 `InteractionUpdateResult`

Fields:

```text
nextState
complete
completionReason
events
```

A handler can update its immutable/type-specific state and emit zero or more lifecycle events on each step.

---

## 10.5 Completion reasons

`InteractionCompletionReason` currently defines:

```text
EXPIRED
COLLISION
CANCELLED_BY_ACTOR
INVALIDATED
SERVER_SHUTDOWN
```

Not all current reasons are necessarily exercised by the tracer handler, but they establish a generalized lifecycle vocabulary.

---

## 10.6 Reject reasons

`InteractionRejectReason` currently defines:

```text
NONE
INVALID_PARAMETERS
TOO_OLD
TOO_FAR_IN_FUTURE
ACTOR_NOT_FOUND
UNSUPPORTED_TYPE
```

A future external API may choose to replicate some rejection information back to the requesting client; the current tracer network flow primarily replicates accepted authoritative starts/collisions.

---

# 11. SimEthereal adapter package

Package: `com.example.collab3d.simethereal`

| Class | Classification | Responsibility |
|---|---|---|
| `SimEtherealClientAdapter` | Integration/infrastructure | Typed client-side SimEthereal service/listener adapter; converts shared object updates to neutral `Pose3d` stamped with server/shared frame time. |
| `SimEtherealConfig` | Internal configuration | Centralizes zone/grid/bit/radius settings used by SimEthereal adapters. |
| `SimEtherealServerAdapter` | Integration/infrastructure | Typed server-side SimEthereal host/ZoneManager adapter; service install, object hosting, frame bracketing, transform publication. |

---

## 11.1 SimEthereal boundary rule

The adapter package should remain the primary location that knows concrete:

- SimEthereal classes,
- SimMath `Vec3d` / `Quatd`,
- zone/state configuration details.

Most application/networking code should exchange `Pose3d` and other neutral types.

---

# 12. Suggested application-facing API surface

If this prototype is extracted into a library, an application should not need to instantiate all 91 classes directly.

A sensible **public** surface would likely include concepts such as:

```text
MultiplayerClient / CollaborationClient facade
MultiplayerServer / CollaborationServer facade
Client/Server configuration records
Pose3d / Quaterniond
ParticipantInfo
ParticipantResultState
SharedInteraction
InteractionCollision
InteractionType
Clock/timing diagnostics snapshot interfaces
InteractionHandler extension interface (server)
InteractionContext (server)
CollisionShapeProvider (server)
ServerRewindService or a narrower rewind interface
```

Keep these behind the facade/internal boundary unless advanced integrators need them:

```text
RemotePoseHistory
RemoteInterpolationController
RemotePosePredictor
PoseReconciler
RemoteParticipantMotionState
ServerPoseHistory
ActiveInteraction
ProjectileInteractionState
SimEthereal configuration internals
raw SpiderMonkey listeners
```

---

# 13. Demo-only/reference implementation boundary

The following are especially likely to be replaced rather than copied unchanged:

```text
ClientMain
ClientLauncher
ClientWorldView
ClientStatusPane
CameraController
CameraMotionModel
RemoteParticipantView
RemoteParticipantLabelOverlay
ClientScoreboardOverlay
TracerRoundManager's JavaFX node implementation
ServerMain's executable/composition shell
ParticipantCollisionShapeProvider's exact demo hit geometry
```

They remain useful as reference integrations for the reusable infrastructure.

---

# 14. Dependency direction

A healthy dependency shape for a library extraction is:

```text
                 application/demo
                       |
        +--------------+--------------+
        |                             |
   client/server                  renderer adapters
   integration                        |
        |                             |
        +------------+----------------+
                     |
                infrastructure
       timing / rewind / interactions
          collision / state stores
                     |
                     v
                 core models
                     |
                     v
               protocol schemas
```

SimEthereal and SpiderMonkey should remain behind integration/adaptation boundaries rather than leaking into every application class.

---

# 15. What not to expose as “the API”

Do not equate “all public Java classes” with “application API.”

In particular:

- network message classes are protocol schema, not the preferred high-level client API,
- `ServerMain`/`ClientMain` are executable composition roots, not reusable facades,
- JavaFX view classes are presentation examples,
- internal interaction state/result records support extensibility but should not all be required by ordinary clients,
- diagnostics counters should remain passive and optional.

The intended reusable API is the set of stable abstractions and facades that preserve the architecture without forcing host applications to adopt demo composition.

---

# 16. Cross-reference by task

| Task | Primary classes |
|---|---|
| Connect a client | `CollaborationNetworkClient`, `ClientConnectionConfig`, `NetworkSerializers` |
| Publish local motion | `LocalPosePublisher`, local pose source, `Pose3d` |
| Synchronize clocks | `ClockSynchronizer`, time-sync messages |
| Receive remote state | `SimEtherealClientAdapter`, `RemotePoseStore` |
| Smooth remote rendering | `RemoteParticipantMotionState`, `RemotePoseHistory`, `RemoteInterpolationController`, `RemotePosePredictor`, `PoseReconciler` |
| Render remote JavaFX participant | `RemoteParticipantManager`, `RemoteParticipantView`, `FxPoseCodec` |
| Add projected names | `RemoteParticipantLabelOverlay` |
| Send interaction intent | `CollaborationNetworkClient`, `InteractionIntentMessage` |
| Simulate interaction | `InteractionManager`, `FixedStepInteractionLoop`, `InteractionHandler`, `InteractionContext` |
| Rewind participants | `ServerRewindService`, `RewindPoseResult` |
| Build historical collision world | `WorldStateSampler`, `CollisionShapeProvider` |
| Perform collision | `CollisionService`, neutral geometry classes |
| Implement tracer | `ProjectileInteractionHandler`, `ProjectileInteractionState`, `ProjectileInteractionConfiguration` |
| Replicate interaction results | `InteractionReplicationService`, shared interaction/collision messages |
| Maintain scores/results | `ServerParticipantStateStore`, `ParticipantResultState`, `ParticipantResultStateMessage`, `ParticipantResultStore` |
| Show scoreboard | `ClientScoreboardOverlay` |
| Diagnose timing/rates | `NetworkRuntimeStats`, `NetworkStatsLogger` |

---

# 17. Source-of-truth note

Use the current Java source for exact constructors, method signatures, and visibility.

This document captures architectural roles. It intentionally does not freeze every current implementation signature as a permanent public API contract.
