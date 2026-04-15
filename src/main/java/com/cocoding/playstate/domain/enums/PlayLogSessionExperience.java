package com.cocoding.playstate.domain.enums;

import java.util.Locale;

public enum PlayLogSessionExperience {
  BURNT_OUT("Burnt out", "😩"),
  MEH("Meh", "😕"),
  OKAY("Fine", "😐"),
  GOOD("Good", "🙂"),
  GREAT("Great", "😄");

  private final String displayLabel;
  private final String emoji;

  PlayLogSessionExperience(String displayLabel, String emoji) {
    this.displayLabel = displayLabel;
    this.emoji = emoji;
  }

  public String getDisplayLabel() {
    return displayLabel;
  }

  public String getEmoji() {
    return emoji;
  }

  public static PlayLogSessionExperience fromParam(String raw) {
    if (raw == null || raw.isBlank()) {
      return OKAY;
    }
    String u = raw.trim().toUpperCase(Locale.ROOT);
    try {
      return valueOf(u);
    } catch (IllegalArgumentException ex) {
      if ("BAD".equals(u)) {
        return BURNT_OUT;
      }
      if ("NEUTRAL".equals(u)) {
        return OKAY;
      }
      return OKAY;
    }
  }
}
