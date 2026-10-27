package com.example.jmeter.interval;

/**
 * Wall-clock (Unix epoch) time source for token expiration checks.
 * Kept separate from {@link NanoClock} — never compare the two directly.
 */
public interface WallClock {
    long currentTimeMillis();
}
