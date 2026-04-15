package com.cocoding.playstate.service;

import com.cocoding.playstate.domain.enums.PlayLogSessionExperience;
import com.cocoding.playstate.model.PlayLog;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Service
public class PlayLogFormService {

  private static final int PLAY_LOG_NOTE_MAX_LENGTH = 1000;

  public record PlayLogFormParsed(
      String trimmedNote,
      LocalDateTime playedAt,
      Integer duration,
      boolean spoilers,
      PlayLogSessionExperience sessionExperience,
      LocalDateTime sessionStartedAt) {}

  private record PlayLogSessionRange(
      LocalDateTime sessionStart, LocalDateTime sessionEnd, Integer durationMinutes) {}

  public Optional<PlayLogFormParsed> parsePlayLogForm(
      String note,
      String durationMinutesRaw,
      String timeInputModeRaw,
      String sessionStartDate,
      String sessionStartTime,
      String sessionEndDate,
      String sessionEndTime,
      String clientLocalToday,
      boolean noteContainsSpoilers,
      String sessionExperienceRaw,
      LocalDateTime playedAtForHoursMode,
      PlayLog existingLog,
      RedirectAttributes redirectAttributes) {
    String trimmedNote = note != null ? note.trim() : "";
    if (trimmedNote.length() > PLAY_LOG_NOTE_MAX_LENGTH) {
      trimmedNote = trimmedNote.substring(0, PLAY_LOG_NOTE_MAX_LENGTH);
    }

    String mode = timeInputModeRaw != null ? timeInputModeRaw.trim().toLowerCase() : "hours";
    LocalDateTime playedAt = playedAtForHoursMode;
    Integer duration;
    LocalDateTime sessionStartedAtStorage = null;
    if ("range".equals(mode)) {
      boolean hasStart = !anyBlank(sessionStartDate, sessionStartTime);
      boolean hasEnd = !anyBlank(sessionEndDate, sessionEndTime);
      boolean partialStart = anyNonBlank(sessionStartDate, sessionStartTime) && !hasStart;
      boolean partialEnd = anyNonBlank(sessionEndDate, sessionEndTime) && !hasEnd;
      if (partialStart || partialEnd) {
        redirectAttributes.addFlashAttribute(
            "playLogError", "Enter both date and time for session start and end.");
        return Optional.empty();
      }
      boolean anyRangeField =
          anyNonBlank(sessionStartDate, sessionStartTime, sessionEndDate, sessionEndTime);
      if (!anyRangeField) {
        redirectAttributes.addFlashAttribute(
            "playLogError",
            "Enter session start and end, or start only to save and add the end later.");
        return Optional.empty();
      }
      if (hasStart && hasEnd) {
        PlayLogSessionRange range =
            parsePlayLogSessionRange(
                sessionStartDate,
                sessionStartTime,
                sessionEndDate,
                sessionEndTime,
                clientLocalToday);
        if (range == null) {
          redirectAttributes.addFlashAttribute(
              "playLogError", "Could not save that session time. Check start and end.");
          return Optional.empty();
        }
        playedAt = range.sessionEnd();
        duration = range.durationMinutes();
        sessionStartedAtStorage = range.sessionStart();
      } else if (hasStart) {
        if (existingLog != null
            && existingLog.getDurationMinutes() != null
            && existingLog.getDurationMinutes() > 0) {
          redirectAttributes.addFlashAttribute(
              "playLogError", "Enter the session end time, or switch to Duration to edit length.");
          return Optional.empty();
        }
        LocalDateTime startOnly =
            parseSessionRangeInstant(sessionStartDate, sessionStartTime, clientLocalToday);
        if (startOnly == null) {
          redirectAttributes.addFlashAttribute(
              "playLogError", "Could not save that session start. Check date and time.");
          return Optional.empty();
        }
        playedAt = startOnly;
        duration = null;
        sessionStartedAtStorage = startOnly;
      } else {
        LocalDateTime mergeStart = sessionStartForRangeMerge(existingLog);
        if (mergeStart == null) {
          redirectAttributes.addFlashAttribute(
              "playLogError", "Enter session start date and time before the end.");
          return Optional.empty();
        }
        PlayLogSessionRange merged =
            parseEndWithExistingStart(mergeStart, sessionEndDate, sessionEndTime, clientLocalToday);
        if (merged == null) {
          redirectAttributes.addFlashAttribute(
              "playLogError",
              "Could not save that session time. Check end date and time (after start).");
          return Optional.empty();
        }
        playedAt = merged.sessionEnd();
        duration = merged.durationMinutes();
        sessionStartedAtStorage = merged.sessionStart();
      }
    } else {
      duration = parseDurationMinutesParam(durationMinutesRaw);
    }

    boolean spoilers = noteContainsSpoilers && !trimmedNote.isEmpty();
    PlayLogSessionExperience sessionExperience =
        PlayLogSessionExperience.fromParam(sessionExperienceRaw);

    if (isPlaySessionAwaitingEnd(existingLog)) {
      int effectiveDuration = duration != null ? duration : 0;
      if (effectiveDuration < 1) {
        redirectAttributes.addFlashAttribute(
            "playLogError",
            "Session must be at least 1 minute. Wait until the timer reaches 1:00, or cancel the"
                + " session.");
        return Optional.empty();
      }
    }

    return Optional.of(
        new PlayLogFormParsed(
            trimmedNote, playedAt, duration, spoilers, sessionExperience, sessionStartedAtStorage));
  }

  private static boolean isPlaySessionAwaitingEnd(PlayLog log) {
    return log != null && log.getSessionStartedAt() != null && log.getDurationMinutes() == null;
  }

  private static LocalDateTime sessionStartForRangeMerge(PlayLog log) {
    if (log == null) {
      return null;
    }
    return log.getSessionStartedAt();
  }

  private static boolean anyNonBlank(String... values) {
    for (String s : values) {
      if (s != null && !s.isBlank()) {
        return true;
      }
    }
    return false;
  }

  private static boolean anyBlank(String... values) {
    for (String s : values) {
      if (s == null || s.isBlank()) {
        return true;
      }
    }
    return false;
  }

  private static PlayLogSessionRange parsePlayLogSessionRange(
      String sessionStartDate,
      String sessionStartTime,
      String sessionEndDate,
      String sessionEndTime,
      String clientLocalTodayRaw) {
    if (clientLocalTodayRaw == null || clientLocalTodayRaw.isBlank()) {
      return null;
    }
    if (anyBlank(sessionStartDate, sessionStartTime, sessionEndDate, sessionEndTime)) {
      return null;
    }
    LocalDateTime start =
        parseSessionRangeInstant(sessionStartDate, sessionStartTime, clientLocalTodayRaw);
    LocalDateTime end =
        parseSessionRangeInstant(sessionEndDate, sessionEndTime, clientLocalTodayRaw);
    if (start == null || end == null) {
      return null;
    }
    end = adjustRangeEndForOvernightSameCalendarDay(start, end);
    if (!end.isAfter(start)) {
      return null;
    }
    long minsLong = ChronoUnit.MINUTES.between(start, end);
    if (minsLong > 60L * 24 * 7) {
      return null;
    }
    int mins = (int) minsLong;
    return new PlayLogSessionRange(start, end, validDurationMinutes(mins));
  }

  private static LocalDateTime parseSessionRangeInstant(
      String dateRaw, String timeRaw, String clientLocalTodayRaw) {
    if (clientLocalTodayRaw == null || clientLocalTodayRaw.isBlank()) {
      return null;
    }
    if (anyBlank(dateRaw, timeRaw)) {
      return null;
    }
    try {
      LocalDate anchor = LocalDate.parse(clientLocalTodayRaw.trim());
      LocalDate allowedMin = anchor.minusDays(1);
      LocalDate allowedMax = anchor;
      LocalDate d = LocalDate.parse(dateRaw.trim());
      if (d.isBefore(allowedMin) || d.isAfter(allowedMax)) {
        return null;
      }
      LocalTime t = LocalTime.parse(timeRaw.trim());
      return LocalDateTime.of(d, t);
    } catch (DateTimeParseException ex) {
      return null;
    }
  }

  private static LocalDateTime adjustRangeEndForOvernightSameCalendarDay(
      LocalDateTime start, LocalDateTime end) {
    if (!end.isAfter(start)
        && start.toLocalDate().equals(end.toLocalDate())
        && end.isBefore(start)) {
      return end.plusDays(1);
    }
    return end;
  }

  private static PlayLogSessionRange parseEndWithExistingStart(
      LocalDateTime sessionStart,
      String sessionEndDate,
      String sessionEndTime,
      String clientLocalTodayRaw) {
    LocalDateTime end =
        parseSessionRangeInstant(sessionEndDate, sessionEndTime, clientLocalTodayRaw);
    if (end == null) {
      return null;
    }
    end = adjustRangeEndForOvernightSameCalendarDay(sessionStart, end);
    if (!end.isAfter(sessionStart)) {
      if (!sessionStart.equals(end)) {
        return null;
      }
      return new PlayLogSessionRange(sessionStart, end, validDurationMinutes(0));
    }
    long minsLong = ChronoUnit.MINUTES.between(sessionStart, end);
    if (minsLong > 60L * 24 * 7) {
      return null;
    }
    int mins = (int) minsLong;
    return new PlayLogSessionRange(sessionStart, end, validDurationMinutes(mins));
  }

  private static Integer validDurationMinutes(Integer raw) {
    if (raw == null || raw < 0) {
      return null;
    }
    int cap = 60 * 24 * 7;
    return Math.min(raw, cap);
  }

  private static Integer parseDurationMinutesParam(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.trim();
    if (s.isEmpty()) {
      return null;
    }
    try {
      return validDurationMinutes(Integer.parseInt(s));
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
