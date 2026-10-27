# Examples

All JMX examples under `examples/` are **demonstrations**. They use JSR223 (Groovy) samplers and simulated tokens. They do **not** call real authentication servers and contain **no** real passwords.

---

## Run Authentication Once Every 10 Minutes

**Goal:** Obtain / refresh an access token infrequently, while API traffic continues every Thread Group iteration.

### Recommended tree

```
Thread Group
  Interval Controller — Refresh Authentication
    HTTP Request — POST /login          (or JSR223 simulation)
      JSON Extractor — access_token     (or vars.put in JSR223)
  HTTP Request — GET /api/orders
  HTTP Request — GET /api/profile
```

Importable demo: [`examples/02-authentication-refresh.jmx`](../examples/02-authentication-refresh.jmx)

### Recommended Interval Controller settings

| Setting | Value |
|---------|-------|
| Execution Interval | `10` |
| Unit | Minutes |
| Execute on first iteration | Enabled |
| Interval starts from | Execution Completion |
| Retry on Failure | Disabled |
| Maximum Retries | `0` |
| Random Jitter | `0` |
| Dynamic Interval | Disabled |
| Token Expiration Variable | Empty |
| Token Issued-At Variable | Empty |
| Force Refresh Variable | Default (`interval_controller_force_refresh`) — unused unless set |

Other defaults (Refresh Before, Expiration Format, etc.) do not change behavior while token variables are empty.

### Execution sequence

**First encounter**

1. Interval Controller decides EXECUTE (first iteration).
2. Authentication runs; `access_token` is stored in JMeter variables.
3. Sibling API requests run using the token.

**Before the next interval**

1. Interval Controller decides SKIP.
2. Authentication does **not** run.
3. Sibling API requests still run (token reused).

**After the interval, when the controller is encountered again**

1. Interval Controller decides EXECUTE.
2. Authentication runs again; token is refreshed.
3. Sibling API requests continue.

### Timeline (one thread, Completion mode)

```
t=0          Encounter #1 → AUTH execute → token T0 → API…
t=0+ε … 10m  Encounter → AUTH skip → API… (token T0)
t≈10m+       Encounter → AUTH execute → token T1 → API…
```

Exact wall times depend on Completion anchoring and how long authentication takes. The controller only evaluates when the thread reaches it.

### Beginner notes

| Topic | Explanation |
|-------|-------------|
| Per-thread state | Each JMeter thread keeps its own last-execution anchor. With multiple threads, each refreshes on its own cadence. |
| Token storage | Tokens live in JMeter Variables (`vars`). They are not written by Interval Controller unless you add extractors / scripts. |
| Token expiration risk | A fixed 10-minute interval is **wrong** if the token expires sooner (e.g. 5 minutes). Prefer a shorter interval, or configure Token Expiration / TTL settings. |
| Must be encountered | Interval Controller never wakes the thread by itself. If the Thread Group is paused or the controller is disabled, refresh will not occur. |
| Not a Timer | Siblings are not delayed by SKIP. |

### Demo timing in the shipped JMX

`02-authentication-refresh.jmx` uses a **short** interval (seconds) so Non-GUI demos finish quickly, while documenting the production recommendation of **10 Minutes**.

---

## Other examples

| File | Demonstrates |
|------|----------------|
| `01-basic-interval-10-minutes.jmx` | Basic EXECUTE/SKIP with a short demo interval |
| `03-retry-on-failure.jmx` | Retry on Failure + Retry Delay |
| `04-jitter-and-dynamic-interval.jmx` | Jitter and Dynamic Interval |
| `05-token-expiration-and-force-refresh.jmx` | TTL seconds and Force Refresh variable |

See each JMX’s Test Plan comments for expected sample counts in the bundled Non-GUI parameters.
