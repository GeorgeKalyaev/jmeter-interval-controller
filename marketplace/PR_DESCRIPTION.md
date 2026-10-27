# Pull Request: Add Interval Controller plugin (v1.1.2)

## Summary

This PR adds **Interval Controller** to the JMeter Plugins Manager catalogue (`site/dat/repo/various.json`).

Interval Controller is a Logic Controller for Apache JMeter that runs nested elements at most once per configured interval **per thread**, and only when the thread encounters the controller. When the interval has not elapsed, nested samplers are skipped immediately (no sleep). Sibling samplers outside the controller continue normally.

It is **not** a JMeter Timer and does not use a background wake-up scheduler.

## Main features

- Configurable execution interval (ms / s / min / h)
- Execute on first iteration
- Interval anchored at Execution Start or Execution Completion
- Optional Retry on Failure with delay and max retries
- Optional random jitter
- Optional dynamic interval expressions
- Optional token expiration / TTL helpers and Force Refresh
- GUI + JMX persistence

## Use cases

- Refresh authentication or tokens on a cadence while continuous API traffic continues
- Run expensive setup infrequently without blocking the Thread Group on SKIP

## Compatibility

- Apache JMeter **5.6.3** (verified)
- Java **8+** (bytecode major version 52)
- Windows GUI / Non-GUI verified; Linux Non-GUI verified (Docker)
- Linux GUI: not tested

## Artifact

- File: `jmeter-interval-controller-1.1.2.jar`
- SHA-256: `94F834F68B6825240C6179D15352BF693583B444B1C3F7D441CD190E5EDF591C`
- **Planned** download URL (valid only after the GitHub Release asset is published):
  `https://github.com/GeorgeKalyaev/jmeter-interval-controller/releases/download/v1.1.2/jmeter-interval-controller-1.1.2.jar`
- No extra `libs` map entries — runtime dependencies are provided by JMeter

## Documentation

- Help URL: https://github.com/GeorgeKalyaev/jmeter-interval-controller
- Screenshot (planned raw URL on tag `v1.1.2`):
  `https://raw.githubusercontent.com/GeorgeKalyaev/jmeter-interval-controller/v1.1.2/docs/images/01-interval-controller-gui.png`
- Configuration reference and examples included in the project repository

## Descriptor fields

| Field | Value |
|-------|-------|
| id | `interval-controller` |
| markerClass | `com.example.jmeter.interval.IntervalControllerGui` |
| componentClasses | `IntervalControllerGui`, `IntervalController` |
| vendor | `GeorgeKalyaev` |

## Relation to existing plugins

Catalogue entries that throttle or delay samples (timers, throughput shaping, etc.) solve different problems. Interval Controller is a **Logic Controller** that gates nested subtrees on encounter while allowing siblings to proceed. Overlap with a stock Timer is intentional only at a high level (“do something less often”); the execution model differs.

## Validation

- [ ] `python3 format_repo.py`
- [ ] `python3 format_repo.py --check`
- [ ] Direct download URL returns the JAR with the SHA-256 above
- [ ] Clean JMeter 5.6.3 install via Plugins Manager places the JAR in `lib/ext`

## Notes for reviewers

Submit this PR only after the GitHub Release `v1.1.2` asset is publicly downloadable.
