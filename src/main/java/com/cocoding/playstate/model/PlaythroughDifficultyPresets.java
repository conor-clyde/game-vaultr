package com.cocoding.playstate.model;

import java.util.List;

public final class PlaythroughDifficultyPresets {

  public static final String EASY = "Easy";
  public static final String NORMAL = "Normal";
  public static final String HARD = "Hard";
  public static final String VERY_HARD = "Very Hard";

  public static final List<String> OPTIONS = List.of(EASY, NORMAL, HARD, VERY_HARD);

  private PlaythroughDifficultyPresets() {}

}
