# Configuration Reference

Interval Controller for Apache JMeter **v1.1.2**

Defaults are taken from `IntervalController` constructor / GUI `clearGui()` and property getters. Do not assume GUI labels imply different runtime defaults.

---

## Basic Settings

### Name

| | |
|--|--|
| **GUI label** | Name (standard JMeter title panel) |
| **Purpose** | Display name in the test plan tree |
| **Default** | `Interval Controller` |
| **Allowed** | Any string |
| **Notes** | Cosmetic; does not affect scheduling |

### Comments

| | |
|--|--|
| **GUI label** | Comments (standard JMeter title panel) |
| **Purpose** | Free-form documentation on the element |
| **Default** | Empty |
| **Allowed** | Any string |

### Execution Interval

| | |
|--|--|
| **GUI label** | Execution Interval: |
| **Purpose** | Base interval length between allowed EXECUTEs (per thread) |
| **Default** | `10` |
| **Allowed** | Non-negative number, or `${variable}` / functions when Dynamic Interval is enabled |
| **Activation** | Always used when resolving the effective interval |
| **Interaction** | Multiplied by **Time unit**. With Dynamic Interval, the expression is expanded via JMeter on each entry. Invalid / negative resolved values are rejected (interval must be ≥ 0). |
| **Example** | `10` with unit Minutes → 10 minutes |
| **Caveats** | Static `${var}` without Dynamic Interval is **not** expanded |

### Time unit

| | |
|--|--|
| **GUI label** | (combo next to Execution Interval) |
| **Purpose** | Unit for Execution Interval |
| **Default** | `Minutes` |
| **Allowed** | Milliseconds, Seconds, Minutes, Hours |
| **Example** | Interval `30`, unit Seconds → 30 seconds |

### Execute on first iteration

| | |
|--|--|
| **GUI label** | Execute on first iteration |
| **Purpose** | Whether the first encounter executes immediately |
| **Default** | Enabled (`true`) |
| **Allowed** | Boolean |
| **When disabled** | Early encounters SKIP until the interval elapses from first sight (`SKIP_FIRST_WAIT`) |
| **Example** | Uncheck to wait a full interval before the first auth refresh |

### Interval starts from — Execution Start

| | |
|--|--|
| **GUI label** | Execution Start |
| **Purpose** | Anchor the next allowed window at **execution start** + interval (+ jitter) |
| **Default** | Not selected (Completion is default) |
| **Allowed** | Mutually exclusive with Execution Completion |
| **Interaction** | On EXECUTE (non-retry), the start anchor is recorded immediately |
| **Caveats** | Long nested work can overlap the next window differently than Completion mode |

### Interval starts from — Execution Completion

| | |
|--|--|
| **GUI label** | Execution Completion |
| **Purpose** | Anchor the next allowed window at **execution completion** + interval (+ jitter) |
| **Default** | Selected |
| **Allowed** | Mutually exclusive with Execution Start |
| **Example** | Auth that takes 2s with a 10-minute Completion interval → next EXECUTE ~10 minutes after auth finishes |

---

## Retry Settings

### Retry on Failure

| | |
|--|--|
| **GUI label** | Retry on Failure |
| **Purpose** | After a nested sample failure, allow re-entry after Retry Delay instead of the full Execution Interval |
| **Default** | Disabled (`false`) |
| **Allowed** | Boolean |
| **Activation** | Only when enabled; uses SampleListener failure feedback from nested samples |
| **Caveats** | Thread Group “On Sample Error” = Stop Thread/Test can prevent retries. Success clears consecutive failure state. |

### Retry Delay

| | |
|--|--|
| **GUI label** | Retry Delay: |
| **Purpose** | Minimum wait before a retry EXECUTE is allowed |
| **Default** | `5` (unit Seconds) |
| **Allowed** | Non-negative number / expression |
| **Activation** | **Effectively inactive when Retry on Failure is disabled** — retry scheduling is not entered |
| **Important** | Does **not** sleep the thread; SKIP/retry readiness is evaluated on subsequent encounters |
| **Example** | Delay `2` Seconds → after failure, next encounter within <2s still skips until ready |

### Maximum Retries

| | |
|--|--|
| **GUI label** | Maximum Retries: |
| **Purpose** | Cap consecutive failure-driven retries |
| **Default** | `0` |
| **Allowed** | Integer ≥ 0 |
| **Semantics (source)** | Exhaustion when `max > 0 && consecutiveFailures > max`. Therefore **`0` means unlimited** additional retry attempts (no exhaustion via this counter). |
| **When exhausted** | Falls back to the normal interval window |
| **Caveats** | GUI tooltip: “0 = unlimited retries.” This is **not** “zero retries.” |

---

## Advanced Settings

### Random Jitter

| | |
|--|--|
| **GUI label** | Random Jitter: |
| **Purpose** | Random offset applied once per new interval window (per thread) |
| **Default** | `0` (unit Seconds) |
| **Allowed** | Non-negative number / expression |
| **Activation** | When resolved max jitter is 0, **no random offset** is applied |
| **Interaction** | Combined with Jitter Mode; wait is clamped so remaining wait ≥ 0 |
| **Caveats** | Jitter Mode is irrelevant when Random Jitter resolves to 0 |

### Jitter Mode

| | |
|--|--|
| **GUI label** | Jitter Mode: |
| **Purpose** | Distribution of the random offset |
| **Default** | `Positive (0..+N)` |
| **Allowed** | Positive (0..+N); Symmetric (−N..+N) |
| **Activation** | Only meaningful when Random Jitter > 0 |
| **Example** | Positive with N=5s → add 0..5 seconds to the wait |

### Dynamic Interval

| | |
|--|--|
| **GUI label** | Dynamic Interval (resolve ${vars} / functions) |
| **Purpose** | Expand Execution Interval through JMeter variables/functions on each entry |
| **Default** | Disabled (`false`) |
| **When enabled** | Changes **how** the interval string is resolved (CompoundVariable expansion) |
| **When disabled** | Numeric literal expected; `${var}` is not expanded |
| **Example** | Interval `${authIntervalMinutes}` with Dynamic Interval enabled |

### Token Expiration Variable

| | |
|--|--|
| **GUI label** | Token Expiration Variable: |
| **Purpose** | Optional JMeter variable holding expiry / TTL input |
| **Default** | Empty (disabled) |
| **Activation** | **Inactive when empty** — token refresh logic does not force EXECUTE |
| **Formats** | See Expiration Format |
| **Caveats** | Uses **wall clock**, not nanoTime. Non-numeric / negative values are ignored (logged at warn) |

### Token Issued-At Variable

| | |
|--|--|
| **GUI label** | Token Issued-At Variable: |
| **Purpose** | Wall-clock Unix **milliseconds** when a TTL token was issued |
| **Default** | Empty → companion name `<TokenExpirationVar>__issued_at_ms` when TTL is used |
| **Activation** | Relevant for **TTL seconds** format |
| **Usage** | `expiresAt = issuedAt + ttlSeconds`; refresh when `now >= expiresAt - refreshBefore` |
| **Caveats** | Unbound TTL (no issued-at memory/variable) does **not** force perpetual refresh |

### Expiration Format

| | |
|--|--|
| **GUI label** | Expiration Format: |
| **Purpose** | How Token Expiration Variable is interpreted |
| **Default** | `Unix milliseconds` |
| **Allowed** | Unix milliseconds; Unix seconds; TTL seconds |
| **Interpretation** | **Unix milliseconds**: absolute expiry epoch ms. **Unix seconds**: absolute expiry epoch seconds (converted to ms). **TTL seconds**: lifetime seconds from issued-at. |
| **Example** | TTL `3600` + issued-at → one-hour lifetime |

### Refresh Before Expiration

| | |
|--|--|
| **GUI label** | Refresh Before Expiration: |
| **Purpose** | Allow EXECUTE when within this window before expiry |
| **Default** | `30` (unit Seconds) |
| **Allowed** | Non-negative number / expression + unit |
| **Activation** | Only when token expiration variable is set and has a usable value |
| **Example** | Refresh 30 seconds before absolute expiry |

### Force Refresh Variable

| | |
|--|--|
| **GUI label** | Force Refresh Variable: |
| **Purpose** | Force next encounter to EXECUTE regardless of interval |
| **Default** | `interval_controller_force_refresh` |
| **Truthy values** | `true`, `1`, `yes` (case-insensitive for true/yes) |
| **Scoped override** | `<name>__<instanceId>` is also checked |
| **Consumption** | On FORCE EXECUTE, the variable (and scoped override) are **removed** (`clearForceRefreshVariable`) |
| **Example** | Beanshell/JSR223: `vars.put("interval_controller_force_refresh","true");` |

---

## Cross-cutting interactions

| Topic | Behavior |
|-------|----------|
| Retry vs next interval | Successful completion clears retry pending; exhausted max retries returns to normal interval anchoring |
| Retry attempts vs interval anchor | Retry EXECUTE uses retry delay readiness; Completion/Start anchoring for normal windows follows Interval Start Mode (retries are special-cased and do not re-apply start-mode anchor the same way as a fresh window) |
| Token vs interval | Token-due or Force can EXECUTE even if the interval window would otherwise SKIP |
| Per-thread state | Anchors, retry counters, TTL binding are per thread clone |

## Related documentation

- [EXAMPLES.md](EXAMPLES.md) — authentication every 10 minutes
- [ARCHITECTURE.md](ARCHITECTURE.md) — decision flow
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
