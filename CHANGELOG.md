# Changelog

All notable changes to Interval Controller for Apache JMeter are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.2] — 2026-10-27

### Fixed

- Nested controller handling: Interval Controller no longer re-initializes Once Only Controllers on every parent iteration.
- Nested Loop Controllers reset their loop count on each Interval Controller EXECUTE.
- Nested controllers are retained in the child list after completion (compatible nesting under Interval Controller).

### Verified

- Apache JMeter 5.6.3 (Windows GUI and Non-GUI; Linux Non-GUI via Docker).
- Java 8 and Java 17 bytecode target 52.
- Canonical release JAR SHA-256:
  `94F834F68B6825240C6179D15352BF693583B444B1C3F7D441CD190E5EDF591C`

### Notes

- Maven coordinates remain `com.example.jmeter:jmeter-interval-controller:1.1.2` for artifact continuity with the verified JAR.
- No scheduling semantics, retry semantics, or token TTL contract changes versus 1.1.1 intent beyond nesting fixes above.

## [1.1.1] — prior

- Token issued-at / TTL_SECONDS binding refinements.
- Force Refresh scoped variable support.
- Documentation and verification updates.

## [1.1.0] — prior

- Retry on Failure, Retry Delay, Maximum Retries.
- Random Jitter and Jitter Mode.
- Dynamic Interval.
- Token expiration helpers and Force Refresh.

## [1.0.0] — prior

- Initial Interval Controller: interval-based EXECUTE/SKIP, first-iteration option, start/completion anchoring.
