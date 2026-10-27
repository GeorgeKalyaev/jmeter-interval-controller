# Interval Controller v1.1.2 — Stable Release

**Title:** Interval Controller v1.1.2 — Stable Release

## Summary

Interval Controller for Apache JMeter executes nested elements at most once per configured interval per thread, only when the thread encounters the controller. SKIP does not sleep or block sibling samplers.

## Features

- Interval-based EXECUTE / SKIP (Variant A — no background wake-up)
- Execute on first iteration
- Interval from Execution Start or Execution Completion
- Retry on Failure, Retry Delay, Maximum Retries (`0` = unlimited when retry is enabled)
- Random Jitter (Positive / Symmetric)
- Dynamic Interval expressions
- Token expiration helpers (Unix ms / Unix s / TTL seconds), Refresh Before Expiration, Force Refresh
- GUI configuration and JMX persistence
- Nested controller compatibility improvements (Once Only / Loop)

## Installation

1. Download `jmeter-interval-controller-1.1.2.jar` from this release.
2. Copy to `apache-jmeter-5.6.3/lib/ext/`.
3. Remove any older `jmeter-interval-controller-*.jar`.
4. Restart JMeter.
5. **Add → Logic Controller → Interval Controller**.

## Compatibility

| Component | Status |
|-----------|--------|
| Apache JMeter 5.6.3 | Verified |
| Java 8+ (bytecode major 52) | Verified |
| Windows GUI / Non-GUI | Verified |
| Linux Non-GUI (Docker) | Verified |
| Linux GUI | Not tested |

## Artifact integrity

| File | SHA-256 |
|------|---------|
| `jmeter-interval-controller-1.1.2.jar` | `94F834F68B6825240C6179D15352BF693583B444B1C3F7D441CD190E5EDF591C` |

## Documentation

After publication, see the repository README and `docs/`:

- `docs/CONFIGURATION.md`
- `docs/EXAMPLES.md` — including **Run Authentication Once Every 10 Minutes**
- `examples/*.jmx`

Documentation URL:

https://github.com/GeorgeKalyaev/jmeter-interval-controller

## Upgrade notes

- Remove older plugin JARs before installing 1.1.2.
- JMX from 1.0.0 / 1.1.x loads with defaults for newer fields.
- Identify builds by SHA-256, not version string alone.
- Do not replace this release asset with a local rebuild when distributing 1.1.2.

## Known limitations

- The controller does not wake threads on a timer; it must be encountered.
- Retry behavior is subject to Thread Group On Sample Error policy.
- Token TTL uses wall clock; unbound TTL does not force perpetual refresh.
- Live GitHub Actions status applies only after the public workflow has been run.

## Planned download URL (after the GitHub Release is published)

The following URL is the **planned** direct asset location. It does **not** exist until Release `v1.1.2` is created and the canonical JAR is attached:

```
https://github.com/GeorgeKalyaev/jmeter-interval-controller/releases/download/v1.1.2/jmeter-interval-controller-1.1.2.jar
```
