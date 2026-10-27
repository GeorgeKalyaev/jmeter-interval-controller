package com.example.jmeter.interval;

/**
 * User-facing interval units. Converted to nanoseconds for comparisons.
 */
public enum TimeUnitOption {
    MILLISECONDS(1_000_000L, "Milliseconds"),
    SECONDS(1_000_000_000L, "Seconds"),
    MINUTES(60L * 1_000_000_000L, "Minutes"),
    HOURS(3600L * 1_000_000_000L, "Hours");

    private final long nanosPerUnit;
    private final String label;

    TimeUnitOption(long nanosPerUnit, String label) {
        this.nanosPerUnit = nanosPerUnit;
        this.label = label;
    }

    public long toNanos(long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Interval must be >= 0");
        }
        return amount * nanosPerUnit;
    }

    public String getLabel() {
        return label;
    }

    public static TimeUnitOption fromLabel(String label) {
        if (label == null || label.trim().isEmpty()) {
            return MINUTES;
        }
        for (TimeUnitOption unit : values()) {
            if (unit.label.equalsIgnoreCase(label.trim()) || unit.name().equalsIgnoreCase(label.trim())) {
                return unit;
            }
        }
        return MINUTES;
    }
}
