package com.example.jmeter.interval;

/**
 * Controllable monotonic clock for unit tests (no real waits).
 */
public final class MutableNanoClock implements NanoClock {
    private long nanoTime;

    public MutableNanoClock(long initialNanoTime) {
        this.nanoTime = initialNanoTime;
    }

    @Override
    public long nanoTime() {
        return nanoTime;
    }

    public void setNanoTime(long nanoTime) {
        this.nanoTime = nanoTime;
    }

    public void advanceNanos(long delta) {
        this.nanoTime += delta;
    }

    public void advanceMillis(long millis) {
        advanceNanos(millis * 1_000_000L);
    }
}
