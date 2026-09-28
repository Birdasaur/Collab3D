# Motion Synchronization Pattern

## 1. Why this document exists

Motion synchronization is the most timing-sensitive reusable pattern in the prototype. It is not a single interpolation class. It is a pipeline spanning:

- local input and simulation,
- high-rate pose publication,
- a server-owned publication timeline,
- SimEthereal state replication,
- remote arrival history,
- client/server clock synchronization,
- adaptive interpolation delay,
- bounded extrapolation,
- reconciliation,
- JavaFX presentation.

Removing one layer because another appears to “already smooth things” changes the behavior of the whole system.

---

## 2. End-to-end pipeline

```text
LOCAL CLIENT

input state
    |
120 Hz local motion simulation
    |
latest local Pose3d
    +-------------------------------> immediate local rendering
    |
120 Hz pose publication
    |
PoseInputMessage (unreliable)
    |

SERVER

latest accepted client pose
    |
120 Hz authoritative publication frame
    +--------------------+
    |                    |
rewind history       SimEthereal state
    |                    |
world snapshots          |
                         |
                         v
REMOTE CLIENT

SimEtherealClientAdapter
    |
RemotePoseStore
    |
RemotePoseHistory
    |
adaptive render delay
    |
renderTime = estimatedServerNow - delay
    |
interpolate if bracketed
    |
bounded extrapolate if slightly ahead
    |
hold if prediction bound exceeded
    |
PoseReconciler
    |
neutral render Pose3d
    |
JavaFX RemoteParticipantView
```

---

## 3. Local motion simulation

### 3.1 Separate simulation from JavaFX pulse

`LocalPosePublisher` owns a dedicated scheduled thread (`local-camera-motion`) rather than using JavaFX `AnimationTimer` as the authoritative local motion clock.

Current default:

```text
CLIENT_MOTION_HZ = 120
```

The reason is consistency. JavaFX pulse timing is a presentation concern and can vary with rendering load, window state, or display refresh behavior.

Input can originate from JavaFX, but the simulation loop should not depend on JavaFX frame cadence.

### 3.2 Thread-safe input boundary

`CameraInputState` contains atomic input state and no JavaFX scene-graph nodes.

The JavaFX event thread records key/mouse intent. The motion thread reads that intent.

This avoids transferring mutable JavaFX objects into the simulation thread.

### 3.3 Neutral pose representation

`CameraMotionModel` outputs `Pose3d`, which is renderer-neutral.

The local JavaFX camera is an adapter/consumer of that pose; it is not the networking state model itself.

### 3.4 Current demo coordinate convention

The demo motion model uses:

- `+Z` as forward,
- `+X` as right,
- JavaFX-style `-Y` as up.

This is demo-specific. Preserve the neutral API but adapt coordinate conversion for another engine as required.

---

## 4. Pose publication

`LocalPosePublisher` maintains local simulation and network publication as conceptually distinct rates, even though both are currently 120 Hz.

```text
CLIENT_MOTION_HZ = 120
CLIENT_POSE_HZ   = 120
```

The publisher uses a phase-accumulator style decision rather than assuming one network send per simulation iteration forever.

This separation matters if a future application wants, for example:

```text
simulation = 240 Hz
pose publication = 120 Hz
```

The current implementation requires publication rate not to exceed motion simulation rate.

### Delivery policy

Pose messages are unreliable:

```java
message.setReliable(false);
```

Newer pose samples supersede older ones.

---

## 5. Pose input is not the remote render timeline

The server receives a client pose with a client-local sample timestamp. It does **not** simply forward that timestamp as the shared remote rendering timestamp.

Instead, on each server publication frame:

```text
serverFrameTime = System.nanoTime()
```

and the participant pose is:

- recorded in server rewind at that frame time,
- published through SimEthereal under that frame time,
- included in the current authoritative world snapshot.

This creates a common server/shared timeline for remote clients.

---

## 6. Why clock synchronization is necessary

A remote client needs to render something like:

```text
serverNow - interpolationDelay
```

But its local `System.nanoTime()` is not numerically the same clock as the server's `System.nanoTime()`.

`ClockSynchronizer` estimates a mapping.

### 6.1 NTP-like sample

```text
Client                                        Server
  t0 -- request -------------------------------> t1
                                                t2
  t3 <------------------------------- response --
```

Computed values:

```text
RTT = (t3 - t0) - (t2 - t1)

offset = ((t1 - t0) + (t2 - t3)) / 2
```

Offset means approximately:

```text
serverTime ~= clientTime + offset
```

### 6.2 Robust sample selection

Current constants:

- rolling sample window: 32,
- best low-RTT samples considered: 8,
- maximum pending requests: 8,
- sync request interval: 1 second.

The synchronizer sorts by RTT, selects the lowest-RTT subset, then uses the median offset from that subset.

This reduces sensitivity to transient queueing delay.

### 6.3 Monotonic time only

The implementation uses monotonic nanosecond clocks for durations and mappings.

Do not replace this with wall-clock time. Wall-clock corrections are unrelated to simulation synchronization and can jump.

---

## 7. SimEthereal client timestamping

`SimEtherealClientAdapter.beginFrame(long time)` receives the shared/server frame time for an incoming state frame.

Each `objectUpdated(...)` converts SimMath state into a neutral `Pose3d` stamped with that frame time.

Therefore remote pose history is ordered in the server/shared timeline rather than by local packet-arrival time.

The adapter records local arrival time separately in `RemotePoseStore` for jitter estimation.

These are different measurements:

```text
sampleTime  = when state belongs on server/shared timeline
arrivalTime = when this client received/processed the update locally
```

Both are useful.

---

## 8. RemotePoseHistory

Each remote participant owns a bounded ordered history.

Current history horizon:

```text
REMOTE_HISTORY_NANOS = 500 ms
```

Responsibilities:

- retain recent server-timestamped poses,
- reject stale/out-of-order samples,
- replace equal-timestamp samples,
- find samples bracketing a desired render time,
- retain enough samples for interpolation/prediction.

The history intentionally keeps at least two useful samples while pruning by time.

---

## 9. Adaptive interpolation delay

A fixed interpolation buffer works, but network jitter changes over time. `RemoteInterpolationController` adjusts the delay per participant.

Current bounds:

| Parameter | Value |
|---|---:|
| Minimum delay | 8 ms |
| Baseline/default | 12 ms |
| Maximum delay | 30 ms |
| Jitter EWMA alpha | 1/16 |
| Jitter multiplier | 2.0 |
| Single variation sample cap | 100 ms |
| Delay increase alpha | 0.35 |
| Delay decrease alpha | 0.02 |

### 9.1 Jitter measurement without knowing absolute clock offset

For two consecutive remote updates, compare:

```text
serverSampleDelta = sampleTime[n] - sampleTime[n-1]
localArrivalDelta = arrivalTime[n] - arrivalTime[n-1]
variation = abs(localArrivalDelta - serverSampleDelta)
```

Absolute client/server clock offset cancels out because this compares deltas.

An EWMA estimates jitter.

### 9.2 Target delay

Conceptually:

```text
targetDelay = baseline + jitterMultiplier * estimatedJitter
```

clamped between the configured min and max.

### 9.3 Fast attack, slow release

The delay increases relatively quickly when jitter rises and decreases slowly when conditions improve.

This hysteresis avoids a buffer that oscillates aggressively around the latest samples.

---

## 10. Choosing the remote render time

When clock synchronization is valid:

```text
estimatedServerNow = ClockSynchronizer.toServerTime(localNow)
renderTime = estimatedServerNow - adaptiveInterpolationDelay
```

The client then asks the remote participant motion state to resolve a pose at `renderTime`.

### Unsynchronized fallback

The current `ClientMain` only enables server-time buffered rendering when both are true:

- buffered remote rendering is enabled,
- the clock synchronizer has a valid estimate.

Before synchronization, it does not pretend local nanoseconds are server nanoseconds.

---

## 11. Interpolation first

If history contains two samples bracketing the requested render time:

```text
P0.time <= renderTime <= P1.time
```

then `Pose3d.interpolate(...)` computes:

- linear position interpolation,
- quaternion spherical interpolation (slerp) for orientation.

This is the preferred path because both endpoints are known authoritative replicated samples.

The interpolation factor is continuous, so rendering is not restricted to discrete 120 Hz server frames.

This is one meaning of **sub-frame timing** in the architecture: display time can fall between server update frames and produce a continuous pose.

---

## 12. Bounded extrapolation

If `renderTime` is slightly newer than the latest available sample, waiting for another packet can produce a visible hitch.

`RemotePosePredictor` therefore extrapolates for a short bounded horizon.

Current maximum:

```text
MAX_PREDICTION_NANOS = 25 ms
```

It uses the last two samples to estimate:

- constant linear velocity,
- rotational delta, extended through quaternion delta power.

### Why bounded

Prediction error grows with time and maneuvering.

The architecture deliberately prefers:

```text
interpolate -> short extrapolation -> hold
```

over unrestricted dead reckoning.

When the 25 ms prediction budget is exhausted, the client holds the latest resolved state instead of continuing to invent motion.

---

## 13. Reconciliation

When authoritative data resumes after extrapolation, immediately snapping to the corrected pose can be visually harsh.

`PoseReconciler` smooths small corrections.

Current tuning:

- correction half-life: 8 ms,
- snap if position error >= 5 world units,
- snap if angular error >= 45 degrees.

Small errors converge quickly. Large errors snap because smoothing a very wrong state can be more misleading than correcting it immediately.

### What reconciliation is not

This remote reconciler is a presentation correction layer. It is not the classic local-player input replay algorithm used by fully server-simulated movement systems.

The current prototype renders its own local motion immediately and does not implement a server-simulated locomotion command/replay loop.

---

## 14. Local player vs remote player prediction

These are different problems.

### Local player

Current prototype:

```text
local input -> local simulation -> immediate local render
```

There is no reason to wait for a server round trip to move the local camera.

If a future application makes movement fully server authoritative, then it may add the classic:

```text
input command sequence
    -> local prediction
    -> server simulation
    -> authoritative acknowledgement
    -> replay unacknowledged inputs
```

That is **not** what `RemotePosePredictor` does.

### Remote players

For remote participants, there is no local input stream to replay. The client instead uses:

```text
history + interpolation + bounded extrapolation + reconciliation
```

Do not merge these two prediction concepts into one class merely because both involve the word “prediction.”

---

## 15. Server rewind is related but separate

Remote rendering history and server rewind solve different timing problems.

### Client history

Purpose:

> render another participant smoothly in the present display frame.

Characteristics:

- client-side,
- 500 ms current history,
- allows bounded forward prediction for presentation.

### Server rewind

Purpose:

> reconstruct what authoritative participants looked like at a synchronized historical event time for validation/collision.

Characteristics:

- server-side,
- 1 second current history,
- exact/interpolated historical queries,
- **no extrapolation**.

Do not reuse client prediction rules in authoritative server rewind.

---

## 16. Time-aligned interactions

Clock synchronization is also used when the local participant creates an interaction.

The client estimates:

```text
eventServerTime = clock.toServerTime(System.nanoTime())
```

and sends that in `InteractionIntentMessage`.

The server can then query rewind at the requested historical event time, within bounded tolerance.

Thus the same synchronized timeline connects:

- remote presentation,
- interaction intent time,
- authoritative rewind,
- collision impact time,
- deterministic tracer presentation.

---

## 17. Collision sub-step time

The authoritative interaction loop itself is fixed-step at 120 Hz, but a collision may occur partway through a simulation segment.

The collision service returns the earliest hit fraction along the swept segment. The handler can convert that fraction into an impact time within the fixed step.

Therefore authoritative event timing is not limited to saying “the collision happened exactly at the end of tick N.”

This improves temporal fidelity while preserving a deterministic fixed-step simulation structure.

---

## 18. Rendering boundary

`RemoteParticipantView` intentionally performs no network interpolation or simulation.

It receives a final neutral pose and applies it to JavaFX through `FxPoseCodec`.

Correct ownership:

```text
network/timing layer -> final Pose3d -> JavaFX view
```

Incorrect ownership:

```text
JavaFX node -> inspect network packets -> invent smoothing -> mutate simulation
```

Keeping smoothing outside the scene graph allows the same networking model to be ported to a different renderer.

---

## 19. Failure behavior and graceful degradation

### Missing clock estimate

Do not compare local and server nanoseconds directly. Use direct/latest rendering until synchronization is valid.

### Sparse history

If insufficient samples exist to interpolate, use the best available pose rather than fabricating a bracket.

### Short packet gap

Use bounded extrapolation up to the configured maximum.

### Long packet gap

Hold rather than continue unbounded prediction.

### Large correction

Snap according to configured thresholds.

### Out-of-order state

Do not let older server samples move history backward.

---

## 20. Diagnostics worth retaining

`NetworkRuntimeStats` / `NetworkStatsLogger` and remote motion diagnostics provide important evidence when tuning:

- sent/received pose rates,
- server state frame rate,
- accepted/rejected pose sequences,
- remote render mode,
- interpolation delay,
- estimated jitter,
- history/sample counts,
- clock RTT/offset status.

Do not tune interpolation by visual impression alone if these measurements are available.

---

## 21. Tuning guidance

### Lower interpolation delay

Pros:

- less apparent remote latency.

Cons:

- more frequent need for extrapolation,
- more sensitivity to jitter.

### Higher interpolation delay

Pros:

- more likely to have a valid bracket,
- smoother under jitter.

Cons:

- remote participants appear farther behind server now.

### Longer prediction horizon

Pros:

- fewer immediate holds during packet gaps.

Cons:

- larger possible prediction error.

### Stronger reconciliation smoothing

Pros:

- softer corrections.

Cons:

- can leave the visual pose wrong longer.

Tune these as a group rather than independently.

---

## 22. Invariants for AI agents and maintainers

Do not simplify away these properties without deliberately changing the architecture:

1. Local simulation and network pose publication are distinct concepts.
2. JavaFX pulse is not the networking/simulation clock.
3. Client and server monotonic clocks are separate domains.
4. Remote state should be timestamped on the server/shared timeline.
5. Arrival time is useful for jitter but is not the sample's authoritative time.
6. Remote rendering is interpolation-first.
7. Prediction is bounded.
8. Large reconciliation errors snap.
9. Server rewind does not extrapolate.
10. The same server publication frame should feed replication, rewind, and current world snapshot.
11. Do not lower 120 Hz merely to silence an external SimEthereal watchdog warning.

---

## 23. Reusable pattern summary

```text
                         CLOCK SYNC
client monotonic <------------------------------> server monotonic
      |                                                |
      |                                                |
LOCAL PLAYER                                   SERVER FRAME @ 120 Hz
input                                               |
  |                                                 +--> rewind
simulation @120                                     +--> world snapshot
  |                                                 +--> SimEthereal
  +--> local render                                      |
  |                                                      |
  +--> PoseInput @120 -----------------------------------+
                                                         |
                                                   remote state
                                                         |
                                               REMOTE CLIENT
                                                   history
                                                      |
                                              adaptive delay
                                                      |
                                              interpolate first
                                                      |
                                            bounded extrapolation
                                                      |
                                                reconciliation
                                                      |
                                                   rendering
```

That complete pipeline—not any one smoothing formula—is the reusable motion synchronization pattern.
