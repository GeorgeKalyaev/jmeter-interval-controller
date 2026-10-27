package com.example.jmeter.interval;

public enum ExpirationFormat {
    UNIX_MILLISECONDS("Unix milliseconds"),
    UNIX_SECONDS("Unix seconds"),
    TTL_SECONDS("TTL seconds");

    private final String label;

    ExpirationFormat(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ExpirationFormat fromLabel(String label) {
        if (label == null || label.trim().isEmpty()) {
            return UNIX_MILLISECONDS;
        }
        for (ExpirationFormat format : values()) {
            if (format.label.equalsIgnoreCase(label.trim()) || format.name().equalsIgnoreCase(label.trim())) {
                return format;
            }
        }
        return UNIX_MILLISECONDS;
    }
}
