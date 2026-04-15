package com.cocoding.playstate.domain.enums;

import java.util.Locale;

/**
 * Reasons someone picks up a game — play intent. Up to {@link #MAX_PER_GAME} per title. Persisted
 * as enum names in {@code user_games.play_intent} (CSV).
 */
public enum WhyPlaying {
  UNWIND("🌿", "Unwind", "Relax and decompress with something low-pressure"),
  STORY("📖", "Story", "Experience the narrative, world, and characters"),
  CHALLENGE("⚔️", "Challenge", "Push yourself and overcome difficult gameplay"),
  EXPLORE("🗺️", "Explore", "Wander, discover, and engage with side content"),
  PROGRESS("🔁", "Progress", "Make progress, level up, and move things forward"),
  SOCIAL("👥", "Social", "Spend time playing with friends or family"),
  INTENSITY("⚡", "Intensity", "Jump into something fast, exciting, and high-energy"),
  LEGACY("🌀", "Legacy run", "Play through a series or revisit a franchise"),
  CURIOSITY("🎲", "Curiosity", "Try something new without expectations");

  public static final int MAX_PER_GAME = 3;

  private final String emoji;
  private final String title;
  private final String description;

  WhyPlaying(String emoji, String title, String description) {
    this.emoji = emoji;
    this.title = title;
    this.description = description;
  }

  public String getEmoji() {
    return emoji;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  /** Chip / tile line: emoji + compact label (matches filter tiles and summaries). */
  public String getBadgeShort() {
    return emoji + " " + getTitle();
  }

  /** Short display name (no emoji); used for accents and data attributes. */
  public String getDisplayName() {
    return title;
  }

  /**
   * Resolves persisted or request values, including legacy {@code PlayIntent} names stored before
   * the rebrand.
   */
  public static WhyPlaying fromExternalName(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String t = raw.trim();
    WhyPlaying legacy = mapLegacyStored(t);
    if (legacy != null) {
      return legacy;
    }
    try {
      return WhyPlaying.valueOf(t);
    } catch (IllegalArgumentException ignored) {
      try {
        return WhyPlaying.valueOf(t.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored2) {
        return null;
      }
    }
  }

  private static WhyPlaying mapLegacyStored(String t) {
    String u = t.toUpperCase(Locale.ROOT);
    return switch (u) {
      case "ACTION", "INTENSITY", "QUICK_SESSION" -> INTENSITY;
      case "CASUAL", "CHILL" -> UNWIND;
      case "TENSE" -> INTENSITY;
      case "TRYING_SOMETHING_NEW" -> EXPLORE;
      case "SKILL_CHALLENGE" -> CHALLENGE;
      case "COMPLETION" -> PROGRESS;
      case "CREATE" -> STORY;
      case "SERIES_RUN", "LEGACY" -> LEGACY;
      case "CURIOUS", "CURIOSITY", "LEGACY_DISCOVERY" -> CURIOSITY;
      default -> null;
    };
  }
}
