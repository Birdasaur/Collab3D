# Rendering Integration Patterns

## 1. Purpose

This document describes how the networking/timing architecture is integrated with JavaFX 3D without making the scene graph part of the multiplayer state model.

The central rule is:

> Networking and authoritative/synchronized motion produce renderer-neutral state; JavaFX consumes that state for presentation.

This boundary is what makes the prototype portable to a larger JavaFX application—or to a different renderer later.

---

## 2. Neutral model vs scene graph

The shared motion model is `Pose3d`:

```text
position + quaternion + sample time
```

It has no dependency on:

- `Node`,
- `Transform`,
- `Affine`,
- `Camera`,
- JavaFX properties.

Similarly, authoritative collision shapes live in `common.geometry`, not in JavaFX `Shape3D` objects.

### Why this matters

If JavaFX nodes become the network state model:

- background network threads would need unsafe scene-graph access,
- server simulation would acquire rendering dependencies,
- testing becomes harder,
- porting to another renderer becomes harder,
- scene transforms can accidentally become game authority.

---

## 3. `FxPoseCodec` is the rendering boundary

`FxPoseCodec` converts between neutral pose/quaternion values and JavaFX transforms.

Treat this as an adapter, not a simulation component.

Preferred direction:

```text
Pose3d -> FxPoseCodec -> JavaFX transform/node
```

When local camera data must enter the neutral model, conversion can occur in the opposite direction, but JavaFX-specific types should not leak through the networking subsystems.

---

## 4. Client world composition

`ClientWorldView` composes the demo into layers.

Conceptually:

```text
StackPane
  |
  +-- SubScene (3D)
  |     |
  |     +-- world/grid
  |     +-- remote participant layer
  |     +-- interaction/tracer layer
  |     +-- camera rig / lighting
  |
  +-- 2D participant label overlay
  |
  +-- 2D scoreboard overlay
```

The overlay layers are mouse-transparent where appropriate so they do not interfere with 3D input.

This layering is reusable even if the target application has a much larger scene.

---

## 5. Local camera rendering

The local participant's motion is simulated outside the JavaFX render pulse.

The render path reads the latest neutral local pose and applies it to the JavaFX camera rig.

Do not make the camera's current transform the canonical simulation state simply because it is visible.

Correct:

```text
input -> CameraMotionModel -> Pose3d -> camera transform
```

Not preferred:

```text
input -> mutate camera node -> scrape node transform -> network
```

The first direction keeps simulation explicit and testable.

---

## 6. Remote participant rendering pipeline

`RemoteParticipantManager` owns the presentation objects for remote participants.

Per frame, conceptually:

```text
estimated server time
      |
RemotePoseStore / RemoteParticipantMotionState
      |
resolved render Pose3d
      |
RemoteParticipantView.applyPose(...)
      |
RemoteParticipantLabelOverlay.update(...)
```

`RemoteParticipantView` does **not** perform interpolation, prediction, or clock mapping.

Those have already occurred by the time the view receives a pose.

---

## 7. `RemoteParticipantView`

The current demo visual is composed from JavaFX 3D primitives representing a body and direction indicator.

It is intentionally a presentation class.

Responsibilities:

- own its JavaFX nodes,
- apply a supplied pose,
- expose the node/group needed for scene composition.

Non-responsibilities:

- network packet handling,
- time synchronization,
- pose history,
- extrapolation,
- collision authority,
- score state.

A real application should replace the visual model freely while retaining the neutral pose input contract.

---

## 8. Why labels are 2D overlays

`RemoteParticipantLabelOverlay` renders names using normal 2D JavaFX `Label` nodes rather than embedding 2D text directly in the transformed 3D participant group.

The overlay projects a 3D participant anchor into screen coordinates:

```text
participant local point
      |
node.localToScreen(...)
      |
overlayPane.screenToLocal(...)
      |
2D label position
```

The label is hidden when the projection is not usable/outside the view.

### Benefits

A 2D overlay label:

- remains screen-facing,
- avoids inheriting participant rotation,
- avoids perspective shrinking/stretching as world geometry,
- remains legible at useful UI size,
- avoids 3D depth interactions/occlusion quirks unless intentionally implemented,
- can be positioned with a constant screen-space offset.

The current offset is a presentation detail; the pattern is the important part.

---

## 9. Why not simply put `Text` inside the 3D participant group

If a `Text` node is parented under the same transformed 3D group as the participant, it inherits world transforms.

That can produce:

- labels rotating away from the camera,
- labels changing apparent size with perspective,
- awkward depth ordering,
- difficult billboard logic,
- transform coupling between avatar orientation and UI.

For nameplates and status labels, a projected 2D overlay is usually simpler and more controllable.

If true world-space text is desired, implement it deliberately as a billboard/world-space UI system rather than accidentally inheriting participant transforms.

---

## 10. Scoreboard overlay

`ClientScoreboardOverlay` is another example of presentation being downstream from neutral replicated state.

Data path:

```text
ParticipantResultStateMessage
      |
ParticipantResultStore
      |
scoreboard snapshot/sort
      |
JavaFX overlay
```

The scoreboard does not listen to tracer geometry and infer hits.

It does not mutate score.

It renders current server-replicated state.

---

## 11. Interaction presentation

`ClientInteractionController` and `TracerRoundManager` translate neutral authoritative interaction data into JavaFX visuals.

### Local response

The actor may create a provisional tracer immediately.

### Authoritative start

When `SharedInteraction` arrives, the presentation is reconciled using the actor/sequence key.

### Authoritative collision

When `InteractionCollision` arrives, the tracer is moved/stopped at the authoritative impact and a short impact effect is shown.

The visual behavior is not used as collision input to the server.

---

## 12. Deterministic tracer animation instead of network transform spam

The tracer presentation can compute its position from:

- authoritative origin,
- direction,
- speed,
- event server time,
- current synchronized server time.

Therefore the server does not need to send a JavaFX node position every frame.

Pattern:

```text
replicate semantic event + authoritative timing
        |
client derives smooth presentation every render frame
```

This is especially effective for simple deterministic effects.

---

## 13. JavaFX thread rule

JavaFX scene-graph mutation belongs on the JavaFX Application Thread.

Network callbacks may arrive on networking/service threads.

The current composition uses `Platform.runLater(...)` where network-driven callbacks need to create/remove/update UI structures.

Do not directly mutate JavaFX nodes from SpiderMonkey, SimEthereal, motion-scheduler, or authoritative simulation threads.

### Data handoff

Use thread-safe/immutable data boundaries such as:

- `ConcurrentHashMap`,
- immutable records,
- `AtomicReference`,
- queued callbacks to FX thread.

Do not use JavaFX properties as the cross-thread synchronization mechanism for core networking state.

---

## 14. Render-loop hook

The JavaFX client uses a render/update callback (`AnimationTimer`) for presentation work such as:

- reading latest local pose and applying camera transform,
- resolving each remote participant at current synchronized render time,
- applying remote transforms,
- projecting/updating labels,
- updating tracer visuals,
- refreshing overlay presentation.

The render loop **consumes** simulation/network state. It should not become the producer of network protocol state merely because it runs frequently.

---

## 15. Remote smoothing belongs before the view

A tempting design is:

```text
RemoteParticipantView receives packets and smooths its own transforms
```

The prototype instead uses:

```text
RemotePoseHistory
   + RemoteInterpolationController
   + RemotePosePredictor
   + PoseReconciler
        |
        v
final neutral Pose3d
        |
RemoteParticipantView
```

Advantages:

- smoothing can be tested without JavaFX,
- multiple renderers can share it,
- debug tools can inspect resolved poses,
- view replacement does not alter network timing.

---

## 16. Participant metadata vs motion state

Participant presentation needs more than pose:

```text
ParticipantInfo
    objectId
    displayName
    hue

Remote pose state
    position
    orientation
    time
```

These arrive through separate paths and may not arrive in exactly the same callback order.

Presentation managers should tolerate a participant metadata entry existing before its first pose, or a pose service beginning near the same time as metadata registration.

Do not assume one network message contains the entire participant view model.

---

## 17. Replacing JavaFX with another renderer

The networking architecture can remain largely intact if the following classes are replaced:

```text
FxPoseCodec
ClientWorldView
RemoteParticipantView
RemoteParticipantLabelOverlay
ClientScoreboardOverlay
TracerRoundManager visual-node implementation
CameraController / JavaFX input integration
```

The following concepts can remain renderer-independent:

```text
Pose3d
Quaterniond
ClockSynchronizer
RemotePoseHistory
RemoteInterpolationController
RemotePosePredictor
PoseReconciler
RemoteParticipantMotionState
RemotePoseStore
SharedInteraction
InteractionCollision
ParticipantResultState
```

This separation is intentional evidence that JavaFX is a presentation choice, not a network protocol requirement.

---

## 18. Integrating into a larger existing JavaFX scene

A target application does not need to adopt `ClientWorldView` wholesale.

Recommended integration:

1. Keep the application's existing scene/subscene/camera ownership.
2. Add a dedicated `Group` or logical layer for remote participant visuals.
3. Add a dedicated interaction/effect layer if needed.
4. Add a 2D overlay pane above the 3D `SubScene` for nameplates/HUD.
5. Feed local simulation/network code neutral poses.
6. On each application render pulse, resolve remote poses and apply them to existing visuals.
7. Keep network callbacks out of direct scene-graph mutation.

---

## 19. Camera ownership choices

The demo owns a camera motion model because it needs a self-contained runnable client.

A larger application may already own camera/avatar motion.

In that case:

- keep the application's local movement model,
- expose its authoritative/current local `Pose3d` through an adapter,
- publish from that neutral state,
- do not duplicate motion just to satisfy the demo class structure.

`CameraMotionModel` is a reference implementation, not a mandatory application API.

---

## 20. Collision visuals vs collision authority

The JavaFX participant mesh/group and server collision shape are related but deliberately separate.

The server currently uses `ParticipantCollisionShapeProvider` to create an approximate neutral compound hit shape corresponding to the visible avatar.

Do not query `RemoteParticipantView` bounds on the server.

For a production application:

```text
visual model                authoritative hit model
-----------                 -----------------------
JavaFX mesh/group      <->   neutral collision shape provider
```

Keep their relationship explicit and testable.

---

## 21. Overlay coordinate considerations

When projecting 3D anchors to a 2D overlay:

- use screen coordinates as the common bridge,
- convert back into the overlay parent's local coordinates,
- handle null/unprojectable/off-screen points,
- account for subscene placement within the window,
- keep labels mouse transparent unless they are intended controls.

Do not assume `localToScene` coordinates from a 3D subscene directly match a sibling overlay's local coordinates.

---

## 22. Presentation lifecycle

For each remote participant, presentation should handle:

```text
ParticipantInfo added
    -> create visual + label shell

first remote pose
    -> begin positioning

ongoing frames
    -> resolve/apply pose + update projected label

ParticipantRemoved
    -> remove 3D nodes + 2D labels + related presentation state
```

State stores may have their own cleanup policy, but JavaFX nodes should not leak after participant removal.

---

## 23. Debug overlays

The current UI includes status/diagnostic presentation useful for validating:

- connection state,
- clock synchronization,
- remote interpolation mode,
- jitter/delay behavior,
- network rates.

When porting to a production UI, consider retaining a developer/debug overlay even if the demo status pane is removed. High-rate networking issues are substantially easier to diagnose with live timing/rate data.

---

## 24. Rendering integration anti-patterns

Avoid:

- using a JavaFX node transform as authoritative server state,
- updating JavaFX nodes from network threads,
- doing remote packet interpolation inside each view class,
- embedding scoreboard mutations in UI controls,
- treating the visible tracer as authoritative collision geometry,
- parenting readable name labels under rotating 3D participant transforms by default,
- tying client simulation frequency to monitor refresh rate,
- sending a new network projectile transform every JavaFX frame when the effect is deterministic from event time.

---

## 25. Rendering integration checklist

- [ ] Core network/simulation state uses neutral classes.
- [ ] JavaFX conversion is isolated in adapter/presentation code.
- [ ] FX scene-graph changes happen on the FX thread.
- [ ] Local motion is not defined by JavaFX pulse timing unless explicitly intended.
- [ ] Remote interpolation/prediction occurs before view application.
- [ ] Remote views do not own network history.
- [ ] 2D labels are projected from 3D anchors into an overlay layer.
- [ ] HUD/scoreboard reads client replica state only.
- [ ] Interaction visuals reconcile with authoritative events.
- [ ] Collision authority remains server-side and renderer-neutral.
- [ ] Application-specific visuals can be replaced without changing protocol/timing code.

---

## 26. Pattern summary

```text
NETWORK / SIMULATION                  JAVAFX PRESENTATION

neutral local Pose3d ----------------> camera transform

remote history
  -> interpolation
  -> bounded prediction
  -> reconciliation
  -> neutral Pose3d -----------------> RemoteParticipantView
                                            |
                                            +-> 3D anchor projection
                                                   |
                                                   v
                                             2D name label

ParticipantResultStore --------------> scoreboard overlay

SharedInteraction / Collision -------> tracer/effect presentation
```

The renderer is the final consumer, not the source of multiplayer truth.
