package com.cocoding.playstate.model;

/**
 * Library-level progress milestone for a title (stored on {@code user_games.completion_type}).
 * {@link #MAIN_PLUS_SIDE} is kept for legacy data only; the play-summary picker maps it to {@link
 * #MAIN_STORY} in the UI.
 */
public enum CompletionType {
  NOT_STARTED("Not Started", "fa-solid fa-infinity"),
  NOT_COMPLETED("Started", "fa-solid fa-hourglass-half"),
  MAIN_STORY("Story complete", "fa-solid fa-flag-checkered"),
  /** Retained for persisted rows; new saves should use {@link #MAIN_STORY}. */
  MAIN_PLUS_SIDE("Story complete", "fa-solid fa-layer-group"),
  HUNDRED_PERCENT("100%", "fa-solid fa-circle-check"),
  ENDLESS("Endless", "fa-solid fa-infinity");

  private final String displayLabel;
  private final String faIconClasses;

  CompletionType(String displayLabel, String faIconClasses) {
    this.displayLabel = displayLabel;
    this.faIconClasses = faIconClasses;
  }

  public String getDisplayLabel() {
    return displayLabel;
  }

  public String getFaIconClasses() {
    return faIconClasses;
  }

  /**
   * Value for the Play Summary milestone dropdown (four options). Legacy {@link #MAIN_PLUS_SIDE}
   * maps to {@link #MAIN_STORY} so the control can show a matching selection.
   */
  public CompletionType forMilestonePicker() {
    return this == MAIN_PLUS_SIDE ? MAIN_STORY : this;
  }
}
