package com.cocoding.playstate.web.view;

import java.util.Locale;

public final class CollectionPlatformHeaderStyle {

  private CollectionPlatformHeaderStyle() {}

  private static final String[] PLAYSTATION_MARKERS = {
    "playstation 5",
    "playstation 4",
    "playstation 3",
    "playstation 2",
    "playstation 1",
    "playstation one",
    "playstation portable",
    "playstation vita",
    "ps5",
    "ps4",
    "ps3",
    "ps2",
    "ps1",
    "ps one",
    "psone",
    "ps vita",
    "psvita",
    "psp",
    "playstation",
  };

  private static final String[] NINTENDO_MARKERS = {
    "new nintendo 3ds",
    "nintendo switch",
    "nintendo 3ds",
    "nintendo ds",
    "nintendo gamecube",
    "nintendo 64",
    "nintendo entertainment system",
    "game boy advance",
    "super nintendo",
    "super famicom",
    "game boy color",
    "gamecube",
    "switch",
    "game boy",
    "wii u",
    "snes",
    "famicom",
    "n64",
    "gba",
    "3ds",
    "2ds",
    "wii",
    "nes",
  };

  private static final String[] XBOX_MARKERS = {
    "xbox series x", "xbox series s", "xbox series", "xbox one", "xbox 360", "xbox",
  };

  private static final String[] PC_MARKERS = {
    "pc (microsoft windows)",
    "microsoft windows",
    "windows",
    "macos",
    "mac os",
    "macintosh",
    "linux",
    "ubuntu",
    "debian",
    "steam os",
    "steam deck",
  };

  public static String modifierClass(String platform) {
    if (platform == null || platform.isBlank()) {
      return "";
    }
    String lower = platform.trim().toLowerCase(Locale.ROOT);
    if (containsAnyMarker(lower, PLAYSTATION_MARKERS)) {
      return "coll-platform--ps";
    }
    if (containsAnyMarker(lower, NINTENDO_MARKERS)) {
      return "coll-platform--nin";
    }
    if (containsAnyMarker(lower, XBOX_MARKERS)) {
      return "coll-platform--xb";
    }
    if (lower.equals("pc")
        || lower.equals("mac")
        || lower.startsWith("pc (")
        || containsAnyMarker(lower, PC_MARKERS)) {
      return "coll-platform--pc";
    }
    return "";
  }

  private static boolean containsAnyMarker(String haystack, String[] markers) {
    for (String m : markers) {
      if (haystack.contains(m)) {
        return true;
      }
    }
    return false;
  }
}
