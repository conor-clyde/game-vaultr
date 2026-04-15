package com.cocoding.playstate.model;

import java.util.List;

public final class PlaythroughDifficultyPresets {

    public static final String EASY = "Easy";
    public static final String NORMAL = "Normal";
    public static final String HARD = "Hard";
    public static final String VERY_HARD = "Very Hard";

    public static final List<String> OPTIONS = List.of(EASY, NORMAL, HARD, VERY_HARD);

    private PlaythroughDifficultyPresets() {}

    
    public static String displayBucket(String stored) {
        if (stored == null || stored.isBlank()) {
            return "";
        }
        for (String p : OPTIONS) {
            if (p.equalsIgnoreCase(stored.trim())) {
                return p;
            }
        }
        return "Custom";
    }

    public static boolean isPreset(String stored) {
        if (stored == null || stored.isBlank()) {
            return false;
        }
        for (String p : OPTIONS) {
            if (p.equalsIgnoreCase(stored.trim())) {
                return true;
            }
        }
        return false;
    }
}
