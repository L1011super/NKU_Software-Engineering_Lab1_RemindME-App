package com.remindme.model;

public enum Priority {
    LOW("低"),
    MEDIUM("中"),
    HIGH("高");

    private final String displayName;

    Priority(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

