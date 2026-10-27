package com.example.jmeter.interval;

public final class SystemWallClock implements WallClock {
    public static final SystemWallClock INSTANCE = new SystemWallClock();

    private SystemWallClock() {
    }

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
