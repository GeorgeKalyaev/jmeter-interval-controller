# Quick Start

## Install

1. Place `jmeter-interval-controller-1.1.2.jar` in `apache-jmeter-5.6.3/lib/ext/`.
2. Remove older `jmeter-interval-controller-*.jar` files.
3. Restart JMeter.
4. Confirm **Add → Logic Controller → Interval Controller**.

Verify SHA-256:

`94F834F68B6825240C6179D15352BF693583B444B1C3F7D441CD190E5EDF591C`

## Minimal plan

```
Test Plan
  Thread Group          (e.g. 1 thread, Loop Forever or N loops)
    Interval Controller (10 Minutes, Execute on first, Completion)
      JSR223 Sampler — Refresh
    JSR223 Sampler — Work
```

Recommended Interval Controller defaults for this scenario:

- Execution Interval: `10`
- Unit: Minutes
- Execute on first iteration: enabled
- Interval starts from: Execution Completion
- Retry on Failure: disabled
- Maximum Retries: `0` (unlimited if retry were enabled; inactive while retry is off)
- Random Jitter: `0`
- Dynamic Interval: disabled
- Token fields: empty / defaults

## What you should observe

| Encounter | Nested “Refresh” | Sibling “Work” |
|-----------|------------------|----------------|
| First | Runs | Runs |
| Before interval elapses | Skipped | Runs |
| After interval | Runs again | Runs |

## Next steps

- Full field reference: [CONFIGURATION.md](CONFIGURATION.md)
- Authentication walkthrough: [EXAMPLES.md](EXAMPLES.md)
- Import `examples/02-authentication-refresh.jmx`
