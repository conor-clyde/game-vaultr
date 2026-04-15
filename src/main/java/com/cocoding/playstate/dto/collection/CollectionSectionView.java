package com.cocoding.playstate.dto.collection;

import java.util.List;

public class CollectionSectionView {

  private final String statusKey;
  private final String displayName;
  private final List<CollectionCardView> games;

  public CollectionSectionView(
      String statusKey, String displayName, List<CollectionCardView> games) {
    this.statusKey = statusKey;
    this.displayName = displayName;
    this.games = games != null ? List.copyOf(games) : List.of();
  }

  public String getStatusKey() {
    return statusKey;
  }

  public String getDisplayName() {
    return displayName;
  }

  public List<CollectionCardView> getGames() {
    return games;
  }
}
