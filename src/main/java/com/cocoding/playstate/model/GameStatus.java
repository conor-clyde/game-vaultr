package com.cocoding.playstate.model;

public enum GameStatus {
    NOT_PLAYING("Backlog"),
    PLAYING("Playing"),
    FINISHED("Finished"),
    PAUSED("Paused"),
    DROPPED("Shelved");
    
    private final String displayName;
    
    GameStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
