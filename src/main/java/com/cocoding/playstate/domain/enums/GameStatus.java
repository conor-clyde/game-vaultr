package com.cocoding.playstate.domain.enums;

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
