package com.cocoding.playstate.dto.collection;

import java.util.ArrayList;
import java.util.List;

public class CollectionCardView {

  private final String apiId;
  private final String title;
  private final String imageUrl;
  private final String platform;
  private final String platformHeaderClass;
  private final List<String> whyPlayingBadgeShort;
  private final List<String> whyPlayingDataLabels;

  /** Emoji per intent, parallel to {@link #whyPlayingDataLabels} (cover overlay). */
  private final List<String> whyPlayingEmojis;

  private final String whyPlayingPrimaryName;
  private final String whyPlayingNamesCsv;
  private final int displayPlayMinutes;
  private final boolean logModalHasPlayLogs;
  private final String logModalLastPlayedRelative;
  private final long logModalSessionCount;
  private final String logModalLastPlayedCalendarLine;
  private final String logModalTotalPlayTimeLabel;
  private final int logModalPlaythroughCount;

  public CollectionCardView(
      String apiId,
      String title,
      String imageUrl,
      String platform,
      String platformHeaderClass,
      List<String> whyPlayingBadgeShort,
      List<String> whyPlayingDataLabels,
      List<String> whyPlayingEmojis,
      String whyPlayingPrimaryName,
      String whyPlayingNamesCsv,
      int displayPlayMinutes,
      boolean logModalHasPlayLogs,
      String logModalLastPlayedRelative,
      long logModalSessionCount,
      String logModalLastPlayedCalendarLine,
      String logModalTotalPlayTimeLabel,
      int logModalPlaythroughCount) {
    this.apiId = apiId;
    this.title = title;
    this.imageUrl = imageUrl;
    this.platform = platform;
    this.platformHeaderClass = platformHeaderClass;
    this.whyPlayingPrimaryName = whyPlayingPrimaryName;
    this.whyPlayingNamesCsv = whyPlayingNamesCsv == null ? "" : whyPlayingNamesCsv;
    this.displayPlayMinutes = displayPlayMinutes;
    this.logModalHasPlayLogs = logModalHasPlayLogs;
    this.logModalLastPlayedRelative = logModalLastPlayedRelative;
    this.logModalSessionCount = logModalSessionCount;
    this.logModalLastPlayedCalendarLine = logModalLastPlayedCalendarLine;
    this.logModalTotalPlayTimeLabel = logModalTotalPlayTimeLabel;
    this.logModalPlaythroughCount = Math.max(0, logModalPlaythroughCount);
    if (whyPlayingBadgeShort == null || whyPlayingBadgeShort.isEmpty()) {
      this.whyPlayingBadgeShort = List.of();
      this.whyPlayingDataLabels = List.of();
      this.whyPlayingEmojis = List.of();
    } else {
      this.whyPlayingBadgeShort = List.copyOf(whyPlayingBadgeShort);
      List<String> data =
          whyPlayingDataLabels == null ? new ArrayList<>() : new ArrayList<>(whyPlayingDataLabels);
      while (data.size() < this.whyPlayingBadgeShort.size()) {
        data.add(this.whyPlayingBadgeShort.get(data.size()));
      }
      this.whyPlayingDataLabels = List.copyOf(data.subList(0, this.whyPlayingBadgeShort.size()));
      List<String> emojis =
          whyPlayingEmojis == null ? new ArrayList<>() : new ArrayList<>(whyPlayingEmojis);
      while (emojis.size() < this.whyPlayingDataLabels.size()) {
        emojis.add("");
      }
      this.whyPlayingEmojis = List.copyOf(emojis.subList(0, this.whyPlayingDataLabels.size()));
    }
  }

  public String getApiId() {
    return apiId;
  }

  public String getTitle() {
    return title;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public String getPlatform() {
    return platform;
  }

  public String getPlatformHeaderClass() {
    return platformHeaderClass;
  }

  public List<String> getWhyPlayingBadgeShort() {
    return whyPlayingBadgeShort;
  }

  public List<String> getWhyPlayingDataLabels() {
    return whyPlayingDataLabels;
  }

  public List<String> getWhyPlayingEmojis() {
    return whyPlayingEmojis;
  }

  public String getWhyPlayingPrimaryName() {
    return whyPlayingPrimaryName;
  }

  public String getWhyPlayingNamesCsv() {
    return whyPlayingNamesCsv;
  }

  public int getDisplayPlayMinutes() {
    return displayPlayMinutes;
  }

  public boolean isLogModalHasPlayLogs() {
    return logModalHasPlayLogs;
  }

  public String getLogModalLastPlayedRelative() {
    return logModalLastPlayedRelative;
  }

  public long getLogModalSessionCount() {
    return logModalSessionCount;
  }

  public String getLogModalLastPlayedCalendarLine() {
    return logModalLastPlayedCalendarLine;
  }

  public String getLogModalTotalPlayTimeLabel() {
    return logModalTotalPlayTimeLabel;
  }

  public int getLogModalPlaythroughCount() {
    return logModalPlaythroughCount;
  }

  public boolean hasWhyPlayings() {
    return !whyPlayingBadgeShort.isEmpty();
  }

  /** Comma-separated pill labels (no emoji) for collection tile display and ARIA. */
  public String getWhyPlayingCommaDataLabels() {
    if (whyPlayingDataLabels.isEmpty()) {
      return "";
    }
    return String.join(", ", whyPlayingDataLabels);
  }
}
