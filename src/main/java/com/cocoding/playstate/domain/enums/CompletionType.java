package com.cocoding.playstate.domain.enums;

/**
 * Library-level progress milestone for a title (stored on {@code user_games.completion_type}).
 * {@link #MAIN_PLUS_SIDE} is kept for legacy data only; the play-summary picker maps it to {@link
 * #MAIN_STORY} in the UI.
 */
public enum CompletionType {
  NOT_STARTED("Not Started"),
  NOT_COMPLETED("Started"),
  MAIN_STORY("Story complete"),
  /** Retained for persisted rows; new saves should use {@link #MAIN_STORY}. */
  MAIN_PLUS_SIDE("Story complete"),
  HUNDRED_PERCENT("100%"),
  ENDLESS("Endless");

  private final String displayLabel;

  CompletionType(String displayLabel) {
    this.displayLabel = displayLabel;
  }

  public String getDisplayLabel() {
    return displayLabel;
  }

  /**
   * Value for the Play Summary milestone dropdown (four options). Legacy {@link #MAIN_PLUS_SIDE}
   * maps to {@link #MAIN_STORY} so the control can show a matching selection.
   */
  public CompletionType forMilestonePicker() {
    return this == MAIN_PLUS_SIDE ? MAIN_STORY : this;
  }
}
