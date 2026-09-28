# Multiplayer Prototype Porting Checklist

## 1. Purpose

Use this checklist when moving the prototype's multiplayer architecture into another Java/JavaFX 3D application or extracting it into a reusable library.

Do **not** copy the entire demo blindly. Preserve the architectural boundaries and replace demo-specific composition/presentation where the target application already has equivalents.

---

## 2. Establish the target authority model first

- [ ] Decide whether target movement remains client-produced/server-published like the prototype or becomes fully server-simulated.
- [ ] Keep interactions/collision/results server authoritative.
- [ ] Identify all future server-owned state domains: health, score, inventory, objectives, etc.
- [ ] Decide whether participant state survives disconnect/reconnect.
- [ ] Decide whether state survives server restart.
- [ ] Define persistent identity separately from transient network connection/object ID if needed.

### Current prototype authority

| Domain | Current authority |
|---|---|
| Local movement generation | client |
| Pose sequence acceptance/world clamp | server |
| Published shared pose timeline | server |
| Clock timeline | server mapping estimated by clients |
| Interaction acceptance | server |
| Interaction simulation | server |
| Collision | server |
| Rewind | server |
| Shots/hits/score | server |
| JavaFX visuals | client presentation only |

---

## 3. Dependencies

- [ ] Java 21 or adapt build/source level intentionally.
- [ ] `com.simsilica:sim-ethereal:1.8.0`.
- [ ] `com.simsilica:sim-math:1.6.0`.
- [ ] `org.jmonkeyengine:jme3-networking:3.9.0-stable`.
- [ ] `org.jmonkeyengine:jme3-core:3.9.0-stable`.
- [ ] JavaFX modules required by the target client.
- [ ] SLF4J/logging binding appropriate to deployment.
- [ ] Preserve dependency convergence/exclusions if using the same library mix.

Review the source `pom.xml` rather than copying dependency versions from stale prose documentation.

---

## 4. Core neutral model to retain

Strong candidates to copy/extract with little application-specific change:

- [ ] `Pose3d`
- [ ] `Quaterniond`
- [ ] `TimedPose3d`
- [ ] `ParticipantInfo`
- [ ] `ParticipantResultState`
- [ ] neutral geometry under `common.geometry`
- [ ] `SharedInteraction`
- [ ] `InteractionCollision`
- [ ] `InteractionType` with protocol compatibility care
- [ ] `NetworkConstants` concepts, with target-specific values reviewed
- [ ] `NetworkSerializers` protocol registration pattern

---

## 5. Client timing/motion infrastructure to retain or adapt

- [ ] `ClockSynchronizer`
- [ ] `RemotePoseHistory`
- [ ] `RemoteInterpolationController`
- [ ] `RemotePosePredictor`
- [ ] `PoseReconciler`
- [ ] `RemoteParticipantMotionState`
- [ ] `RemotePoseStore`
- [ ] `LocalPosePublisher` pattern
- [ ] client/server monotonic clock-domain separation
- [ ] adaptive interpolation delay
- [ ] bounded prediction horizon
- [ ] reconciliation snap thresholds

Do not move this logic into JavaFX node classes.

---

## 6. SimEthereal boundary

- [ ] Retain typed `SimEtherealClientAdapter` / `SimEtherealServerAdapter` pattern.
- [ ] Keep concrete SimEthereal/SimMath types localized to the adapter package where practical.
- [ ] Configure state collection rate from the intended server rate.
- [ ] Preserve server `beginFrame` / entity updates / `endFrame` framing.
- [ ] Preserve server/shared frame timestamps on client `objectUpdated` samples.
- [ ] Do not regress to reflection-based optional adapters from older README/validation text.
- [ ] Do not lower the 120 Hz server target merely to silence ZoneManager watchdog warnings.
- [ ] If patching SimEthereal watchdog expectations, make expected cadence configurable while preserving existing library defaults for backward compatibility.

---

## 7. Protocol compatibility

- [ ] Keep `NetworkConstants.PROTOCOL_VERSION` synchronized across builds.
- [ ] Register the same message classes in the same order on both sides.
- [ ] Treat serializer registration changes as protocol changes.
- [ ] Do not reorder current `InteractionType` enum values while ordinal encoding is used.
- [ ] Prefer explicit stable IDs before the protocol grows significantly.
- [ ] Decide reliable vs unreliable delivery deliberately.
- [ ] Keep pose input unreliable unless architecture changes justify otherwise.
- [ ] Keep discrete authoritative interaction/results reliably replicated.
- [ ] Add late-join snapshot messages for new persistent state.

Current serializer order is documented in `NETWORK_PROTOCOL.md`.

---

## 8. Client integration

- [ ] Create/configure network client outside JavaFX visual classes.
- [ ] Register serializers before connecting.
- [ ] Install SimEthereal client service before normal shared-state processing.
- [ ] Send `ClientHelloMessage` after connection.
- [ ] Store assigned local object ID.
- [ ] Maintain participant metadata separately from pose state.
- [ ] Start periodic clock synchronization.
- [ ] Supply a neutral local `Pose3d` source.
- [ ] Publish local pose at intended network rate.
- [ ] Keep local rendering immediate.
- [ ] Resolve remote participants at `serverNow - adaptiveDelay`.
- [ ] Interpolate first, extrapolate only within the configured bound.
- [ ] Apply reconciliation after remote pose resolution.
- [ ] Marshal JavaFX node changes to the FX thread.
- [ ] Reconcile provisional interactions with authoritative start messages.
- [ ] Treat collision messages as authoritative presentation inputs.
- [ ] Maintain client replicas of persistent state.

---

## 9. Existing application local movement

If the target already owns movement:

- [ ] Do not import `CameraMotionModel` simply because the demo uses it.
- [ ] Write an adapter from the application's movement/avatar state to `Pose3d`.
- [ ] Decide which thread owns that pose.
- [ ] Make the latest pose safely readable by the publisher/render loop.
- [ ] Preserve sequence/timestamp semantics.
- [ ] If movement is fully server authoritative, replace `PoseInputMessage` semantics with input/command messages rather than pretending client poses are server simulation.

---

## 10. Remote rendering integration

- [ ] Create a remote participant visual layer in the existing 3D scene.
- [ ] Map each remote object ID to one presentation object.
- [ ] Apply resolved `Pose3d` through a renderer adapter such as `FxPoseCodec`.
- [ ] Keep interpolation/prediction outside the visual object.
- [ ] Remove visual nodes on participant removal.
- [ ] Add a 2D overlay layer for nameplates if desired.
- [ ] Project 3D anchors to screen, then screen to overlay coordinates.
- [ ] Do not parent ordinary readable name labels under rotating 3D avatar transforms unless true world-space/billboard text is intentionally desired.

---

## 11. Server integration

- [ ] Register serializers before application message exchange.
- [ ] Create SpiderMonkey server.
- [ ] Install SimEthereal server service.
- [ ] Install connection/message listeners.
- [ ] Start transport.
- [ ] Create participant/session storage.
- [ ] Create rewind service.
- [ ] Create collision shape provider.
- [ ] Create world-state sampler.
- [ ] Create collision service.
- [ ] Create interaction context/handlers/manager.
- [ ] Attach replication observer.
- [ ] Attach server-owned state policy observer.
- [ ] Start authoritative fixed-step interaction loop.
- [ ] Start state publisher.
- [ ] Ensure publication frame aligns SimEthereal + rewind + latest world snapshot.

---

## 12. Stronger movement authority, if required

The prototype does not independently simulate participant locomotion on the server.

If the target requires competitive anti-cheat movement authority:

- [ ] Replace pose-input authority with input/command messages.
- [ ] Simulate movement on server.
- [ ] Acknowledge command/input sequence as needed.
- [ ] Publish server simulation poses through the existing state path.
- [ ] Record those same poses in rewind.
- [ ] Add local client prediction/replay of unacknowledged inputs if needed.
- [ ] Keep remote interpolation path separate from local input prediction/reconciliation.

Do not call `RemotePosePredictor` a substitute for local input replay.

---

## 13. Rewind

- [ ] Record poses in server monotonic time.
- [ ] Record the same state that is published to clients.
- [ ] Bound rewind memory/time horizon.
- [ ] Return explicit “too old/too new/no history” results.
- [ ] Interpolate inside known history.
- [ ] Do **not** extrapolate authoritative rewind.
- [ ] Define how interactions behave when required history is unavailable.

---

## 14. Interaction framework

- [ ] Network messages express intent, not authoritative hit results.
- [ ] Actor identity comes from the server session.
- [ ] Network callback enqueues commands rather than running heavy simulation.
- [ ] Fixed-step interaction thread owns active interaction state.
- [ ] Implement each type through `InteractionHandler`.
- [ ] Validate synchronized event time.
- [ ] Validate submitted origin/direction/target data as appropriate.
- [ ] Reconstruct authoritative origin/state from server data.
- [ ] Catch up through historical snapshots when appropriate.
- [ ] Use renderer-neutral collision shapes.
- [ ] Emit internal authoritative lifecycle events.
- [ ] Replicate only client-needed authoritative events.

---

## 15. New interaction type checklist

- [ ] Append a protocol-stable interaction type value.
- [ ] Determine whether current intent message fields are sufficient.
- [ ] Add a new message instead of overloading unrelated fields if needed.
- [ ] Implement handler start validation.
- [ ] Define active state model.
- [ ] Define fixed-step update behavior.
- [ ] Define collision/target policy.
- [ ] Define catch-up/rewind policy.
- [ ] Define completion reasons.
- [ ] Define authoritative events.
- [ ] Define client replication needs.
- [ ] Define provisional local presentation if useful.
- [ ] Define any persistent state mutations separately.

---

## 16. Server-owned state

For every new persistent-within-session state domain:

- [ ] Server is sole authoritative writer.
- [ ] Shared state representation is immutable.
- [ ] Mutation occurs from authoritative server events/policies.
- [ ] Client receives snapshot/delta but cannot arbitrarily set it.
- [ ] Replication is reliable where convergence matters.
- [ ] Late join receives current state.
- [ ] Disconnect behavior is explicit.
- [ ] Restart persistence behavior is explicit.
- [ ] Derived values are derived instead of redundantly stored where practical.
- [ ] UI remains downstream from client replica.

---

## 17. Collision geometry

- [ ] Replace demo `ParticipantCollisionShapeProvider` if target avatar geometry differs.
- [ ] Keep collision geometry renderer-neutral.
- [ ] Verify coordinate conventions between visual and collision models.
- [ ] Define self/team/friendly-fire filters.
- [ ] Test earliest-hit behavior.
- [ ] Test rotated boxes/compound shapes as required.
- [ ] Keep server collision independent of JavaFX scene bounds.

---

## 18. Timing configuration

Review all timing values as a coherent system rather than independently.

Current baseline:

```text
client motion                 120 Hz
client pose publication       120 Hz
server shared-state frame     120 Hz
server interaction loop       120 Hz
remote baseline delay          12 ms
remote delay range           8-30 ms
remote prediction max          25 ms
remote history                500 ms
server rewind history        1000 ms
clock sync interval          1000 ms
tracer lifetime               500 ms
```

- [ ] Confirm target CPU/network budget.
- [ ] Confirm target WAN/LAN assumptions.
- [ ] Measure jitter before retuning interpolation.
- [ ] Keep prediction bounded.
- [ ] Keep enough rewind history for allowed interaction latency.
- [ ] Keep interaction catch-up bound explicit.

---

## 19. Threading assumptions

- [ ] JavaFX scene graph is mutated only on FX thread.
- [ ] Local motion has a clear owner thread.
- [ ] Pose publication does not depend on JavaFX pulse.
- [ ] Network callbacks do minimal work and hand off simulation commands.
- [ ] Interaction active state has one owner thread.
- [ ] Server state publisher owns each publication frame.
- [ ] Immutable snapshots cross subsystem boundaries.
- [ ] Thread-safe stores are used only where cross-thread access is actually required.
- [ ] Shutdown terminates scheduled executors/loops cleanly.

---

## 20. Lifecycle / initialization order

### Client

```text
serializers
 -> client transport
 -> SimEthereal client service
 -> listeners
 -> connect/start
 -> hello/identity
 -> clock sync
 -> local publisher
 -> render-loop consumption
```

- [ ] Respect this dependency order.

### Server

```text
serializers
 -> server transport
 -> SimEthereal service
 -> listeners
 -> start
 -> rewind/world/collision
 -> interaction manager/policy
 -> interaction loop
 -> state publisher
```

- [ ] Ensure interaction dependencies exist before accepting interaction work.

---

## 21. Shutdown order

### Client

- [ ] Stop local pose/motion publisher.
- [ ] Stop time-sync scheduling.
- [ ] Stop/remove presentation callbacks as required.
- [ ] Close network client/SimEthereal service cleanly.
- [ ] Do not leave executor threads alive after application exit.

### Server

- [ ] Stop accepting new application work.
- [ ] Stop interaction loop.
- [ ] Stop state publisher.
- [ ] Stop stats/logging.
- [ ] Stop hosted/session services.
- [ ] Close server transport.

---

## 22. Networking configuration

- [ ] Bind host/interface appropriate to deployment.
- [ ] Configure TCP port (prototype default 6143).
- [ ] Configure UDP port (prototype default 6144).
- [ ] Confirm firewall rules.
- [ ] Do not hard-code internal development addresses into reusable source/docs/build artifacts.
- [ ] Decide discovery/connect UX.
- [ ] Decide whether direct internet/NAT traversal is in scope.
- [ ] Decide whether TLS/VPN/other deployment security is required.

The current prototype is not a matchmaking/NAT traversal system.

---

## 23. Packaging

The current Maven build contains development run profiles and jlink/jpackage-oriented native profiles.

- [ ] Separate client and server launchers.
- [ ] Ensure JavaFX modules are included only where needed.
- [ ] Verify SimEthereal/jME modules/dependencies in the runtime image.
- [ ] Verify native launchers use the correct main class.
- [ ] Test packaged client/server, not only IDE execution.
- [ ] Keep command-line host/name configuration available for client launchers.
- [ ] Avoid embedding environment-specific addresses in packaged defaults.

---

## 24. Demo-specific classes to replace first

Likely application-specific/reference implementation:

- [ ] `ClientMain`
- [ ] `ClientWorldView`
- [ ] `ClientStatusPane`
- [ ] `CameraController`
- [ ] `CameraMotionModel` if the application already has movement
- [ ] `RemoteParticipantView`
- [ ] `RemoteParticipantLabelOverlay`
- [ ] `ClientScoreboardOverlay`
- [ ] JavaFX portion of `TracerRoundManager`
- [ ] `ServerMain` composition shell

Do not assume “demo-specific” means architecturally unimportant. These classes show how to wire reusable components into an application.

---

## 25. Reusable infrastructure to preserve conceptually

- [ ] neutral shared models
- [ ] serializer/protocol discipline
- [ ] typed SimEthereal adapters
- [ ] clock synchronization
- [ ] remote pose history
- [ ] adaptive interpolation delay
- [ ] bounded prediction
- [ ] reconciliation
- [ ] server rewind
- [ ] immutable world snapshots
- [ ] renderer-neutral collision
- [ ] command-queued fixed-step interactions
- [ ] authoritative lifecycle events
- [ ] server-owned state snapshots
- [ ] late-join state replication

---

## 26. Public API extraction

Many current client helper classes are package-private because the repository is a compact demo.

If extracting a library:

- [ ] Define a small explicit public client facade.
- [ ] Define a small explicit public server facade.
- [ ] Expose neutral application callbacks/interfaces rather than JavaFX nodes.
- [ ] Keep internal timing/store classes package-private unless integrators need them.
- [ ] Extract configuration records instead of requiring edits to constants.
- [ ] Avoid making every current implementation class public merely for convenience.
- [ ] Preserve test access through package tests or dedicated test interfaces.

---

## 27. Validation before declaring a port complete

### Connectivity

- [ ] Multiple clients connect concurrently.
- [ ] Identity/participant add/remove works.
- [ ] Late join sees existing participants.

### Motion

- [ ] Local movement remains responsive.
- [ ] Pose send rate matches intended rate.
- [ ] Server state rate matches intended rate.
- [ ] Remote history receives server-timestamped samples.
- [ ] Clock sync converges.
- [ ] Remote interpolation is normally the dominant mode.
- [ ] Short induced loss/jitter triggers bounded extrapolation rather than unbounded drift.
- [ ] Long gaps hold/correct safely.

### Interactions

- [ ] Local provisional effect appears immediately.
- [ ] Authoritative start reconciles without duplicate visual.
- [ ] Server rewind is queried at event time.
- [ ] Server collision result reaches all clients.
- [ ] Client cannot authoritatively declare a hit.

### Persistent state

- [ ] Accepted shot changes shot count once.
- [ ] Valid hit changes hit/score once.
- [ ] No hit means no hit/score increment.
- [ ] Late join receives current totals.
- [ ] UI accurately reflects server snapshot.

### Lifecycle

- [ ] Disconnect cleans server/session/rewind/state.
- [ ] Remote visual disappears.
- [ ] Client/server shut down without lingering owned executor threads.

---

## 28. Source-of-truth rule

The current Java source supersedes older repository prose where they disagree.

In particular, do not port these obsolete descriptions from older docs:

- 30 Hz server state as the current architecture,
- simple one-stage exponential remote smoothing as the current architecture,
- reflection-based SimEthereal integration as the current architecture.

The current implementation is protocol v5 with the multi-layer 120 Hz/timing/interaction/result architecture documented in this package.
