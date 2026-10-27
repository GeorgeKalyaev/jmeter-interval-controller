package com.example.jmeter.interval;

public enum JitterMode {
    POSITIVE("Positive (0..+N)"),
    SYMMETRIC("Symmetric (−N..+N)");

    private final String label;

    JitterMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static JitterMode fromLabel(String label) {
        if (label == null || label.trim().isEmpty()) {
            return POSITIVE;
        }
        for (JitterMode mode : values()) {
            if (mode.label.equalsIgnoreCase(label.trim()) || mode.name().equalsIgnoreCase(label.trim())) {
                return mode;
            }
        }
        return POSITIVE;
    }
}
