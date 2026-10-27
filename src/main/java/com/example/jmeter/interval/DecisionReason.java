package com.example.jmeter.interval;

/** Why the controller chose EXECUTE or SKIP (DEBUG diagnostics). */
public enum DecisionReason {
    FIRST_ITERATION,
    INTERVAL_ELAPSED,
    FORCE,
    RETRY,
    EXPIRED,
    SKIP_INTERVAL,
    SKIP_FIRST_WAIT,
    SKIP_INVALID_INTERVAL,
    SKIP_MAX_RETRIES_COOLDOWN
}
