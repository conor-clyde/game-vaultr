package com.cocoding.playstate.service;

import com.cocoding.playstate.catalog.PlatformCatalog;
import com.cocoding.playstate.model.Game;
import com.cocoding.playstate.model.UserGame;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlatformSelectionService {

  public List<String> ownershipPlatformChoices(Game game, UserGame userGame) {
    LinkedHashSet<String> set = new LinkedHashSet<>();
    List<String> storedOptions = game.getPlatformOptionsList();
    if (storedOptions != null) {
      for (String p : storedOptions) {
        if (p != null && !p.isBlank()) {
          set.add(p.trim());
        }
      }
    }
    if (userGame.getPlatform() != null && !userGame.getPlatform().isBlank()) {
      set.add(userGame.getPlatform().trim());
    }
    if (set.isEmpty()) {
      return new ArrayList<>(PlatformCatalog.allIgdbPlatformNamesSorted());
    }
    ArrayList<String> sorted = new ArrayList<>(set);
    sorted.sort(String.CASE_INSENSITIVE_ORDER);
    return sorted;
  }

  public String resolveCanonicalPlatformName(String submitted, List<String> allowed) {
    if (submitted == null) {
      return null;
    }
    String t = submitted.trim();
    if (t.isEmpty()) {
      return null;
    }
    for (String a : allowed) {
      if (a.equalsIgnoreCase(t)) {
        return a;
      }
    }
    return null;
  }
}
