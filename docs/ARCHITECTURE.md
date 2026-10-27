# Architecture

## Role in JMeter

Interval Controller extends `GenericController` and participates in the normal thread execution path. It also implements:

- `LoopIterationListener` — iteration boundaries
- `SampleListener` — nested sample success/failure for retry

## Variant A scheduling

On each entry, the controller decides **EXECUTE** or **SKIP**:

```
encounter
   │
   ├─ Force Refresh truthy? ──────────────► EXECUTE (FORCE), clear variable
   ├─ Token refresh due? ─────────────────► EXECUTE (token)
   ├─ Retry pending & delay elapsed? ─────► EXECUTE (RETRY)
   ├─ First iteration rules ──────────────► EXECUTE or SKIP_FIRST_WAIT
   └─ Interval elapsed since anchor? ─────► EXECUTE or SKIP
```

SKIP returns control immediately. No `Thread.sleep`, no background scheduler, no timer thread.

## Anchoring

| Mode | Next allowed time |
|------|-------------------|
| Execution Start | startNano + interval (+ jitter) |
| Execution Completion | completionNano + interval (+ jitter) |

Jitter is applied once per new successful interval window (per thread). Symmetric jitter is clamped so remaining wait is never negative.

## Per-thread state

JMeter clones controllers per thread. Anchors, retry counters, and TTL bindings are **not** shared across threads unless you deliberately share via JMeter properties / external systems.

## Token helpers

Token logic uses **wall-clock** milliseconds. Interval nano clocks and wall clocks are not mixed for TTL math.

## Plugin discovery

Classes live in package `com.example.jmeter.interval`. JMeter discovers `IntervalControllerGui` from `lib/ext`. Marker / component classes for Plugins Manager:

- Marker: `com.example.jmeter.interval.IntervalControllerGui`
- Components: `IntervalControllerGui`, `IntervalController`
