package com.cocoding.playstate.dto.playlog;

public record PlayLogJsonRow(
    long id,
    String playedAtIso,
    int dayOfMonth,
    String monthShort,
    String fullPlayedAtLabel,
    String note,
    boolean noteContainsSpoilers,
    Integer durationMinutes,
    String durationLabel,
    String sessionProgressLabel,
    String sessionExperienceLabel,
    String sessionProgressCode,
    String sessionExperienceCode,
    boolean countsTowardLibraryPlaytime,
    String sessionStartedAtIso,
    boolean sessionAwaitingEnd,
    Long playthroughId) {}
