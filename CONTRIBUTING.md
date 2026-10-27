# Contributing

Thank you for considering a contribution to Interval Controller for Apache JMeter.

## Ways to contribute

- Bug reports with reproducible JMX and JMeter / Java versions
- Documentation improvements
- Tests that lock down scheduling, retry, jitter, or token behavior
- Features proposed for a **future** version (not silent changes to a released JAR)

## Development setup

1. JDK 8+ (bytecode target remains 8 / major version 52)
2. Apache Maven 3.6+
3. Apache JMeter 5.6.3 for manual GUI / Non-GUI checks

```bash
mvn -B -s .mvn-settings.xml clean verify
```

## Coding guidelines

- Do not change JMX property keys without a versioned migration plan.
- Preserve Java 8 source/bytecode compatibility.
- Prefer unit tests for decision logic; keep GUI tests deterministic.
- Document behavior changes in `CHANGELOG.md`.

## Pull requests

1. Open an issue describing the problem or proposal when practical.
2. Keep PRs focused.
3. Include tests for behavioral changes.
4. Do not replace the published canonical JAR of an already-released version.

## Code of conduct

Be respectful and constructive. Harassment or personal attacks are not acceptable.
