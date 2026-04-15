package com.cocoding.playstate.dto.home;

import java.time.LocalDateTime;
import java.util.List;

public final class HomeDashboardDtos {

  private HomeDashboardDtos() {}

  public record HomeSessionRow(
      String gameTitle,
      String apiId,
      String imageUrl,
      String noteDisplay,
      LocalDateTime playedAt,
      Integer durationMinutes,
      boolean noteContainsSpoilers,
      boolean noteTruncated,
      String anchorTitle) {}

  public record HomePreviewTile(String title, String apiId, String imageUrl, String statusLabel) {}

  public record HomeInProgressTile(
      String title,
      String apiId,
      String imageUrl,
      String platformLabel,
      List<String> whyPlayingBadgeShort,
      List<String> whyPlayingDataLabels,
      String lastPlayedLabel,
      String lastPlayedHint) {

    public String getWhyPlayingSummaryLine() {
      if (whyPlayingDataLabels != null && !whyPlayingDataLabels.isEmpty()) {
        return String.join(" · ", whyPlayingDataLabels);
      }
      if (whyPlayingBadgeShort == null || whyPlayingBadgeShort.isEmpty()) {
        return "";
      }
      return String.join(" · ", whyPlayingBadgeShort);
    }
  }

  public record HomeHeroQuickRow(
      String title,
      String apiId,
      String imageUrl,
      String statusLabel,
      String platform,
      String intentLabel) {}

  public record HomeHeroStats(
      int gamesInCollection,
      long sessionsLogged,
      int playingNow,
      int finished,
      String avgRatingDisplay,
      String playTimeDisplay,
      String playTimeHoursDisplay) {}

  public record DashboardHeroCopy(
      Phase phase, String lastPlayedGameTitle, String lastPlayedRelative) {

    public enum Phase {
      WELCOME_EMPTY,
      COLLECTION_NO_SESSIONS,
      ACTIVE_LAST_PLAYED,
      SESSIONS_NO_ACTIVE_GAME
    }
  }
}
