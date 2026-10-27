package com.example.jmeter.interval;

/**
 * Default clock backed by {@link System#nanoTime()}.
 */
public final class SystemNanoClock implements NanoClock {
    public static final SystemNanoClock INSTANCE = new SystemNanoClock();

    private SystemNanoClock() {
    }

    @Override
    public long nanoTime() {
        return System.nanoTime();
    }
}
