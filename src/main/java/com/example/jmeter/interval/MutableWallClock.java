package com.example.jmeter.interval;

/** Controllable wall clock for tests. */
public final class MutableWallClock implements WallClock {
    private long millis;

    public MutableWallClock(long initialMillis) {
        this.millis = initialMillis;
    }

    @Override
    public long currentTimeMillis() {
        return millis;
    }

    public void setMillis(long millis) {
        this.millis = millis;
    }

    public void advanceMillis(long delta) {
        this.millis += delta;
    }
}
