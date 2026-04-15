package com.cocoding.playstate.util;

import java.util.List;

public final class ReflectionTagCatalog {

  private ReflectionTagCatalog() {}

  public static final List<String> MOOD =
      List.of(
          "relaxing",
          "stressful",
          "intense",
          "cozy",
          "addictive",
          "frustrating",
          "immersive",
          "comforting",
          "exhausting");

  public static final List<String> GAMEPLAY =
      List.of(
          "story-heavy",
          "grindy",
          "exploration",
          "competitive",
          "replayable",
          "skill-based",
          "puzzle-like",
          "open-world");

  public static final List<String> MEMORY =
      List.of(
          "masterpiece",
          "underrated",
          "nostalgia",
          "comfort-game",
          "time-sink",
          "challenge-run-worthy",
          "emotional-story");
}
