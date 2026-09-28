# Multiplayer API Documentation Index

## Purpose

This documentation set describes the current `spidermonkey-simethereal-javafx-demo` source snapshot as a reusable multiplayer architecture and integration reference.

The source snapshot contains 91 Java source files and uses protocol version **5**. Where older `README.md` or `VALIDATION.md` statements conflict with the current Java source, **the current source is authoritative**. In particular, the current code uses 120 Hz client motion, 120 Hz pose publication, 120 Hz server state publication, a 120 Hz authoritative interaction loop, typed SimEthereal adapters, adaptive remote interpolation/prediction, server rewind/collision, and replicated participant result state.

## Start here

| Need | Document |
|---|---|
| Understand the whole design | [`MULTIPLAYER_ARCHITECTURE.md`](MULTIPLAYER_ARCHITECTURE.md) |
| Give an AI coding agent the project rules | [`AI_AGENT_CONTEXT.md`](AI_AGENT_CONTEXT.md) |
| Add the networking stack to a 3D client | [`CLIENT_INTEGRATION_GUIDE.md`](CLIENT_INTEGRATION_GUIDE.md) |
| Embed the authoritative server | [`SERVER_INTEGRATION_GUIDE.md`](SERVER_INTEGRATION_GUIDE.md) |
| Inspect every application message | [`NETWORK_PROTOCOL.md`](NETWORK_PROTOCOL.md) |
| Understand motion synchronization | [`MOTION_SYNCHRONIZATION_PATTERN.md`](MOTION_SYNCHRONIZATION_PATTERN.md) |
| Add a synchronized interaction | [`AUTHORITATIVE_INTERACTION_PATTERN.md`](AUTHORITATIVE_INTERACTION_PATTERN.md) |
| Add server-owned replicated state | [`SERVER_OWNED_STATE_PATTERN.md`](SERVER_OWNED_STATE_PATTERN.md) |
| Integrate with JavaFX/another renderer | [`RENDERING_INTEGRATION_PATTERNS.md`](RENDERING_INTEGRATION_PATTERNS.md) |
| Find important classes by subsystem | [`API_CLASS_REFERENCE.md`](API_CLASS_REFERENCE.md) |
| Move this prototype into another application | [`PORTING_CHECKLIST.md`](PORTING_CHECKLIST.md) |

## Recommended reading paths

### Human integrator

1. `MULTIPLAYER_ARCHITECTURE.md`
2. `CLIENT_INTEGRATION_GUIDE.md` or `SERVER_INTEGRATION_GUIDE.md`
3. `NETWORK_PROTOCOL.md`
4. The pattern document for the subsystem being changed
5. `API_CLASS_REFERENCE.md`
6. `PORTING_CHECKLIST.md`

### AI coding agent

1. `AI_AGENT_CONTEXT.md`
2. `MULTIPLAYER_ARCHITECTURE.md`
3. The relevant integration/pattern document
4. `NETWORK_PROTOCOL.md` before changing any message or serializer
5. `API_CLASS_REFERENCE.md` before introducing a new class that may duplicate an existing responsibility

## Current technology baseline

- Java 21
- JavaFX 21.0.9
- jMonkeyEngine `jme3-networking` / SpiderMonkey 3.9.0-stable
- jMonkeyEngine `jme3-core` 3.9.0-stable
- SimEthereal 1.8.0
- SimMath 1.6.0
- SLF4J 1.7.36
- Maven build

The POM deliberately excludes/overrides older transitive jME artifacts brought by SimEthereal and enables Maven dependency convergence.

## Current network/timing baseline

- Application name: `JavaFx3dCollaborationDemo`
- Protocol version: `5`
- Default host: `localhost`
- TCP port: `6143`
- UDP port: `6144`
- Maximum participants: `12`
- Client motion: `120 Hz`
- Client pose publication: `120 Hz`
- Server shared-state publication: `120 Hz`
- Server interaction simulation: `120 Hz`
- Client remote history: `500 ms`
- Server rewind history: `1 s`
- Adaptive remote interpolation delay: `8-30 ms`, `12 ms` default
- Maximum remote extrapolation: `25 ms`
- Clock synchronization probe: once per second

## Core architectural distinction

The prototype has multiple authority models:

- **Local camera motion:** simulated immediately on the client.
- **Participant pose publication:** server accepts the latest sequence-valid, normalized, world-clamped client pose and republishes it through SimEthereal. This is authoritative distribution, but not a server-simulated anti-cheat movement model.
- **Interactions:** server-authoritative acceptance, historical catch-up, simulation, and collision.
- **Persistent result state:** server-owned and replicated reliably.

Do not describe the current movement path as fully server-authoritative movement simulation.

## Source layout

```text
com.example.collab3d.client
    Client composition, local motion, remote motion resolution,
    clock sync, JavaFX rendering, client interaction presentation

com.example.collab3d.common
    Renderer-neutral data models, protocol constants, serializer registry,
    diagnostics

com.example.collab3d.common.geometry
    Renderer-neutral collision geometry

com.example.collab3d.common.interactions
    Shared interaction value objects and type identifiers

com.example.collab3d.common.messages
    SpiderMonkey application messages

com.example.collab3d.server
    Server composition, participant sessions, rewind, persistent result state

com.example.collab3d.server.collision
    Authoritative world snapshots and collision services

com.example.collab3d.server.interaction
    Generic interaction lifecycle framework plus tracer projectile example

com.example.collab3d.simethereal
    The only package that should know SimEthereal/SimMath concrete types
```

## Existing-document caveat

The repository's older `README.md` and `VALIDATION.md` were written at an earlier implementation stage. Examples of stale descriptions include a 30 Hz state rate, simple smoothing, and a reflective SimEthereal adapter. Use this documentation set and the current Java source for the current architecture.
