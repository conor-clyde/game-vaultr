package com.cocoding.playstate.model;

public enum PlayLogSessionProgress {
    STARTED("Started"),
    CONTINUING("Continuing"),
    FINISHED("Finished");

    private final String displayLabel;

    PlayLogSessionProgress(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    
    public static PlayLogSessionProgress fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return CONTINUING;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return CONTINUING;
        }
    }
}
