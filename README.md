# SpiderMonkey + SimEthereal JavaFX 3D collaboration prototype

This is a single Maven codebase with two independent entry points:

- `com.example.collab3d.server.ServerMain`
- `com.example.collab3d.client.ClientMain`

Run the server in one JVM and each client in a separate JVM. Clients connect to
`localhost` automatically using TCP port **6143** and UDP port **6144**.

## Configuration under test

```text
SimEthereal                    1.8.0
jme3-networking / SpiderMonkey 3.9.0-stable
jme3-core                      3.9.0-stable
Java                           27
JavaFX                         27
```

The POM deliberately overrides SimEthereal's old transitive jME dependency and
pins both jME artifacts to 3.9.0-stable. Maven Enforcer's dependency-convergence
rule is enabled so that a mixed jME dependency graph is rejected.

## What the prototype demonstrates

- Plain Java SpiderMonkey/SimEthereal server; no jME `Application`, renderer,
  scene graph, LWJGL, or native graphics libraries.
- JavaFX 3D client with a `PerspectiveCamera`.
- Mouse-drag look control.
- W/A/S/D movement, Q/E vertical movement, and Shift speed boost.
- Client pose input sent to the server as unreliable SpiderMonkey messages.
- Authoritative transform publication from the server through SimEthereal at
  30 Hz.
- Remote participants rendered as a camera body, direction cylinder, and
  tetrahedral arrow head pointing along the camera's local +Z direction.
- A smoothing checkbox for comparing raw received transforms with client-side
  exponential position and quaternion smoothing.
- A neutral `Pose3d` API between JavaFX and the networking implementation.

## Build

Requirements:

- JDK 27
- Maven 3.9 or later

```text
mvn -U -DskipTests clean package
```

Inspect the critical dependency override:

```text
mvn -DskipTests dependency:tree \
  -Dincludes=com.simsilica:*,org.jmonkeyengine:*
```

The output should resolve both `jme3-networking` and `jme3-core` to
`3.9.0-stable`. No jME `3.1.0-stable` artifact should remain.

## Run locally

Open three terminals in the project directory.

### Terminal 1: server

```text
mvn -Pserver -DskipTests exec:java
```

PowerShell shortcut:

```text
.\run-server.ps1
```

### Terminal 2: first client

```text
mvn -Pclient -DskipTests "-Dclient.args=--name=Alice" javafx:run
```

PowerShell shortcut:

```text
.\run-client.ps1 Alice
```

### Terminal 3: second client

```text
mvn -Pclient -DskipTests "-Dclient.args=--name=Bob" javafx:run
```

PowerShell shortcut:

```text
.\run-client.ps1 Bob
```

Click inside each 3D viewport before using keyboard controls.

## Test procedure

1. Start the server.
2. Start Alice and Bob as separate client JVMs.
3. Move Alice with W/A/S/D and rotate her camera by dragging the mouse.
4. Confirm Bob sees Alice's composite arrow move and rotate correctly.
5. Repeat in the other direction.
6. Toggle **Smooth remote poses** independently in each client. With smoothing
   disabled, the arrow displays the most recently received authoritative pose.
   With smoothing enabled, position is interpolated and orientation uses
   quaternion spherical interpolation.
7. Watch `Pose updates` and the diagnostics pane for traffic and adapter output.

## Architecture

```text
JavaFX camera
    |
    | FxPoseCodec
    v
neutral Pose3d
    |
    | unreliable SpiderMonkey input message
    v
plain Java server
    |
    | 30 Hz authoritative ZoneManager update
    v
SimEthereal / SpiderMonkey UDP state stream
    |
    v
neutral Pose3d mailbox
    |
    | JavaFX AnimationTimer
    v
remote JavaFX arrow/frustum indicator
```

The JavaFX classes do not import SimEthereal or SimMath types. The networking
adapter does not import JavaFX types. All SimEthereal setup and callback decoding
is confined to `com.example.collab3d.simethereal`.

## Why the SimEthereal adapter uses reflection

SimEthereal 1.8.0 was published against an older jME networking release. This
prototype intentionally tests it with jME 3.9.0-stable. The small reflective
adapter does two things:

1. confines all SimEthereal and SimMath implementation types behind neutral
   records; and
2. reports constructor, service-registration, or `SharedObject` accessor
   differences directly at startup instead of spreading version-dependent code
   through the JavaFX client.

Once Configuration B is proven on the target machine, the reflective calls can
be replaced by direct typed calls in the four classes under
`com.example.collab3d.simethereal` without changing the server, client UI, or
neutral pose model.

## Important limitations of this first cut

- There is no authentication, encryption, reconnection policy, or room model.
- The server trusts client camera input other than finite-value normalization,
  sequence rejection, and a +/-100 position clamp.
- The SimEthereal zone grid is deliberately large relative to this test scene so
  all twelve possible participants remain mutually visible. Zones are an
  internal SimEthereal requirement, not an application-level feature here.
- Network smoothing is intentionally simple. It is a visual comparison tool,
  not a production latency-compensation implementation.
- The generation environment could not execute a real Maven dependency build.
  See `VALIDATION.md` for what was and was not validated before packaging.
