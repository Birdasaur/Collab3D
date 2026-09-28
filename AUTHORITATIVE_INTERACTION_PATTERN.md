# Authoritative Interaction Pattern

## 1. Purpose

This document describes the reusable pattern for synchronized interactions whose **intent originates on a client but whose acceptance, simulation, collision, and result are owned by the server**.

The current tracer round is the worked example. The architecture is intended to support additional interaction types without turning the networking layer into tracer-specific code.

---

## 2. Core flow

```text
CLIENT
local intent
    |
optional immediate provisional presentation
    |
InteractionIntentMessage
    |

SERVER NETWORK BOUNDARY
identify actor from session
    |
enqueue StartInteractionCommand
    |

AUTHORITATIVE INTERACTION THREAD
validate request
    |
rewind authoritative world if needed
    |
construct authoritative interaction state
    |
InteractionStartedEvent
    +-------------------------+
    |                         |
replicate accepted start   server policy observer
    |                         |
clients                  persistent state mutation
    |
fixed-step simulation
    |
authoritative collision
    |
InteractionCollisionEvent
    +-------------------------+
    |                         |
replicate collision       server policy observer
    |                         |
clients                  persistent state mutation
    |
client presentation only
```

The client requests. The server decides.

---

## 3. Architectural invariants

1. A client message represents **intent**, not an authoritative result.
2. Actor identity is derived from the server connection/session.
3. The server assigns the canonical interaction ID.
4. Client-supplied origin/target information is validated or treated as a hint.
5. Authoritative simulation runs off the network callback thread.
6. Historical interactions use bounded server rewind.
7. Collision uses renderer-neutral authoritative geometry.
8. Authoritative events are separated from network replication.
9. Persistent game/application state is mutated by server policy, not by client presentation.
10. A client may render provisionally for responsiveness, but authoritative replication reconciles it.

---

## 4. Shared interaction model

The shared neutral interaction representation is `SharedInteraction`.

Conceptually it contains:

- server `interactionId`,
- authoritative `actorObjectId`,
- actor/client `interactionSequence`,
- event server time,
- accepted server time,
- interaction type,
- authoritative origin,
- authoritative direction,
- optional target ID.

It contains no JavaFX node and no server implementation object.

This model can be consumed by:

- a JavaFX presentation layer,
- logging/recording,
- another renderer,
- test code,
- AI/tooling without knowledge of scene-graph details.

---

## 5. Client intent creation

`CollaborationNetworkClient.sendTracerProjectile(...)` currently:

1. verifies connection/identity/clock readiness,
2. normalizes the submitted direction,
3. increments a client interaction sequence,
4. maps local monotonic time into estimated server time,
5. sends a reliable `InteractionIntentMessage`,
6. returns a provisional `SharedInteraction` with no authoritative server ID yet.

### Why require clock readiness

A synchronized event time is necessary for meaningful rewind. Sending a raw client-local timestamp would give the server no comparable historical point.

### Why return a provisional interaction

Waiting one round trip before showing a muzzle/tracer effect makes the local user's own action feel delayed.

The client can present immediately while still accepting the server as authoritative.

---

## 6. Provisional client presentation

The provisional tracer is keyed by:

```text
(actorObjectId, interactionSequence)
```

When the authoritative `SharedInteractionMessage` arrives, `TracerRoundManager` finds the matching provisional tracer and updates/reconciles it rather than spawning a duplicate.

This gives the local actor:

- immediate feedback,
- eventual authoritative origin/time/ID,
- no second visual shot when the server confirms the action.

### Important rule

Provisional presentation must never mutate server-owned score/hits or declare authoritative collisions.

It is presentation speculation only.

---

## 7. Network-thread boundary

On `InteractionIntentMessage`, the server should do only lightweight envelope/session work before transferring the request to the authoritative interaction system.

Current path:

```text
SpiderMonkey callback
    |
lookup participant session
    |
decode/validate supported type
    |
construct StartInteractionCommand
    |
InteractionManager.enqueue(...)
```

`InteractionManager` uses a concurrent command queue so the authoritative interaction thread remains the single owner of active interaction state.

Avoid running projectile catch-up/collision directly inside the network callback.

---

## 8. `StartInteractionCommand`

The command is the handoff from transport/session code to interaction simulation.

It carries the information the handler needs without carrying a SpiderMonkey connection object through the simulation layer.

This separation makes interaction handlers easier to test and reuse.

---

## 9. InteractionManager responsibilities

`InteractionManager` is the central interaction lifecycle coordinator.

It owns:

- handler registration by `InteractionType`,
- pending start-command queue,
- server interaction ID allocation,
- active interaction collection,
- calls to `handler.start(...)`,
- historical catch-up,
- per-step `handler.update(...)`,
- lifecycle event dispatch,
- removal of completed interactions.

It does not need to know tracer geometry details. Those belong to the handler/collision system.

---

## 10. Fixed-step simulation

`FixedStepInteractionLoop` drives `InteractionManager` at:

```text
SERVER_INTERACTION_HZ = 120
```

Current loop properties:

- fixed simulation step,
- accumulator-based scheduling,
- maximum 8 simulation steps per wake,
- short `parkNanos` waits with final fine-grained timing.

A fixed step is useful because interaction behavior is easier to reason about and test than arbitrary render/network delta times.

The loop is independent from JavaFX and from client frame rate.

---

## 11. Interaction handler extension point

`InteractionHandler` defines the semantic behavior of one interaction type.

Conceptually a handler supplies:

```text
type()
start(command, interactionId, context, now)
catchUp(activeInteraction, from, to, context)
update(activeInteraction, stepStart, stepEnd, context)
```

Exact source signatures should remain the implementation authority, but the ownership pattern is stable:

- manager owns lifecycle,
- handler owns type-specific semantics,
- context supplies authoritative services.

---

## 12. InteractionContext

`InteractionContext` bundles authoritative services needed by handlers rather than making handlers reach into `ServerMain`.

This is a useful dependency boundary for future interaction types.

It provides access to server-side capabilities such as authoritative world sampling/collision required by the handler.

Prefer adding a well-defined service to the context over passing the server composition root into handlers.

---

## 13. Tracer/projectile start validation

`ProjectileInteractionHandler` demonstrates a robust start path.

### 13.1 Future-time bound

An event may not be arbitrarily far ahead of server now.

Current tolerance:

```text
20 ms
```

This accommodates synchronization error without letting a client schedule arbitrary future interactions.

### 13.2 Historical bound

The server will not catch up arbitrarily old shots.

Current maximum catch-up:

```text
500 ms
```

This is deliberately shorter than the full one-second rewind store.

### 13.3 Direction validation

Direction must be finite, nonzero, and normalized for authoritative simulation.

### 13.4 Actor historical pose

The server samples the actor at `eventServerTimeNanos`.

This historical pose defines the authoritative shot origin.

### 13.5 Submitted-origin tolerance

The client may send an origin hint, but it must be close to the authoritative rewound origin.

Current tolerance:

```text
2.0 world units
```

The server then constructs the authoritative muzzle origin itself.

### 13.6 Actor identity

The actor comes from the session associated with the network connection, not from a client-selected actor field.

---

## 14. Authoritative tracer configuration

Current projectile defaults:

| Parameter | Value |
|---|---:|
| Speed | 60 units/s |
| Lifetime | 500 ms |
| Muzzle offset | 0.60 units |
| Collision radius | 0.06 units |
| Simulation rate | 120 Hz |
| Future-time tolerance | 20 ms |
| Max historical catch-up | 500 ms |
| Origin validation tolerance | 2.0 units |

These are configuration/policy choices, not hard requirements of the architecture.

---

## 15. Historical catch-up

Network delivery and processing introduce delay. If the shot event time is already in the past when the server accepts it, starting the projectile at “server now” would incorrectly ignore where it should already have traveled.

The handler therefore simulates from:

```text
eventServerTime -> current authoritative simulation time
```

in fixed increments.

For each historical interval, it requests an authoritative historical world snapshot and performs the same collision logic used during live simulation.

This is a generalized pattern:

> Validate an event at its synchronized event time, then replay/catch up authoritative simulation through historical state until it reaches the live timeline.

---

## 16. Live projectile simulation

Once caught up, the projectile becomes a normal active interaction.

For each fixed step:

1. compute segment start/end from velocity and elapsed time,
2. create a `SweptSphere3d`,
3. obtain current authoritative world snapshot,
4. exclude the actor through a collision filter,
5. sweep against collision shapes,
6. choose earliest hit,
7. emit collision/completion events if hit,
8. otherwise advance state or expire at lifetime.

The projectile is not simulated by moving a JavaFX sphere and asking JavaFX for collision.

---

## 17. Renderer-neutral collision

Current collision model includes:

- `Vector3d`,
- `SweptSphere3d`,
- `OrientedBox3d`,
- `CompoundShape3d`,
- `CollisionResult`,
- `CollisionService`.

This allows authoritative server collision to run headlessly.

`ParticipantCollisionShapeProvider` currently approximates the demo participant's visible geometry.

A production game can supply its own hitboxes without changing the network protocol or JavaFX renderer.

---

## 18. Historical world sampling

`WorldStateSampler` bridges pose history and collision geometry.

### Live

The server publication thread publishes an immutable latest world snapshot.

### Historical

A handler can request state at historical server time `T`, and the sampler reconstructs entity states from `ServerRewindService` plus the collision-shape provider.

This prevents the interaction handler from manually combining pose history and geometry rules.

---

## 19. Interaction events

The server interaction framework uses internal lifecycle events.

Current important event types:

- `InteractionStartedEvent`,
- `InteractionCollisionEvent`,
- `InteractionCompletedEvent`.

These internal events are more expressive than the network protocol and allow multiple consumers.

For example:

```text
InteractionCollisionEvent
    +--------------------+
    |                    |
network replication   scoring policy
```

That separation is intentional.

---

## 20. Replication policy

`InteractionReplicationService` maps only selected authoritative events to network messages.

Current mapping:

- start -> reliable `SharedInteractionMessage`,
- collision -> reliable `InteractionCollisionMessage`,
- completion -> not explicitly replicated.

Not every internal event needs a wire message.

Add one only when clients need that information for correctness or presentation.

---

## 21. Persistent result policy is not interaction simulation

The server observes interaction events and updates `ServerParticipantStateStore`.

Current rule:

```text
accepted TRACER_PROJECTILE start
    -> shotsFired + 1

authoritative collision with a valid non-self participant target
    -> hitCount + 1
    -> score + 1
```

This is application policy layered on interaction events.

The projectile handler does not directly own scoreboard state.

This is the pattern to preserve for future health, objectives, achievements, ammo, etc.

---

## 22. Deterministic client presentation from server time

`TracerRoundManager` does not require per-frame projectile position packets.

Given an accepted `SharedInteraction`:

```text
position(t) = origin + direction * speed * (serverNow - eventServerTime)
```

within its visual lifetime.

This gives smooth client presentation while the server still owns collision.

The presentation clock comes from the synchronized server-time mapping.

### Collision correction

When `InteractionCollisionMessage` arrives, the client uses the authoritative impact point/time and stops/corrects the tracer presentation there.

---

## 23. Adding a new interaction type

Use this sequence.

### Step 1: define semantics

Decide:

- what client intent is necessary,
- what the server must validate,
- whether historical rewind matters,
- whether the interaction is instantaneous, continuous, or finite-lived,
- what authoritative events clients need.

### Step 2: extend `InteractionType`

Because the current protocol uses enum ordinals, append rather than reorder existing values.

### Step 3: decide whether the existing intent message is sufficient

The current generic intent carries:

- sequence,
- event time,
- type,
- origin,
- direction,
- optional target.

If the new interaction needs materially different data, add an explicit protocol shape rather than abusing unrelated fields.

### Step 4: implement `InteractionHandler`

Keep type-specific validation/simulation here.

### Step 5: register the handler

Register it with `InteractionManager` during server composition.

### Step 6: use authoritative state services

Use `InteractionContext`, `WorldStateSampler`, rewind, and collision rather than reading client render state.

### Step 7: emit lifecycle events

Internal events let replication and application policy remain separate.

### Step 8: choose replication

Clients may need:

- accepted start,
- state changes,
- collision,
- completion,
- or none for a purely server-side effect.

Do not replicate every internal simulation tick by default.

### Step 9: add client presentation separately

The client renderer should consume authoritative neutral interaction data. It should not become the authority.

### Step 10: add persistent state policy separately

If the interaction changes health/score/objectives, update server-owned state from authoritative events.

---

## 24. Example: selection interaction

The enum already contains selection-oriented placeholder types, but handlers are not implemented in the current server path.

A future authoritative selection could follow:

```text
client points at candidate target
    |
Selection intent(target, event time)
    |
server verifies actor + target eligibility
    |
optional historical visibility/ray validation
    |
InteractionStarted/Completed
    |
server-owned selection state mutation
    |
reliable state snapshot to clients
```

The important point is that adding `SELECTION_SET` to the enum does not make it authoritative; a handler/policy must define what it means.

---

## 25. Example: damage-producing projectile

Do **not** put client-reported damage in the collision message path.

Prefer:

```text
authoritative collision
    |
server combat/damage policy
    |
server-owned health state mutation
    |
reliable health snapshot/event
```

The projectile's job is to establish the collision fact. Damage policy is a separate authoritative subsystem.

---

## 26. Common incorrect simplifications

### “Just send a hit message from the client”

Wrong authority boundary. The client can report intent, not authoritative hit outcome.

### “Use the current target pose when the request arrives”

That ignores network delay and defeats rewind.

### “Move the server projectile using JavaFX nodes”

That makes headless authoritative simulation depend on rendering.

### “Broadcast projectile position every server tick”

Unnecessary for deterministic constant-velocity tracer presentation. Replicate the accepted event and authoritative corrections/results instead.

### “Do scoring in the tracer renderer”

Presentation must not own persistent state.

### “Run the handler inside the network listener”

This destroys the clean single-owner interaction simulation model and can block networking.

---

## 27. Testing strategy for new handlers

Test at three layers.

### Pure handler/collision tests

- valid start,
- invalid/future/too-old event,
- origin tolerance,
- hit/miss geometry,
- earliest hit,
- expiration,
- historical catch-up.

### Manager/lifecycle tests

- command queue -> start,
- ID allocation,
- active lifecycle,
- event ordering,
- completion removal.

### End-to-end transport tests

- intent reaches server,
- accepted event reaches all clients,
- provisional actor tracer reconciles rather than duplicates,
- collision reaches all clients,
- server-owned result state changes exactly once.

---

## 28. Pattern summary

```text
RESPONSIVE CLIENT                         AUTHORITATIVE SERVER

input
  |
provisional visual
  |
intent ----------------------------------------> session-derived actor
                                                    |
                                              bounded validation
                                                    |
                                                rewind state
                                                    |
                                              authoritative start
                                                    |
                     <----------------------- replicate accepted state
  |
reconcile provisional
                                                    |
                                              fixed-step simulation
                                                    |
                                              server collision
                                                    |
                     <----------------------- replicate collision
  |
presentation only                                  |
                                             server-owned policy/state
```

This pattern allows low-latency client feedback without transferring authority away from the server.
