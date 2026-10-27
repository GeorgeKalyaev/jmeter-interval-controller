package com.example.jmeter.interval;

/**
 * Where the next-allowed window is anchored.
 */
public enum IntervalStartMode {
    /**
     * Next execution allowed at {@code executionStart + interval}.
     */
    EXECUTION_START("Execution Start"),

    /**
     * Next execution allowed at {@code executionCompletion + interval}.
     */
    EXECUTION_COMPLETION("Execution Completion");

    private final String label;

    IntervalStartMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static IntervalStartMode fromLabel(String label) {
        if (label == null || label.trim().isEmpty()) {
            return EXECUTION_COMPLETION;
        }
        for (IntervalStartMode mode : values()) {
            if (mode.label.equalsIgnoreCase(label.trim()) || mode.name().equalsIgnoreCase(label.trim())) {
                return mode;
            }
        }
        return EXECUTION_COMPLETION;
    }
}
