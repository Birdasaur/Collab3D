# Network Protocol Reference

## 1. Scope

This document describes the **actual application message classes in the current source snapshot**. It is not a hypothetical protocol design.

Current protocol version: **5**.

The protocol sits beside SimEthereal's own shared-object/state transport. Application messages handle identity, participant metadata, pose input, clock synchronization, authoritative interaction events, and persistent participant result state. SimEthereal handles replicated participant transform state.

---

## 2. Compatibility rules

### 2.1 Shared serializer registration is a protocol contract

Both client and server call:

```java
NetworkSerializers.registerAll();
```

The current registration order is:

```text
 1. ClientHelloMessage
 2. AssignedIdentityMessage
 3. ParticipantInfoMessage
 4. ParticipantRemovedMessage
 5. PoseInputMessage
 6. ServerNoticeMessage
 7. TimeSyncRequestMessage
 8. TimeSyncResponseMessage
 9. InteractionIntentMessage
10. SharedInteractionMessage
11. InteractionCollisionMessage
12. ParticipantResultStateMessage
```

Do not independently reorder or conditionally register these classes on one side.

`NetworkSerializers` uses an `AtomicBoolean` to make registration idempotent within a JVM.

### 2.2 Interaction enum ordinals are currently on the wire

`InteractionIntentMessage` and `SharedInteractionMessage` encode the interaction type using `InteractionType.ordinal()`.

Current order:

```text
0 POINTER_RAY
1 SELECTION_CLEAR
2 SELECTION_SET
3 TRACER_PROJECTILE
```

**Do not reorder or insert values ahead of existing values without a protocol migration.**

Prefer appending new values while this ordinal encoding remains in use. A future protocol should consider explicit stable numeric type IDs.

### 2.3 Protocol version evolution

`NetworkConstants.PROTOCOL_VERSION` is currently 5.

Source history represented in the current serializer comments indicates:

- version 4 introduced interaction messages,
- version 5 introduced persistent participant result state.

When a breaking message/schema/serializer change is made, increment the protocol version and define the compatibility behavior explicitly.

---

## 3. Reliability summary

| Message | Direction | Delivery policy |
|---|---|---|
| `ClientHelloMessage` | client -> server | reliable default |
| `AssignedIdentityMessage` | server -> client | reliable default |
| `ParticipantInfoMessage` | server -> client | reliable default |
| `ParticipantRemovedMessage` | server -> client | reliable default |
| `PoseInputMessage` | client -> server | **unreliable** |
| `ServerNoticeMessage` | server -> client | reliable default |
| `TimeSyncRequestMessage` | client -> server | **reliable** |
| `TimeSyncResponseMessage` | server -> client | **reliable** |
| `InteractionIntentMessage` | client -> server | **reliable** |
| `SharedInteractionMessage` | server -> client(s) | **reliable** |
| `InteractionCollisionMessage` | server -> client(s) | **reliable** |
| `ParticipantResultStateMessage` | server -> client(s) | **reliable** |

“Reliable default” means the current source does not override SpiderMonkey `AbstractMessage`'s reliable default at that send path. `PoseInputMessage` explicitly sets unreliable delivery.

### Why pose input is unreliable

Pose samples are superseding state. If sample N is delayed behind N+1, waiting for N does not improve the current pose.

### Why interaction/result messages are reliable

Accepted interactions, collision results, and persistent result snapshots represent discrete authoritative facts that should not silently disappear because a UDP packet was lost.

---

## 4. `ClientHelloMessage`

### Purpose

Begins application-level participant registration after the SpiderMonkey connection has been established.

### Direction

```text
client -> server
```

### Fields

| Field | Type/concept | Meaning |
|---|---|---|
| `displayName` | String | Requested participant display name |

### Server handling

The server:

- enforces session capacity,
- sanitizes the display name,
- allocates a server object ID,
- creates participant/session state,
- starts SimEthereal hosting,
- sends identity/roster/current result state.

The display name is not an authentication credential.

---

## 5. `AssignedIdentityMessage`

### Purpose

Tells the newly registered client which server-assigned participant object it owns.

### Direction

```text
server -> registering client
```

### Fields

| Field | Meaning |
|---|---|
| `objectId` | Server-assigned participant/shared-object ID |
| `displayName` | Sanitized server-approved display name |

### Client handling

The client records its local object ID and uses it to distinguish local vs remote replicated participants.

Clients must not invent their own authoritative object IDs.

---

## 6. `ParticipantInfoMessage`

### Purpose

Replicates participant metadata that is not part of the SimEthereal pose object.

### Direction

```text
server -> client(s)
```

### Fields

| Field | Meaning |
|---|---|
| `objectId` | Participant object ID |
| `displayName` | Display name |
| `hueDegrees` | Presentation hue assigned by server |

### Lifecycle

On join:

- the joining client receives existing participants,
- existing clients receive the new participant.

This message creates/updates metadata; transform state arrives through SimEthereal.

---

## 7. `ParticipantRemovedMessage`

### Purpose

Removes a participant from client-side metadata/rendering/state after disconnect.

### Direction

```text
server -> client(s)
```

### Fields

| Field | Meaning |
|---|---|
| `objectId` | Participant that left |

### Client handling

Remove associated:

- participant metadata,
- remote pose/render state,
- UI labels,
- any client replica state that should not survive session removal.

The server separately stops SimEthereal object hosting.

---

## 8. `PoseInputMessage`

### Purpose

Publishes the local client's newest movement pose to the server.

### Direction

```text
client -> server
```

### Delivery

**Unreliable.**

### Fields

| Field | Meaning |
|---|---|
| `x`, `y`, `z` | Position |
| `qx`, `qy`, `qz`, `qw` | Orientation quaternion |
| `clientSequence` | Monotonically increasing client pose sequence |
| `clientSampleTimeNanos` | Client monotonic time when pose was sampled |

### Client send rate

Current default: **120 Hz**.

### Server handling

The server:

1. converts to neutral pose data,
2. sanitizes/normalizes the pose,
3. clamps position to world bounds,
4. records server receive time separately,
5. accepts only a sequence newer than the currently stored client sequence.

### Important timestamp rule

`clientSampleTimeNanos` is in the client's `System.nanoTime()` domain. It is not directly comparable to server monotonic timestamps.

The server's authoritative replicated pose timeline is stamped by the server publication frame.

---

## 9. `ServerNoticeMessage`

### Purpose

Carries server-generated human-readable status/notice text.

### Direction

```text
server -> client
```

### Fields

| Field | Meaning |
|---|---|
| `text` | Notice text |

### Use

Operational/demo notification only. Do not build critical machine state transitions by parsing notice strings.

---

## 10. `TimeSyncRequestMessage`

### Purpose

Starts an NTP-like monotonic clock synchronization sample.

### Direction

```text
client -> server
```

### Delivery

**Reliable.**

### Fields

| Field | Meaning |
|---|---|
| `sequence` | Time-sync request sequence |
| `clientSendTimeNanos` | Client `t0` monotonic timestamp |

### Current cadence

One request per second, with bounded outstanding requests.

---

## 11. `TimeSyncResponseMessage`

### Purpose

Returns server timestamps required for client-side RTT/offset estimation.

### Direction

```text
server -> client
```

### Delivery

**Reliable.**

### Fields

| Field | Timing symbol | Meaning |
|---|---|---|
| `sequence` | — | Matches request |
| `clientSendTimeNanos` | `t0` | Original client send time |
| `serverReceiveTimeNanos` | `t1` | Server receive time |
| `serverSendTimeNanos` | `t2` | Server response send time |

The client records `t3` when the response arrives.

### Client computation

```text
roundTrip = (t3 - t0) - (t2 - t1)
offset    = ((t1 - t0) + (t2 - t3)) / 2
```

where offset maps client monotonic time toward server monotonic time.

The client maintains a rolling sample window and derives its current estimate from the lowest-RTT samples.

---

## 12. `InteractionIntentMessage`

### Purpose

Carries a **client request/intent** to begin an interaction. It is not an authoritative interaction result.

### Direction

```text
client -> server
```

### Delivery

**Reliable.**

### Fields

| Field | Meaning |
|---|---|
| `interactionSequence` | Client-local interaction sequence |
| `eventServerTimeNanos` | Client estimate of server time at the interaction event |
| `interactionTypeOrdinal` | `InteractionType.ordinal()` |
| `originX`, `originY`, `originZ` | Submitted origin hint |
| `directionX`, `directionY`, `directionZ` | Submitted interaction direction |
| `targetObjectId` | Optional/request target, `-1` when absent |

### Trust boundary

The message intentionally does not carry an authoritative actor ID. The server derives the actor from the connection/session.

For the tracer handler, the submitted origin is only a validation hint. The authoritative origin is reconstructed from server rewind.

### Current supported type

The server's current network path handles `TRACER_PROJECTILE`.

---

## 13. `SharedInteractionMessage`

### Purpose

Replicates an interaction that the server has accepted and instantiated authoritatively.

### Direction

```text
server -> client(s)
```

### Delivery

**Reliable.**

### Fields

| Field | Meaning |
|---|---|
| `interactionId` | Server-assigned unique interaction ID |
| `actorObjectId` | Authoritative actor participant ID |
| `interactionSequence` | Actor's original client interaction sequence |
| `eventServerTimeNanos` | Authoritative interaction event time |
| `acceptedServerTimeNanos` | Server time when start was accepted |
| `interactionTypeOrdinal` | `InteractionType.ordinal()` |
| `originX`, `originY`, `originZ` | Authoritative interaction origin |
| `directionX`, `directionY`, `directionZ` | Authoritative direction |
| `targetObjectId` | Optional target ID, `-1` when absent |

### Identity/keying

The client tracer presentation uses the pair:

```text
(actorObjectId, interactionSequence)
```

to reconcile a locally rendered provisional interaction with the later authoritative version.

`interactionId` is the server's canonical interaction identity once accepted.

---

## 14. `InteractionCollisionMessage`

### Purpose

Replicates an authoritative collision result for a shared interaction.

### Direction

```text
server -> client(s)
```

### Delivery

**Reliable.**

### Fields

| Field | Meaning |
|---|---|
| `interactionId` | Server interaction ID |
| `actorObjectId` | Interaction actor |
| `clientSequence` | Original actor interaction sequence |
| `targetObjectId` | Authoritative collision target |
| `eventServerTimeNanos` | Server timeline time of impact |
| `positionX`, `positionY`, `positionZ` | Authoritative impact point |
| `normalX`, `normalY`, `normalZ` | Authoritative impact normal |

### Client handling

Presentation code may move a visible tracer to the authoritative impact position and show an impact effect.

The client must not convert this into a new client-authored hit decision. The collision is already authoritative.

---

## 15. `ParticipantResultStateMessage`

### Purpose

Replicates the current server-owned participant result state.

### Direction

```text
server -> client(s)
```

### Delivery

**Reliable.**

### Fields

| Field | Meaning |
|---|---|
| `objectId` | Participant ID |
| `shotsFired` | Accepted shot count |
| `hitCount` | Authoritative valid hit count |
| `score` | Server-owned score |

### Semantics

Current server policy:

```text
accepted tracer start -> shotsFired + 1
valid authoritative hit -> hitCount + 1, score + 1
```

Accuracy is derived on the client/model side and is not transmitted as separate authoritative state.

### Late join

The server sends the current result-state snapshot for existing participants to a newly registered participant.

This is state replication, not event replay.

---

## 16. SimEthereal state channel

Application message classes do not carry normal remote pose replication from server to clients.

The high-frequency transform path is:

```text
client PoseInputMessage
      |
server latest participant pose
      |
server publication frame @ 120 Hz
      |
SimEtherealServerAdapter.updateEntity(...)
      |
SimEthereal shared-object protocol
      |
SimEtherealClientAdapter.objectUpdated(...)
      |
RemotePoseStore
```

This separation is intentional.

Do not add a second custom server-to-client pose message unless there is a specific architectural reason to replace or supplement SimEthereal.

---

## 17. Participant join sequence

A representative application-level sequence is:

```text
Client                                          Server
  |                                                |
  |--- SpiderMonkey connection ------------------->|
  |                                                |
  |--- ClientHello(displayName) ------------------->|
  |                                                |
  |<-- AssignedIdentity(objectId, name) ------------|
  |<-- ParticipantInfo(existing...) ----------------|
  |<-- ParticipantResultState(existing...) ---------|
  |                                                |
  |<-- ParticipantInfo(new participant broadcast) --|--> other clients
  |<-- ParticipantResultState(new zero state) -------|--> other clients
  |                                                |
  |     SimEthereal object state begins             |
```

Exact ordering among independent broadcast/state service deliveries should not be used as an application invariant unless explicitly enforced.

Client stores/renderers should tolerate metadata and pose-state arrival being close but not perfectly ordered.

---

## 18. Clock synchronization sequence

```text
Client                                  Server
  |                                       |
  | t0                                    |
  |-- TimeSyncRequest(seq,t0) ----------->|
  |                                       | t1 receive
  |                                       | t2 send
  |<-- TimeSyncResponse(seq,t0,t1,t2) ----|
  | t3 receive                            |
  |                                       |
  | compute RTT + offset                  |
```

The mapping is used for:

- selecting a remote render time in server coordinates,
- timestamping interaction intents in the server timeline.

---

## 19. Tracer interaction sequence

```text
Client actor                         Server                         All clients
    |                                  |                                |
    | local provisional render         |                                |
    |                                  |                                |
    |-- InteractionIntent ------------>|                                |
    |                                  | validate/rewind/start           |
    |                                  |                                |
    |                                  |-- SharedInteraction ----------->|
    |                                  |                                | reconcile/show
    |                                  | fixed-step simulation           |
    |                                  | rewind/live collision           |
    |                                  |                                |
    |                                  |-- InteractionCollision -------->|
    |                                  |                                | impact presentation
```

The actor does not wait for the round trip to show a tracer. It reconciles its provisional effect when the authoritative start arrives.

---

## 20. Result-state sequence

```text
InteractionStartedEvent(TRACER)
        |
server recordShot(actor)
        |
ParticipantResultStateMessage
        |
client ParticipantResultStore
        |
scoreboard/UI

InteractionCollisionEvent(valid target)
        |
server recordHit(actor)
        |
ParticipantResultStateMessage
        |
client ParticipantResultStore
        |
scoreboard/UI
```

Result-state replication is intentionally independent from the visual tracer lifecycle.

---

## 21. Sequencing rules

### Pose sequence

`clientSequence` is monotonically increasing for local pose publication. The server rejects an older/equal pose sequence from replacing the current one.

### Interaction sequence

`interactionSequence` is client-local and monotonically increases for interaction requests.

It serves two important purposes:

- correlating provisional client presentation with authoritative replication,
- identifying an actor's request independently of the server-assigned interaction ID.

### Time-sync sequence

Time sync uses its own sequence space to correlate responses with outstanding requests.

Do not merge these sequence counters merely because they are all integers/longs. They represent different streams.

---

## 22. Timestamp domains

The protocol uses several timestamps. Their domain matters.

| Timestamp | Clock domain |
|---|---|
| `PoseInputMessage.clientSampleTimeNanos` | sender client monotonic clock |
| server pose receive time | server monotonic clock |
| SimEthereal published frame time | server/shared timeline |
| `InteractionIntentMessage.eventServerTimeNanos` | client's estimate of server monotonic time |
| `SharedInteraction.eventServerTimeNanos` | authoritative server timeline |
| collision `eventServerTimeNanos` | authoritative server timeline |

Never assume `System.nanoTime()` values from different JVMs are numerically comparable without clock synchronization.

---

## 23. Adding a message safely

When adding a new application message:

1. Define a no-argument construction path compatible with SpiderMonkey serialization if required by the chosen serializer pattern.
2. Keep field semantics explicit and stable.
3. Register the class in `NetworkSerializers` on both client and server.
4. Decide delivery semantics intentionally.
5. Add handling on the correct thread boundary.
6. Increment the protocol version if compatibility is broken.
7. Document late-join behavior if the message represents persistent state.
8. Do not duplicate an existing SimEthereal responsibility without a reason.

---

## 24. Reliable vs unreliable design guidance

Use **unreliable** delivery when all of the following are true:

- data is high-frequency,
- newer data supersedes older data,
- loss can be repaired by a future update,
- head-of-line waiting is more harmful than loss.

Use **reliable** delivery for:

- identity/lifecycle,
- discrete accepted interactions,
- authoritative collision facts,
- persistent state snapshots,
- synchronization exchanges whose loss is not useful.

Do not blindly make every message reliable simply because the session is small.

---

## 25. Current protocol limitations

The current protocol does not define:

- authentication credentials,
- reconnect/resume tokens,
- room/lobby/match identifiers,
- general inventory/health/objective messages,
- arbitrary entity replication outside SimEthereal participant objects,
- generic RPC semantics,
- protocol schema negotiation,
- stable explicit interaction type IDs independent of enum ordinal.

Add these as explicit protocol concepts if the target application requires them.
