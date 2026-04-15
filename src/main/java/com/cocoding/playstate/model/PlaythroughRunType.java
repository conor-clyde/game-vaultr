package com.cocoding.playstate.model;

public enum PlaythroughRunType {
  FIRST_TIME("First Time"),
  REPLAY("Replay"),
  NEW_GAME_PLUS("New Game+"),
  ONGOING_SANDBOX("Ongoing / Sandbox");

  private final String displayLabel;

  PlaythroughRunType(String displayLabel) {
    this.displayLabel = displayLabel;
  }

  public String getDisplayLabel() {
    return displayLabel;
  }
}
