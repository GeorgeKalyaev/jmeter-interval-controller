# Examples

Self-contained JMeter 5.6.3 plans for Interval Controller v1.1.2.

| File | Demo focus |
|------|------------|
| `01-basic-interval-10-minutes.jmx` | Basic EXECUTE/SKIP (short demo interval; production recommendation is 10 minutes) |
| `02-authentication-refresh.jmx` | Simulated auth once per interval; APIs every iteration |
| `03-retry-on-failure.jmx` | Retry on Failure |
| `04-jitter-and-dynamic-interval.jmx` | Dynamic Interval + jitter |
| `05-token-expiration-and-force-refresh.jmx` | TTL seconds + Force Refresh |

No real passwords, tokens, or production endpoints.

Walkthrough: [docs/EXAMPLES.md](../docs/EXAMPLES.md)
