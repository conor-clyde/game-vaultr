package com.cocoding.playstate.format;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public final class LastPlayedFormat {

  private LastPlayedFormat() {}

  public static String relativeLabel(LocalDateTime playedAt) {
    if (playedAt == null) {
      return null;
    }
    ZoneId zone = ZoneId.systemDefault();
    LocalDate playedDay = playedAt.atZone(zone).toLocalDate();
    LocalDate today = LocalDate.now(zone);
    long days = ChronoUnit.DAYS.between(playedDay, today);
    if (days <= 0) {
      return "Today";
    }
    if (days == 1) {
      return "Yesterday";
    }
    if (days < 7) {
      return days + " days ago";
    }
    long weeks = days / 7;
    if (weeks == 1) {
      return "1 week ago";
    }
    return weeks + " weeks ago";
  }

  public static String calendarLine(LocalDateTime playedAt) {
    if (playedAt == null) {
      return null;
    }
    return playedAt
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK));
  }
}
