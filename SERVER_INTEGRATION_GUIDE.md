# Server Integration Guide

## 1. Goal

This guide explains how to embed the prototype's authoritative server components into another application or game server.

The important integration target is not `ServerMain` as a monolithic executable. The reusable design is the combination of:

- SpiderMonkey session/message handling,
- SimEthereal shared-object publication,
- a synchronized monotonic server timeline,
- bounded pose history for rewind,
- immutable world snapshots for authoritative simulation,
- a fixed-step interaction loop,
- renderer-neutral collision geometry,
- server-owned persistent result state,
- reliable event/state replication where delivery matters.

`ServerMain` should be treated as the current composition root and reference wiring.

---

## 2. Current server model

The current prototype runs a single authoritative server process for a small shared 3D session.

Current defaults:

| Setting | Value |
|---|---:|
| Maximum participants | 12 |
| TCP port | 6143 |
| UDP port | 6144 |
| Server state publication | 120 Hz |
| Authoritative interaction simulation | 120 Hz |
| Rewind history | 1 second |
| World coordinate clamp | +/-100 |
| Protocol version | 5 |

The server owns:

- participant identity assignment,
- authoritative server time,
- participant lifecycle,
- SimEthereal object hosting,
- the authoritative replicated pose timeline,
- rewind history,
- interaction acceptance and simulation,
- collision determination,
- persistent participant result state,
- late-join result snapshots.

### Movement authority caveat

The current movement path is intentionally simpler than the interaction path. The server accepts each client's newest pose after finite-value cleanup, quaternion normalization, sequence rejection, and world-bound clamping. It does **not** independently simulate player locomotion or enforce movement-rate/acceleration rules.

Therefore, describe current movement as:

> server-published / server-timestamped participant pose state derived from client pose input

Do not describe it as a fully server-simulated anti-cheat movement model.

An application that requires stronger movement authority should replace pose ingestion with its own server-side movement simulation while preserving the downstream publication, rewind, and snapshot interfaces.

---

## 3. Primary server components

### Composition and transport

- `ServerMain`
  - executable entry point,
  - owns current nested server composition,
  - configures SpiderMonkey, listeners, SimEthereal, interactions, state publication, stats, and shutdown.
- `NetworkSerializers`
  - registers all application message classes in a fixed shared order.
- `NetworkConstants`
  - central rates, ports, timing limits, and protocol constants.

### Shared-object replication

- `SimEtherealServerAdapter`
  - typed boundary around SimEthereal/SimMath,
  - installs `EtherealHost`,
  - owns `ZoneManager`,
  - hosts/stops participant objects,
  - brackets each server publication frame with `beginFrame()` / `endFrame()`,
  - publishes object transform updates.

### Rewind and authoritative world sampling

- `ServerRewindService`
  - participant pose histories on the server timeline,
  - bounded by `SERVER_REWIND_HISTORY_NANOS`,
  - supports exact/interpolated historical queries only.
- `ServerPoseHistory`
  - per-participant ordered history implementation.
- `WorldStateSampler`
  - publishes immutable live snapshots,
  - reconstructs historical snapshots from rewind data,
  - adds collision shapes through a `CollisionShapeProvider`.

### Interaction framework

- `InteractionManager`
  - accepts thread-safe start commands,
  - owns active interactions on the interaction thread,
  - dispatches to registered handlers,
  - emits lifecycle events.
- `FixedStepInteractionLoop`
  - dedicated 120 Hz authoritative simulation loop.
- `InteractionHandler`
  - extension point for interaction semantics.
- `ProjectileInteractionHandler`
  - tracer/projectile worked example.
- `CollisionService`
  - renderer-neutral swept collision tests.
- `InteractionReplicationService`
  - maps authoritative start/collision events to network messages.

### Persistent server-owned state

- `ServerParticipantStateStore`
  - current shots/hits/score state,
  - server mutation only,
  - late-join snapshots.
- `ParticipantResultState`
  - immutable shared representation.

---

## 4. Recommended initialization order

Preserve the dependency order even if the surrounding application's server bootstrap differs.

```text
1. Register shared serializers
        |
2. Create SpiderMonkey server
        |
3. Install SimEthereal server service
        |
4. Register connection/message listeners
        |
5. Start transport
        |
6. Create rewind/world/collision services
        |
7. Create interaction context + handlers
        |
8. Create interaction manager + replication observer
        |
9. Start fixed-step interaction loop
        |
10. Start 120 Hz state publication loop
        |
11. Start diagnostics/statistics as desired
```

### 4.1 Register serializers first

Both client and server must call:

```java
NetworkSerializers.registerAll();
```

before exchanging application messages.

Do not create a server-only serializer order. The shared order is a wire compatibility contract.

### 4.2 Install SimEthereal before accepting normal session traffic

`SimEtherealServerAdapter.installOn(server)` adds the required SimEthereal service infrastructure to the SpiderMonkey server.

Do not replace this with reflection-based optional loading. The current source intentionally uses typed SimEthereal APIs.

### 4.3 Create authoritative services before interactions can begin

The interaction system assumes it can access:

- current/historical participant poses,
- authoritative collision shapes,
- authoritative server time,
- a replication sink.

Make those dependencies available before processing `InteractionIntentMessage` traffic.

---

## 5. Participant session lifecycle

### 5.1 Client connection

A network connection alone is not yet a fully registered participant.

The current application waits for `ClientHelloMessage` and then creates a participant session.

### 5.2 Registration

Current registration performs the following operations conceptually:

```text
ClientHello
    |
validate capacity
    |
sanitize display name
    |
assign server objectId
    |
create initial Pose3d
    |
create server session record
    |
register rewind participant
    |
start SimEthereal object hosting
    |
send AssignedIdentity to joiner
    |
send existing ParticipantInfo entries to joiner
    |
send existing ParticipantResultState snapshots to joiner
    |
broadcast new ParticipantInfo
    |
create/broadcast new zeroed result state
```

The current display-name policy:

- blank -> `Client-<connection id>`,
- strip surrounding whitespace,
- truncate to 32 characters.

The hue assignment is presentation metadata; applications may replace that policy.

### 5.3 Late joiners

Late join handling is deliberately explicit.

The new client receives:

- participant metadata for participants already present,
- current server-owned result-state snapshots.

SimEthereal separately supplies current shared-object transform state through its object replication service.

When adding new persistent server-owned state, include a late-join snapshot path rather than assuming clients will reconstruct prior events.

### 5.4 Disconnect

Current disconnect handling removes the participant from:

- session maps,
- SimEthereal hosting,
- rewind state,
- participant result state,

and broadcasts `ParticipantRemovedMessage`.

The prototype does not implement reconnection identity recovery or persistent account state.

---

## 6. Pose ingestion

### 6.1 Incoming data

`PoseInputMessage` contains:

- position,
- quaternion,
- client sequence number,
- client monotonic sample time.

The message is sent unreliably because newer pose samples supersede older ones.

### 6.2 Server acceptance

The current server:

1. converts the message into neutral pose data,
2. normalizes/cleans invalid numeric pose components,
3. clamps position to the configured world limit,
4. wraps it as `TimedPose3d`, preserving both client sample time and server receive time,
5. accepts it only if its client sequence is newer than the currently stored sequence.

This prevents stale/out-of-order samples from replacing newer state.

### 6.3 Do not mix clock domains

`TimedPose3d` deliberately preserves:

- client sample time,
- server receive time.

They are not interchangeable.

Do not compare raw client `System.nanoTime()` values directly with server `System.nanoTime()` values. They are independent monotonic clock domains.

---

## 7. Server state publication loop

The server state publisher is a scheduled single-thread executor running at `SERVER_STATE_HZ` (120 Hz).

Each frame conceptually does:

```text
serverNow = System.nanoTime()

SimEthereal.beginFrame(serverNow)

for each participant:
    pose = latest accepted server pose
    rewind.record(objectId, serverNow, pose)
    SimEthereal.updateEntity(objectId, pose)

SimEthereal.endFrame()

WorldStateSampler.publish(serverNow, snapshotPoses)
```

This alignment is important.

The same authoritative frame time is used for:

- SimEthereal shared-state publication,
- rewind history,
- the latest immutable world snapshot.

That means interaction simulation, collision, and client remote rendering can reason about a coherent server timeline.

### 7.1 Stronger production movement model

If another application already has a server simulation:

```text
DO NOT
client PoseInput -> directly become authoritative movement

DO
server simulation output -> publication frame -> rewind + SimEthereal + world snapshot
```

The networking architecture does not require client-authored poses. That is only the prototype's current locomotion source.

---

## 8. SimEthereal integration

`SimEtherealServerAdapter` intentionally localizes SimEthereal/SimMath types.

Application code should deal primarily in neutral types such as `Pose3d`.

### 8.1 State collection rate

The adapter configures SimEthereal state collection from `SERVER_STATE_HZ`.

### 8.2 Zone update cadence

The server also brackets each publication frame with `ZoneManager.beginUpdate(serverTime)` and `endUpdate()`.

These two ideas must remain distinct:

- **state collection interval**: how SimEthereal collects/sends shared state,
- **server/zone update cadence**: how often the application is actually advancing its authoritative frame.

The prototype intentionally runs its server frame at 120 Hz.

### 8.3 Known SimEthereal watchdog issue

At 120 Hz, stock SimEthereal may emit ZoneManager timing/watchdog warnings that were designed around a slower expected update interval.

Architectural policy from the prototype work:

- do not reduce the 120 Hz application cadence merely to silence the warning,
- do not suppress/filter the warning as the solution,
- treat expected ZoneManager update cadence as an upstream/library configurability issue,
- preserve backward-compatible library defaults when patching SimEthereal.

This warning does not redefine the server's desired timing model.

---

## 9. Clock synchronization service

The server handles `TimeSyncRequestMessage` by recording two server timestamps:

```text
t0 = client send

t1 = server receive

t2 = server send

t3 = client receive
```

The response returns the original client request data plus `t1` and `t2`.

The client estimates offset and RTT. The server does not need to maintain a per-client wall-clock mapping for normal operation.

Use monotonic time for simulation/timing. Do not substitute wall-clock time such as `Instant.now()` or `System.currentTimeMillis()` into this path.

---

## 10. Authoritative interaction ingestion

The current network request is `InteractionIntentMessage`.

A request contains intent data such as:

- client interaction sequence,
- synchronized event server time,
- interaction type ordinal,
- submitted origin/direction,
- optional target object ID.

The server derives the actor identity from the authenticated/connected session. The message does **not** get to choose its own authoritative actor object ID.

Current server support accepts `TRACER_PROJECTILE`; other enum entries are placeholders for future handlers.

The network listener should do minimal work:

```text
network thread
    |
validate basic session/type
    |
enqueue StartInteractionCommand
    |
return
```

Do not run authoritative projectile simulation directly on the SpiderMonkey message thread.

---

## 11. Fixed-step authoritative interaction loop

`FixedStepInteractionLoop` runs interaction simulation independently from network callbacks and UI/rendering.

Current configuration:

- 120 Hz fixed step,
- maximum 8 accumulated steps per wake,
- bounded catch-up behavior.

Conceptually:

```text
incoming command queue
        |
InteractionManager
        |
handler.start(...)
        |
optional historical catch-up
        |
active interaction
        |
fixed-step handler.update(...)
        |
authoritative events
        +--------------------+
        |                    |
 replication             server policy
        |                    |
 clients              result-state mutation
```

The interaction manager owns its active interaction map on the interaction thread. The command queue is the cross-thread handoff.

---

## 12. Rewind and historical world state

### 12.1 Server rewind

`ServerRewindService` stores approximately one second of pose history in server time.

Historical queries return explicit statuses such as:

- exact/interpolated pose available,
- too old,
- too new,
- participant not found,
- no history.

The rewind service does **not** extrapolate beyond recorded authoritative state.

### 12.2 Why no rewind extrapolation

For an authoritative hit test, fabricating a participant pose beyond known server history can create false hits or false misses.

If an event cannot be reconstructed within allowed tolerances, interaction policy should reject/fallback explicitly rather than silently extrapolate authoritative history.

### 12.3 WorldStateSampler

The interaction system does not query JavaFX nodes or SimEthereal visual objects for collision.

`WorldStateSampler` produces renderer-neutral `AuthoritativeWorldSnapshot` values containing entity poses and collision shapes.

For historical time `T`, it queries rewind state and reconstructs a snapshot appropriate for authoritative collision at `T`.

---

## 13. Collision integration

`CollisionService` operates on neutral geometry.

Current primitives include:

- `SweptSphere3d`,
- `OrientedBox3d`,
- `CompoundShape3d`.

`ParticipantCollisionShapeProvider` approximates the demo participant model as a compound shape.

An integrating application should normally replace `ParticipantCollisionShapeProvider` with application-specific hit geometry while preserving:

```java
CollisionShapeProvider
```

as the boundary.

### 13.1 Collision filters

Use `CollisionFilter` to exclude entities that should not participate in a particular sweep. The tracer handler excludes the actor from its own hit test.

### 13.2 Earliest-hit policy

The collision service returns the earliest valid hit along the swept segment, which is appropriate for a projectile/tracer that should stop on first contact.

---

## 14. Projectile/tracer authoritative workflow

`ProjectileInteractionHandler` is the reference implementation.

### Start validation

It validates:

- event time is not too far in the future,
- event time is not older than bounded catch-up history,
- direction is finite/nonzero and normalizable,
- actor existed at the event time,
- submitted client origin is sufficiently close to the authoritative rewound origin.

Current timing/geometry limits include:

- future tolerance: 20 ms,
- maximum catch-up: 500 ms,
- submitted-origin tolerance: 2 world units,
- tracer speed: 60 units/s,
- tracer lifetime: 500 ms,
- muzzle offset: 0.60,
- collision radius: 0.06.

### Authoritative origin

The actual projectile origin is derived from the actor's authoritative historical pose, not blindly trusted from the submitted client origin.

### Historical catch-up

If the intent describes an event that happened before the server processes it, the handler advances the projectile through historical world snapshots from its event time toward current server time.

This is what makes synchronized event timestamps and rewind useful together.

### Live simulation

After catch-up, subsequent fixed steps use the latest authoritative world snapshot and sweep the tracer segment against candidate entities.

On hit, the handler emits:

- `InteractionCollisionEvent`,
- `InteractionCompletedEvent`.

On expiration, it emits a completion event without a collision.

---

## 15. Interaction replication

`InteractionReplicationService` observes authoritative events.

Current replication policy:

| Authoritative event | Network output |
|---|---|
| `InteractionStartedEvent` | reliable `SharedInteractionMessage` |
| `InteractionCollisionEvent` | reliable `InteractionCollisionMessage` |
| `InteractionCompletedEvent` | no explicit message in current prototype |

Completion is currently inferred by the presentation/lifetime semantics rather than separately replicated.

If a future interaction requires a client-visible completion reason, add an explicit protocol representation rather than overloading collision messages.

---

## 16. Server-owned persistent result state

The current result-state policy demonstrates how durable-within-session server state should be handled.

```text
accepted tracer start
      |
ServerParticipantStateStore.recordShot(actor)
      |
reliable ParticipantResultStateMessage

valid authoritative hit
      |
recordHit(actor)
      |
hitCount + score mutation
      |
reliable ParticipantResultStateMessage
```

The current score policy is one point per hit.

Accuracy is derived from `hitCount / shotsFired`; it is not independently stored as authoritative state.

### 16.1 Why this is a separate subsystem

The interaction framework answers questions like:

- was an interaction accepted?
- where did a projectile travel?
- what did it collide with?

The result store answers:

- what is this participant's current accumulated state?

Do not force persistent application state into transient interaction objects.

---

## 17. Adding a new server-owned state domain

For health, inventory, objectives, team state, or similar data:

1. Define an immutable neutral state model.
2. Create a server-only mutation store/service.
3. Define the authoritative events that are allowed to mutate it.
4. Add a reliable state/snapshot message.
5. Register that message in `NetworkSerializers` on both sides.
6. Send initial/current snapshots to late joiners.
7. Maintain a client read-only replica/store.
8. Keep derived presentation values derived where possible.

Do not allow clients to send arbitrary “set state” messages for server-owned state.

---

## 18. Adding a new interaction type

A server integration normally requires:

1. Define or append a stable `InteractionType` value.
2. Define any additional intent fields if the generic intent does not carry enough information.
3. Implement `InteractionHandler`.
4. Register the handler with `InteractionManager`.
5. Define start validation and authoritative origin/state rules.
6. Decide whether historical catch-up is required.
7. Use `WorldStateSampler` / `CollisionService` as needed.
8. Emit lifecycle events.
9. Extend replication only for events clients actually need.
10. Extend server-owned state policy separately if results should accumulate.

**Protocol warning:** current messages encode `InteractionType` by `ordinal()`. Append new enum values; do not reorder existing values without a protocol migration.

---

## 19. Threading model

A production integration should preserve single-writer ownership where possible.

| Context | Primary responsibilities |
|---|---|
| SpiderMonkey/network threads | receive messages, validate basic envelope/session, enqueue work |
| State publisher thread | sample current server participant state, write rewind, publish SimEthereal, publish world snapshot |
| Authoritative interaction thread | start/update interactions, perform collision, emit interaction events |
| Stats/logger thread(s) | diagnostics only |

Avoid blocking network callbacks with simulation work.

Avoid sharing mutable interaction state directly between the publisher and interaction loops.

Use immutable snapshots and queues at thread boundaries.

---

## 20. Shutdown order

A clean shutdown should stop producers before destroying the transport/services they depend on.

Recommended order:

```text
1. stop accepting new application work
2. stop authoritative interaction loop
3. stop server state publisher
4. stop diagnostics/stat loggers
5. close/stop hosted participant services as needed
6. close SpiderMonkey server
7. terminate remaining owned executors
```

The exact calls depend on the containing application, but the principle is important: do not leave a scheduled publisher calling into a stopped SimEthereal/SpiderMonkey server.

---

## 21. Integration with an existing authoritative game simulation

If the target application already has an authoritative simulation, do not create a second competing movement model.

Use the existing simulation as the source of truth:

```text
existing server simulation
        |
neutral Pose3d/entity state
        +-------------------------+
        |                         |
SimEthereal publication      rewind history
        |                         |
remote clients              historical queries
                                  |
                            interaction/collision
```

The most reusable pieces are therefore the **boundaries and timing patterns**, not necessarily the demo's session pose container.

---

## 22. Security/authority hardening opportunities

The prototype intentionally does not solve every competitive-game security concern.

Potential production upgrades include:

- server-side locomotion simulation,
- movement acceleration/speed validation,
- authentication and account identity,
- authorization by interaction type,
- replay/sequence windows beyond simple latest-sequence checks,
- rate limiting,
- abuse-resistant interaction validation,
- transport/application encryption policy appropriate to deployment,
- reconnect/session resumption semantics.

These should be added without weakening the existing separation between intent and authoritative result.

---

## 23. Server integration checklist

- [ ] `NetworkSerializers.registerAll()` is called before application message exchange.
- [ ] Client and server use the same protocol version and serializer definitions.
- [ ] SimEthereal is installed before hosted objects are created.
- [ ] Participant object IDs are server assigned.
- [ ] Network callbacks do not directly mutate interaction-thread-owned state.
- [ ] Current server state is published on a monotonic server timeline.
- [ ] Rewind records the same authoritative frames that are replicated.
- [ ] `WorldStateSampler` uses renderer-neutral collision shapes.
- [ ] Interaction requests are treated as intent, not results.
- [ ] Actor identity comes from the server session.
- [ ] Historical interaction validation has explicit time bounds.
- [ ] Persistent state is mutated only by server policy.
- [ ] Late joiners receive current persistent snapshots.
- [ ] Reliable vs unreliable delivery is intentional per message type.
- [ ] Existing application simulation replaces, rather than competes with, demo locomotion authority.
- [ ] Shutdown stops simulation/publish loops before network teardown.
- [ ] 120 Hz timing is not reduced merely to hide SimEthereal timing warnings.

---

## 24. Recommended extraction for a reusable server API

The current `ServerMain.CollaborationServer` composition is private/nested and appropriate for a demo executable, but not an ideal external application API.

For a library extraction, create a public server facade that owns:

- connection configuration,
- lifecycle (`start`, `close`),
- participant callbacks/policies,
- an application-provided authoritative pose/entity source,
- interaction-handler registration,
- server-state observers if needed.

Keep lower-level services individually testable rather than exposing `ServerMain` itself as the library API.
