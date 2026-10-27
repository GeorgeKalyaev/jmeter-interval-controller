package com.example.jmeter.interval;

/**
 * Monotonic time source. Production code uses {@link SystemNanoClock};
 * tests inject a controllable clock — never {@code Thread.sleep()}.
 */
public interface NanoClock {
    long nanoTime();
}
