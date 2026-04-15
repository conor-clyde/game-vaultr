package com.cocoding.playstate.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlatformCatalog {

  private PlatformCatalog() {}

  public static final Map<String, Integer> IDS_BY_IGDB_NAME =
      Map.ofEntries(
          Map.entry("PC (Microsoft Windows)", 6),
          Map.entry("Linux", 3),
          Map.entry("Mac", 14),
          Map.entry("PlayStation", 7),
          Map.entry("PlayStation 2", 8),
          Map.entry("PlayStation 3", 9),
          Map.entry("PlayStation 4", 48),
          Map.entry("PlayStation 5", 167),
          Map.entry("PlayStation Portable", 38),
          Map.entry("PlayStation Vita", 46),
          Map.entry("Xbox", 11),
          Map.entry("Xbox 360", 12),
          Map.entry("Xbox One", 49),
          Map.entry("Xbox Series X|S", 169),
          Map.entry("Nintendo 64", 4),
          Map.entry("Nintendo GameCube", 21),
          Map.entry("Wii", 5),
          Map.entry("Wii U", 41),
          Map.entry("Nintendo Switch", 130),
          Map.entry("Nintendo Switch 2", 508),
          Map.entry("Nintendo DS", 20),
          Map.entry("Nintendo 3DS", 37),
          Map.entry("New Nintendo 3DS", 137),
          Map.entry("Game Boy", 33),
          Map.entry("Game Boy Color", 22),
          Map.entry("Game Boy Advance", 24));

  public static final List<PrimaryChip> PRIMARY_CHIPS =
      List.of(
          new PrimaryChip("PC (Microsoft Windows)", "PC"),
          new PrimaryChip("PlayStation 5", "PS5"),
          new PrimaryChip("Xbox Series X|S", "Xbox S/X"),
          new PrimaryChip("Nintendo Switch 2", "Switch 2"));

  public record PrimaryChip(String igdbName, String label) {}

  private static final Derived DERIVED = Derived.build();

  public static final List<String> SECONDARY_PLATFORM_NAMES = DERIVED.secondaryPlatformNames();

  public static boolean isSecondaryCatalogName(String igdbName) {
    if (igdbName == null) {
      return false;
    }
    String k = igdbName.trim();
    return IDS_BY_IGDB_NAME.containsKey(k) && !DERIVED.primaryIgdbNames().contains(k);
  }

  private record Derived(Set<String> primaryIgdbNames, List<String> secondaryPlatformNames) {

    static Derived build() {
      Set<String> primary = new LinkedHashSet<>();
      for (PrimaryChip chip : PRIMARY_CHIPS) {
        String name = chip.igdbName();
        if (!IDS_BY_IGDB_NAME.containsKey(name)) {
          throw new IllegalStateException(
              "PRIMARY_CHIPS igdbName must exist in IDS_BY_IGDB_NAME: " + name);
        }
        primary.add(name);
      }
      primary = Collections.unmodifiableSet(primary);

      List<String> secondary = new ArrayList<>();
      for (String name : IDS_BY_IGDB_NAME.keySet()) {
        if (!primary.contains(name)) {
          secondary.add(name);
        }
      }
      secondary.sort(String.CASE_INSENSITIVE_ORDER);
      return new Derived(primary, Collections.unmodifiableList(secondary));
    }
  }

  public static List<String> allIgdbPlatformNamesSorted() {
    List<String> names = new ArrayList<>(IDS_BY_IGDB_NAME.keySet());
    names.sort(String.CASE_INSENSITIVE_ORDER);
    return Collections.unmodifiableList(names);
  }
}
