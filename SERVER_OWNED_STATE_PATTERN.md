# Server-Owned State Pattern

## 1. Purpose

This document describes the prototype's pattern for **persistent-within-session application state that must be owned and mutated by the authoritative server**.

The current scoreboard/result-state implementation is the canonical example. The same pattern can be reused for:

- health,
- inventory,
- ammunition,
- objectives,
- team scores,
- capture state,
- match progress,
- permissions/roles,
- other authoritative participant or world state.

The important pattern is broader than the scoreboard UI.

---

## 2. Current canonical state

`ParticipantResultState` contains:

```text
objectId
shotsFired
hitCount
score
```

Accuracy is derived:

```text
accuracyPercent = shotsFired == 0
    ? 0
    : 100 * hitCount / shotsFired
```

It is deliberately not stored as an independent authoritative field because it is deterministically derived from authoritative counters.

---

## 3. End-to-end flow

```text
AUTHORITATIVE INTERACTION EVENT
             |
             v
SERVER POLICY
             |
             v
ServerParticipantStateStore
             |
      immutable snapshot
             |
             v
ParticipantResultStateMessage (reliable)
             |
             v
CLIENT ParticipantResultStore
             |
             v
read-only presentation
             |
             v
ClientScoreboardOverlay
```

No client UI action directly changes authoritative result state.

---

## 4. Authority rule

The server is the only writer of `ParticipantResultState`.

Clients receive replicated snapshots and may:

- display them,
- sort them,
- derive presentation values,
- use them for non-authoritative local UI.

Clients do **not** send:

```text
setScore(...)
setHitCount(...)
setShotsFired(...)
```

messages to the server.

The authoritative state changes only because a server policy observes an authoritative fact.

---

## 5. Current scoreboard policy

### Accepted shot

```text
InteractionStartedEvent(TRACER_PROJECTILE)
        |
recordShot(actorObjectId)
        |
shotsFired += 1
        |
broadcast current ParticipantResultState
```

### Authoritative participant hit

```text
InteractionCollisionEvent
        |
target is a known participant
        |
target != actor
        |
recordHit(actorObjectId)
        |
hitCount += 1
score += 1
        |
broadcast current ParticipantResultState
```

This means a shot is counted when the **server accepts** the tracer interaction, not merely when the user presses the local fire key.

Likewise, a hit is counted from the **server's collision event**, not from a client's visual estimate.

---

## 6. Why mutate from authoritative events

The interaction system has already established facts such as:

- this shot was accepted,
- this collision occurred,
- this target was hit at this time.

Server policy can safely transform those facts into persistent state.

This prevents duplicated validation logic such as having the score subsystem independently decide whether the shot should have existed.

The preferred dependency direction is:

```text
interaction/collision facts
          |
          v
application state policy
```

not:

```text
scoreboard logic -> interaction simulation
```

---

## 7. Store responsibilities

`ServerParticipantStateStore` currently owns participant result records.

Its responsibilities include:

- register a participant with zeroed state,
- remove state on disconnect,
- increment accepted-shot count,
- increment hit count and score,
- return current immutable state,
- return snapshots for replication/late join.

The store is server-side implementation state. Clients should not share or mutate the same store object.

---

## 8. Immutable shared model

`ParticipantResultState` is an immutable record suitable for crossing subsystem boundaries.

This is preferable to exposing a mutable server entity to:

- network serialization,
- UI code,
- logging,
- tests.

General rule:

> Keep authoritative mutation internal; expose immutable snapshots externally.

---

## 9. Replication message

`ParticipantResultStateMessage` carries the complete current participant result state:

- `objectId`,
- `shotsFired`,
- `hitCount`,
- `score`.

It is sent reliably.

This is a **state snapshot**, not a delta command.

Clients can replace their prior replica for the same object ID with the new state.

### Why snapshot instead of increment events

A snapshot makes the client idempotent at the application level:

```text
old local state + "hit increment"
```

requires exact once-only event application, whereas:

```text
replace local state with server snapshot
```

is much easier to recover and reason about.

For small state objects and small sessions, the snapshot cost is trivial.

---

## 10. Client replica

`ParticipantResultStore` is a client-side concurrent map of the latest replicated state.

It is not an authority.

Appropriate operations are:

- update/replace from a server message,
- remove when a participant leaves if desired,
- get current state,
- produce a sorted snapshot for UI.

Do not add application logic that mutates this store in anticipation of a server result unless it is clearly marked as provisional presentation state and separately reconciled.

---

## 11. Late-join snapshots

A new participant did not observe earlier shots/hits. Event history alone therefore cannot construct the current scoreboard unless the entire past is replayed.

The server explicitly sends the **current result snapshot** for existing participants during registration.

Pattern:

```text
new participant joins
       |
server enumerates current authoritative state
       |
reliable snapshot messages
       |
new client replica immediately converges
```

Any future persistent-within-session state should define a late-join path at design time.

---

## 12. Participant removal

The current server result store removes a participant's result state on disconnect.

Therefore current semantics are:

> participant result state is durable within the active participant session, not across disconnect/reconnect or server restart.

If another application requires persistence across reconnection, identity must no longer be defined solely by transient connection/object ID. Introduce an authenticated persistent identity and a durable server datastore.

Do not silently change the current `objectId` lifecycle into an account identity.

---

## 13. State vs event replication

Use state and events for different purposes.

### Events

Good for:

- “projectile started,”
- “collision happened,”
- “objective completed,”
- short-lived presentation effects.

### State snapshots

Good for:

- current health,
- current score,
- current inventory,
- current team totals,
- current objective ownership.

A late joiner usually needs state snapshots even if live clients also receive events.

---

## 14. Derived state

Store the smallest authoritative basis when possible.

Current example:

```text
stored:
    shotsFired
    hitCount
    score

derived:
    accuracy
```

Benefits:

- fewer fields that can disagree,
- simpler updates,
- simpler late-join snapshots,
- clearer authority.

For future domains, consider:

```text
healthPercent derived from health/maxHealth
inventoryWeight derived from inventory entries
accuracy derived from hit/shot counters
objectiveProgress derived from objective state
```

Do not store derived values independently unless there is a semantic reason they can diverge.

---

## 15. Adding health using the pattern

A clean health subsystem could look like:

```text
Authoritative collision/damage event
        |
ServerHealthStore.applyDamage(target, amount)
        |
HealthState(target, current, max, status)
        |
HealthStateMessage (reliable)
        |
ClientHealthStore
        |
HUD / participant presentation
```

The client may request an attack. It does not report its target's resulting health.

---

## 16. Adding inventory using the pattern

```text
client pickup intent
        |
server validates item + player + range
        |
server inventory mutation
        |
InventoryState snapshot/delta under server version
        |
client replica
        |
UI
```

Inventory may be larger than scoreboard state, so full snapshots on every mutation may eventually become expensive. The authority pattern remains the same even if replication evolves to versioned deltas plus periodic snapshots.

---

## 17. Adding objectives/team state

For shared state not owned by one participant, use a world/team key instead of forcing it into `ParticipantResultState`.

Example:

```text
ObjectiveState(objectiveId, ownerTeam, progress, revision)
```

The important properties remain:

- server-only mutation,
- immutable external representation,
- reliable replication,
- late-join snapshot,
- presentation-only client replica.

---

## 18. Revisions/versioning for more complex state

The current scoreboard does not require an explicit revision because reliable ordered delivery and simple replacement are sufficient for the demo.

For more complex systems, consider adding:

```text
revision / version / authoritativeTimestamp
```

so the client can reject stale state if messages may arrive from multiple channels or asynchronous producers.

Do not add version numbers reflexively; add them when there is an actual ordering/concurrency need.

---

## 19. Threading

Authoritative state mutation should have a clear ownership policy.

In the current implementation, result-state changes are triggered from authoritative interaction events and handled as server policy.

If future state can be mutated by multiple server subsystems, avoid ad hoc concurrent mutation of shared mutable objects.

Prefer one of:

- a single state-owner thread,
- commands into a state service,
- atomic/locked transactional mutation with immutable snapshots.

The client replica can remain concurrent/read-oriented because multiple presentation/network contexts may access it.

---

## 20. Persistence beyond the process

The current store is in memory.

It does not persist across:

- participant disconnect/rejoin,
- server restart,
- process failure.

If durable persistence is required, add it behind the server-owned state service rather than allowing clients to become the recovery source.

A durable pattern would be:

```text
authoritative mutation
      |
in-memory current state
      +--> reliable client replication
      |
      +--> durable datastore/event log
```

The datastore is still a server concern.

---

## 21. Presentation is downstream

`ClientScoreboardOverlay` sorts current replicated states and renders:

- player,
- shots,
- hits,
- accuracy,
- score.

Current sort preference is approximately:

1. score descending,
2. hits descending,
3. display name / ID tie-break.

That order is a presentation rule. It does not affect authoritative state.

A different UI can use the same `ParticipantResultStore` without changing networking or simulation.

---

## 22. Incorrect patterns to avoid

### Client increments its own score immediately and tells server later

This makes the client an authority and creates reconciliation problems.

### Score is embedded only in transient projectile objects

Late joiners and completed interactions would lose current state.

### UI parses collision effects to infer hits

Rendering is not an authoritative data source.

### Server sends only “increment score” events forever

This makes recovery/late join unnecessarily difficult without a snapshot/version mechanism.

### Accuracy is stored independently from shots/hits

This creates a redundant value that can drift.

---

## 23. Extension checklist

For every new server-owned state domain:

- [ ] Identify the authoritative owner.
- [ ] Define immutable shared state.
- [ ] Define which authoritative events/commands may mutate it.
- [ ] Keep client intent separate from server mutation.
- [ ] Create a server-only state store/service.
- [ ] Decide snapshot vs delta replication.
- [ ] Use reliable delivery for state that must converge.
- [ ] Define late-join behavior.
- [ ] Define disconnect/reconnect semantics.
- [ ] Define whether state survives server restart.
- [ ] Add client read-only replica/store.
- [ ] Keep UI downstream of the replica.
- [ ] Derive redundant presentation values rather than storing them where possible.
- [ ] Add revision/timestamp only if ordering semantics require it.

---

## 24. Pattern summary

```text
CLIENT INTENT
    |
    v
AUTHORITATIVE SERVER EVENT
    |
    v
SERVER POLICY
    |
    v
SERVER-OWNED MUTABLE STORE
    |
    v
IMMUTABLE STATE SNAPSHOT
    |
    v
RELIABLE REPLICATION
    |
    v
CLIENT READ-ONLY REPLICA
    |
    v
UI / PRESENTATION
```

The current scoreboard is intentionally small, but this ownership pattern is one of the most reusable architectural results of the prototype.
