# Client Integration Guide

## 1. Goal

This guide explains how to integrate the prototype's multiplayer client architecture into an existing Java/JavaFX 3D application without copying the demo UI wholesale.

The reusable target is the **network/timing/state pattern**, not `ClientMain` itself.

## 2. What to keep vs replace

### Usually keep or adapt directly

- `Pose3d`
- `Quaterniond`
- `ParticipantInfo`
- `ParticipantResultState`
- common interaction records
- all application message classes
- `NetworkConstants`
- `NetworkSerializers`
- `ClockSynchronizer`
- `RemotePoseStore`
- `RemoteParticipantMotionState`
- `RemotePoseHistory`
- `RemoteInterpolationController`
- `RemotePosePredictor`
- `PoseReconciler`
- `ParticipantResultStore`
- `SharedInteractionStore` if useful to the application
- `SimEtherealClientAdapter`
- `CollaborationNetworkClient` as a reference facade

### Replace or strongly adapt

- `CameraController`
- `CameraInputState`
- `CameraMotionModel`
- `ClientWorldView`
- `RemoteParticipantView`
- `RemoteParticipantLabelOverlay`
- `TracerRoundManager`
- `ClientScoreboardOverlay`
- `ClientStatusPane`
- `ClientMain`

These classes encode the demo's first-person camera and JavaFX presentation.

## 3. Visibility warning

Many reusable client classes are currently package-private because the prototype is a single application, not a published library. If integrating from another package/module, choose one of these approaches deliberately:

1. move the integration code into the same package;
2. promote selected classes/methods to a supported public API;
3. wrap package-private implementation behind a new public facade.

For a long-term library, option 2 or 3 is preferable. Do not expose every demo class merely to avoid package boundaries.

## 4. Required client objects

A non-demo client needs equivalents of:

```text
NetworkRuntimeStats              optional diagnostics
RemotePoseStore                  incoming authoritative remote motion
SharedInteractionStore          optional latest interaction state
ParticipantResultStore          replicated server-owned result state
CollaborationNetworkClient      SpiderMonkey + time sync + SimEthereal facade
LocalPosePublisher or equivalent local simulation/publication loop
renderer-specific participant presentation
renderer-specific interaction presentation
```

If the host application already has its own motion simulation, `LocalPosePublisher` can be replaced by an adapter that samples the host application's authoritative local pose at `CLIENT_POSE_HZ` and calls `sendPose()`.

## 5. Initialization order

Use this order conceptually:

```text
1. Create neutral/thread-safe state stores.
2. Create local simulation/state provider.
3. Create renderer/view objects on the JavaFX thread.
4. Create CollaborationNetworkClient.
5. Create interaction input/presentation controller.
6. Start local motion/pose publication loop.
7. Start optional diagnostics.
8. Connect SpiderMonkey on a background thread.
9. Start/render the normal JavaFX frame loop.
```

`CollaborationNetworkClient.connect()` internally:

1. calls `NetworkSerializers.registerAll()`;
2. creates the SpiderMonkey client using application name/protocol/host/ports;
3. installs application message listeners;
4. installs the typed `SimEtherealClientAdapter` before client startup;
5. starts the client.

## 6. Connection configuration

`ClientConnectionConfig` supports JavaFX named parameters:

```text
--host=<server-host>
--tcpPort=6143
--udpPort=6144
```

Defaults come from `NetworkConstants` and use `localhost`, TCP 6143, UDP 6144.

The demo also accepts:

```text
--name=<display-name>
--timeSyncDiagnostics=true|false
```

Do not hard-code a deployment-specific address into reusable code.

## 7. Supplying local pose state

### 7.1 Demo pattern

The demo uses:

```text
JavaFX input events
 -> CameraInputState
 -> LocalPosePublisher @ 120 Hz
 -> CameraMotionModel.update()
 -> Pose3d
 -> CollaborationNetworkClient.sendPose()
```

`CameraMotionModel` is plain Java and does not touch the JavaFX scene graph.

### 7.2 Existing-application pattern

If the application already owns movement, provide a renderer-neutral snapshot:

```java
Pose3d pose = new Pose3d(
        x,
        y,
        z,
        orientationQuaternion,
        System.nanoTime());
```

Then send it at the configured publication rate.

Important:

- timestamp when the sample is created, not when a later send callback happens;
- keep sequence generation in one publisher/facade;
- do not read mutable JavaFX nodes from a 120 Hz background thread;
- if the application's canonical transform lives in JavaFX, capture it on the FX thread into a neutral immutable snapshot, then publish that snapshot from the networking loop.

`sendPose()` marks `PoseInputMessage` unreliable so newer pose samples can supersede lost/late samples.

## 8. Rendering the local participant/camera

The demo renders local motion from `CameraMotionModel.latestPose()` during `AnimationTimer`:

```text
120 Hz motion thread produces immutable snapshots
        |
        v
AtomicReference<Pose3d>
        |
        v
JavaFX AnimationTimer reads newest completed pose
        |
        v
FxPoseCodec.toAffine()
        |
        v
cameraRig transform
```

This allows local motion/network simulation to run at 120 Hz even if JavaFX is rendering at a different cadence.

In an existing engine, replace only the final renderer application step.

## 9. Consuming remote participants

### 9.1 Incoming state

`SimEtherealClientAdapter` receives shared objects. Each `objectUpdated()` is converted to `Pose3d` and stamped with the current SimEthereal frame time received by `beginFrame(long)`.

The callback feeds:

```text
RemotePoseStore.offer(objectId, pose)
```

`RemotePoseStore` is safe to call from the networking/SimEthereal callback thread.

### 9.2 Per-frame render resolution

On the render thread:

```text
serverNow = networkClient.toServerTime(localRenderNow)
pose = poseStore.getRenderPose(objectId, serverNow, bufferingEnabled)
renderer.applyPose(pose)
```

Only enable server-time buffered interpolation after `networkClient.hasClockEstimate()` is true. `ClientMain` explicitly gates the feature this way.

### 9.3 Do not put smoothing in the view

`RemoteParticipantView.applyPose()` must receive a fully resolved pose. Prediction/interpolation belongs in `RemoteParticipantMotionState`, not in renderer code.

This keeps the same motion behavior usable with JavaFX, jME, LWJGL, another UI toolkit, or a headless observer.

## 10. Remote motion behavior

With buffered motion enabled:

1. target render time = `serverNow - adaptiveInterpolationDelay`;
2. interpolate if authoritative samples bracket target time;
3. if target is slightly newer than the newest sample, extrapolate up to 25 ms;
4. after 25 ms, hold the latest authoritative sample;
5. reconcile the target into the currently rendered pose;
6. snap immediately if error exceeds 5 world units or 45 degrees.

With buffering disabled, the newest authoritative sample is rendered directly and the reconciler is reset.

## 11. Participant lifecycle

The client receives two different kinds of participant data:

- application metadata via `ParticipantInfoMessage`;
- transform state via SimEthereal.

Do not assume they arrive in one deterministic UI-friendly order.

The current client handles a metadata-before-identity edge case by removing any accidentally created remote view when it learns its own local object ID.

On `ParticipantRemovedMessage`:

- remove remote pose state;
- remove interaction state;
- remove result state;
- remove renderer/UI objects;
- remove actor-associated tracer visuals.

Treat removal operations as idempotent where practical.

## 12. Clock synchronization integration

`CollaborationNetworkClient` starts one reliable NTP-style sync exchange per second after transport connection.

Useful methods:

```text
hasClockEstimate()
toServerTime(clientTimeNanos)
toClientTime(serverTimeNanos)
```

Use synchronized server time for:

- remote rendering against SimEthereal server timestamps;
- interaction event timestamps;
- deterministic client presentation of synchronized interactions.

Do not apply the clock offset to `PoseInputMessage.clientSampleTimeNanos`; that field intentionally preserves the original client clock domain.

## 13. Sending an authoritative interaction intent

The demo tracer flow:

```text
Space key
 -> read latest local Pose3d
 -> compute camera +Z forward vector
 -> sendTracerProjectile(origin, direction)
```

`sendTracerProjectile()` requires:

- connected SpiderMonkey client;
- assigned local object ID;
- established clock estimate;
- finite nonzero direction.

It maps current local `nanoTime` to server time, sends a reliable `InteractionIntentMessage`, then returns a provisional `SharedInteraction` for immediate local presentation.

The provisional interaction has `interactionId = -1` because the server has not assigned the authoritative interaction ID yet.

## 14. Reconciling local interaction presentation

The tracer renderer keys visuals by:

```text
(actorObjectId, interactionSequence)
```

That key exists both in the provisional local event and the later authoritative server broadcast.

Therefore:

- local shot appears immediately;
- authoritative start does not create a duplicate;
- it updates/reconciles the existing tracer definition;
- authoritative collision later resolves the impact.

Preserve this correlation pattern when adding latency-hidden client presentation.

## 15. Consuming authoritative collisions

`InteractionCollisionMessage` becomes an `InteractionCollision` domain record and is delivered to the client listener.

The presentation layer may:

- move the visual to the authoritative impact point;
- stop the projectile trail;
- show an impact effect;
- play audio or UI feedback.

It must **not** infer authoritative score/damage changes from local collision simulation. Those arrive from server-owned state/event replication.

## 16. Consuming participant result state

`ParticipantResultStateMessage` contains the complete authoritative state for one participant:

```text
objectId
shotsFired
hitCount
score
```

The client converts it to `ParticipantResultState` and stores it in `ParticipantResultStore`.

`accuracyPercent()` is derived from hits/shots.

A different UI can consume the store without using `ClientScoreboardOverlay`.

## 17. JavaFX thread boundary

SpiderMonkey and SimEthereal callbacks are not JavaFX UI callbacks.

The current pattern is:

```text
network callback
    -> update thread-safe neutral store directly
    -> for actual UI mutations: Platform.runLater(...)
```

Examples marshalled through `Platform.runLater()` include:

- status text;
- identity UI;
- participant view creation/removal;
- scoreboard refresh;
- tracer start/collision presentation.

Never mutate JavaFX nodes from network or scheduled-executor threads.

## 18. Existing render-loop hook

The demo's `updateFrame(now)` is the minimal reference:

```text
record render stat
read latest local pose
apply local renderer pose
check clock synchronization
map local render time -> server time
resolve/render remote participants
advance interaction visuals using server time
refresh slow diagnostics if due
```

An existing application should place equivalent hooks in its own render/update loop.

## 19. Shutdown order

The current client stops in this order:

1. stop JavaFX animation timer;
2. stop `LocalPosePublisher` and clear held movement input;
3. stop stats logger;
4. close network client, which stops time-sync scheduling and closes SpiderMonkey.

For another application, stop producers before tearing down the transport they publish through.

## 20. Recommended public facade for production integration

The prototype's package-private classes are useful internally but are not yet a polished external API. A production library would benefit from a public facade roughly equivalent to:

```text
MultiplayerClient
    connect()
    close()
    submitLocalPose(Pose3d)
    fireInteraction(...)
    localObjectId()
    hasClockEstimate()
    toServerTime(...)
    participant events
    interaction events
    result-state events

RemoteMotionResolver
    offer(objectId, authoritativePose)
    resolve(objectId, serverNow)
    remove(objectId)
```

The facade should preserve the current internal responsibilities rather than merging them.

## 21. Integration validation checklist

Before considering a client port complete, verify:

- local motion/publication rate is independent of renderer FPS;
- pose messages are unreliable;
- clock sync establishes an estimate;
- remote poses carry server/SimEthereal frame timestamps;
- normal remote rendering reports mostly interpolation;
- short packet gaps produce bounded extrapolation;
- longer gaps hold rather than diverge;
- participant removal clears render/state objects;
- locally fired tracer appears immediately;
- server start reconciles rather than duplicates it;
- authoritative collision controls impact presentation;
- result state updates only from server messages;
- late joiner receives existing participant result states;
- all JavaFX mutations occur on the FX thread.
