package com.cocoding.playstate.service;

import com.cocoding.playstate.domain.enums.PlaythroughProgressStatus;
import com.cocoding.playstate.dto.playthrough.PlaythroughsPayload;
import com.cocoding.playstate.dto.playthrough.PlaythroughsPayload.PlaythroughItem;
import com.cocoding.playstate.model.PlaythroughDifficultyPresets;
import com.cocoding.playstate.model.UserGamePlaythrough;
import com.cocoding.playstate.repository.PlayLogRepository;
import com.cocoding.playstate.repository.UserGamePlaythroughRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserGamePlaythroughService {

  private static final long MAX_MANUAL_PLAY_MINUTES = 50_000L * 60L;

  private final UserGamePlaythroughRepository playthroughRepository;
  private final PlayLogRepository playLogRepository;
  private final ObjectMapper objectMapper;

  public UserGamePlaythroughService(
      UserGamePlaythroughRepository playthroughRepository,
      PlayLogRepository playLogRepository,
      ObjectMapper objectMapper) {
    this.playthroughRepository = playthroughRepository;
    this.playLogRepository = playLogRepository;
    this.objectMapper = objectMapper;
  }

  public String toBootstrapPayloadJson(String userId, Long gameId) {
    List<UserGamePlaythrough> list = findForGame(userId, gameId);
    List<PlaythroughItem> items =
        list.stream()
            .map(
                p ->
                    new PlaythroughItem(
                        p.getId(),
                        p.getShortName(),
                        p.getDifficulty() != null ? p.getDifficulty() : "",
                        p.isCurrent(),
                        p.getManualPlayMinutes(),
                        p.getCompletionPercent(),
                        p.getProgressNote(),
                        p.getProgressStatus().name(),
                        p.getEndedDateInputValue()))
            .toList();
    try {
      return objectMapper.writeValueAsString(new PlaythroughsPayload(items));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public List<UserGamePlaythrough> findForGame(String userId, Long gameId) {
    return playthroughRepository.findByUserIdAndGameIdOrderBySortIndexAscIdAsc(userId, gameId);
  }

  public long countForGame(String userId, Long gameId) {
    return playthroughRepository.countByUserIdAndGameId(userId, gameId);
  }

  public Map<Long, Long> countForGames(String userId, List<Long> gameIds) {
    if (gameIds == null || gameIds.isEmpty()) {
      return Map.of();
    }
    return playthroughRepository.countByUserIdAndGameIdIn(userId, gameIds).stream()
        .collect(
            Collectors.toMap(
                row -> (Long) row[0],
                row -> (Long) row[1]));
  }

  @Transactional
  public void replaceFromJson(String userId, Long gameId, String json) throws IOException {
    if (json == null || json.isBlank()) {
      return;
    }
    PlaythroughsPayload payload = objectMapper.readValue(json.trim(), PlaythroughsPayload.class);
    List<PlaythroughItem> raw = payload.items() != null ? payload.items() : List.of();
    if (raw.size() > UserGamePlaythrough.MAX_PER_USER_GAME) {
      throw new IllegalArgumentException("Too many playthroughs.");
    }

    List<UserGamePlaythrough> existing =
        playthroughRepository.findByUserIdAndGameIdOrderBySortIndexAscIdAsc(userId, gameId);
    Map<Long, UserGamePlaythrough> byId =
        existing.stream()
            .collect(Collectors.toMap(UserGamePlaythrough::getId, Function.identity()));

    Set<Long> incomingIds =
        raw.stream().map(PlaythroughItem::id).filter(Objects::nonNull).collect(Collectors.toSet());
    for (Long id : incomingIds) {
      UserGamePlaythrough row = byId.get(id);
      if (row == null || !userId.equals(row.getUserId()) || !gameId.equals(row.getGameId())) {
        throw new IllegalArgumentException("Invalid playthrough id.");
      }
    }

    Set<Long> keepIds = new HashSet<>(incomingIds);
    for (UserGamePlaythrough old : new ArrayList<>(existing)) {
      if (!keepIds.contains(old.getId())) {
        playLogRepository.deleteByUserIdAndGameIdAndPlaythroughId(userId, gameId, old.getId());
        playthroughRepository.delete(old);
      }
    }

    if (raw.isEmpty()) {
      return;
    }

    boolean currentAssigned = false;
    int sort = 0;
    List<UserGamePlaythrough> out = new ArrayList<>();
    for (PlaythroughItem it : raw) {
      String d = normalizeDifficulty(it.difficulty());
      boolean cur = it.current() && !currentAssigned;
      if (cur) {
        currentAssigned = true;
      }
      Integer manualMins = normalizeManualPlayMinutes(it.manualPlayMinutes());
      String shortName = normalizeShortName(it.shortName());
      if (shortName == null) {
        throw new IllegalArgumentException("Each playthrough needs a name.");
      }
      String progressNote = normalizeProgressNote(it.progressNote());
      Instant endedNorm = normalizeEndDate(it.endDate());
      PlaythroughProgressStatus progressSt = parseProgressStatus(it.progressStatus());
      if (it.id() != null) {
        UserGamePlaythrough row = byId.get(it.id());
        row.setSortIndex(sort++);
        row.setShortName(shortName);
        row.setDifficulty(d);
        row.setCurrent(cur);
        row.setManualPlayMinutes(manualMins);
        row.setCompletionPercent(normalizeCompletionPercent(it.completionPercent()));
        row.setProgressNote(progressNote);
        row.setProgressStatus(progressSt);
        row.setEndedAt(endedNorm);
        out.add(row);
      } else {
        UserGamePlaythrough row = new UserGamePlaythrough();
        row.setUserId(userId);
        row.setGameId(gameId);
        row.setSortIndex(sort++);
        row.setShortName(shortName);
        row.setDifficulty(d);
        row.setCurrent(cur);
        row.setManualPlayMinutes(manualMins);
        row.setCompletionPercent(normalizeCompletionPercent(it.completionPercent()));
        row.setProgressNote(progressNote);
        row.setProgressStatus(progressSt);
        row.setEndedAt(endedNorm);
        out.add(row);
      }
    }
    for (UserGamePlaythrough row : out) {
      PlaythroughProgressStatus ps = row.getProgressStatus();
      if (ps == PlaythroughProgressStatus.COMPLETED || ps == PlaythroughProgressStatus.STOPPED) {
        row.setCurrent(false);
      }
    }
    for (UserGamePlaythrough row : out) {
      if (row.isCurrent()) {
        row.setProgressStatus(PlaythroughProgressStatus.PLAYING);
      }
    }
    playthroughRepository.saveAll(out);
  }

  private static String normalizeShortName(String raw) {
    if (raw == null) {
      return null;
    }
    String t = raw.trim();
    if (t.isEmpty()) {
      return null;
    }
    if (t.length() > 40) {
      return t.substring(0, 40);
    }
    return t;
  }

  private static Integer normalizeCompletionPercent(Integer raw) {
    if (raw == null) {
      return null;
    }
    int v = raw;
    if (v < 0) {
      return 0;
    }
    if (v > 100) {
      return 100;
    }
    return v;
  }

  private static String normalizeDifficulty(String difficulty) {
    String d = difficulty != null ? difficulty.trim() : "";
    if (d.isEmpty()) {
      return PlaythroughDifficultyPresets.NORMAL;
    }
    if (d.length() > 128) {
      d = d.substring(0, 128);
    }
    return d;
  }

  private static String normalizeProgressNote(String raw) {
    if (raw == null) {
      return null;
    }
    String t = raw.trim();
    if (t.isEmpty()) {
      return null;
    }
    if (t.length() > 512) {
      return t.substring(0, 512);
    }
    return t;
  }

  private static Instant normalizeEndDate(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      LocalDate d = LocalDate.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
      return d.atStartOfDay(ZoneId.systemDefault()).toInstant();
    } catch (DateTimeException e) {
      return null;
    }
  }

  private static PlaythroughProgressStatus parseProgressStatus(String raw) {
    if (raw == null || raw.isBlank()) {
      return PlaythroughProgressStatus.PLAYING;
    }
    String s = raw.trim().toUpperCase();
    if ("IN_PROGRESS".equals(s)) {
      return PlaythroughProgressStatus.PLAYING;
    }
    if ("DROPPED".equals(s)) {
      return PlaythroughProgressStatus.STOPPED;
    }
    try {
      return PlaythroughProgressStatus.valueOf(s);
    } catch (IllegalArgumentException e) {
      return PlaythroughProgressStatus.PLAYING;
    }
  }

  private static Integer normalizeManualPlayMinutes(Integer raw) {
    if (raw == null || raw <= 0) {
      return null;
    }
    long v = raw.longValue();
    if (v > MAX_MANUAL_PLAY_MINUTES) {
      throw new IllegalArgumentException("Playthrough play time cannot exceed 50,000 hours.");
    }
    if (v > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Playthrough play time is too large.");
    }
    return raw;
  }

  public Optional<UserGamePlaythrough> findActivePlaythrough(String userId, Long gameId) {
    return findForGame(userId, gameId).stream().filter(UserGamePlaythrough::isCurrent).findFirst();
  }

  /**
   * Updates the playthrough marked {@link UserGamePlaythrough#isCurrent()} (active run) from Play
   * Summary edit: UI mode ACTIVE / STOPPED / FINISHED. Mirrors {@link #replaceFromJson} invariants
   * for current vs terminal progress.
   */
  @Transactional
  public void updateCurrentPlaythroughMode(String userId, Long gameId, String modeRaw) {
    List<UserGamePlaythrough> all =
        new ArrayList<>(
            playthroughRepository.findByUserIdAndGameIdOrderBySortIndexAscIdAsc(userId, gameId));
    Optional<UserGamePlaythrough> curOpt =
        all.stream().filter(UserGamePlaythrough::isCurrent).findFirst();
    if (curOpt.isEmpty()) {
      return;
    }
    UserGamePlaythrough pt = curOpt.get();
    String m = modeRaw != null ? modeRaw.trim().toUpperCase() : "ACTIVE";
    switch (m) {
      case "ACTIVE" -> {
        for (UserGamePlaythrough p : all) {
          p.setCurrent(p.getId().equals(pt.getId()));
        }
        pt.setProgressStatus(PlaythroughProgressStatus.PLAYING);
      }
      case "STOPPED" -> {
        pt.setCurrent(false);
        pt.setProgressStatus(PlaythroughProgressStatus.STOPPED);
      }
      case "FINISHED" -> {
        pt.setCurrent(false);
        pt.setProgressStatus(PlaythroughProgressStatus.COMPLETED);
      }
      default -> throw new IllegalArgumentException("Choose a valid playthrough state.");
    }
    for (UserGamePlaythrough row : all) {
      PlaythroughProgressStatus ps = row.getProgressStatus();
      if (ps == PlaythroughProgressStatus.COMPLETED || ps == PlaythroughProgressStatus.STOPPED) {
        row.setCurrent(false);
      }
    }
    for (UserGamePlaythrough row : all) {
      if (row.isCurrent()) {
        row.setProgressStatus(PlaythroughProgressStatus.PLAYING);
      }
    }
    playthroughRepository.saveAll(all);
  }

  public Optional<UserGamePlaythrough> findNewestPlaythrough(String userId, Long gameId) {
    return findForGame(userId, gameId).stream()
        .max(
            Comparator.comparingInt(UserGamePlaythrough::getSortIndex)
                .thenComparingLong(UserGamePlaythrough::getId));
  }

  @Transactional
  public UserGamePlaythrough createDefaultPlaythroughIfNone(String userId, Long gameId) {
    List<UserGamePlaythrough> existing = findForGame(userId, gameId);
    if (!existing.isEmpty()) {
      return existing.getFirst();
    }
    UserGamePlaythrough row = new UserGamePlaythrough();
    row.setUserId(userId);
    row.setGameId(gameId);
    row.setSortIndex(0);
    row.setDifficulty("Normal");
    row.setCurrent(false);
    row.setShortName("Playthrough 1");
    row.setManualPlayMinutes(null);
    row.setCompletionPercent(null);
    return playthroughRepository.save(row);
  }

  public Optional<UserGamePlaythrough> findOwned(String userId, Long gameId, long playthroughId) {
    return playthroughRepository
        .findById(playthroughId)
        .filter(p -> userId.equals(p.getUserId()) && gameId.equals(p.getGameId()));
  }

  @Transactional
  public void deleteForGame(String userId, Long gameId) {
    playLogRepository.clearPlaythroughIdForUserAndGame(userId, gameId);
    playthroughRepository.deleteByUserIdAndGameId(userId, gameId);
  }

  @Transactional
  public void deleteOwnedPlaythrough(String userId, Long gameId, long playthroughId) {
    Optional<UserGamePlaythrough> opt = findOwned(userId, gameId, playthroughId);
    if (opt.isEmpty()) {
      throw new IllegalArgumentException("That playthrough was not found for this game.");
    }
    UserGamePlaythrough row = opt.get();
    playLogRepository.deleteByUserIdAndGameIdAndPlaythroughId(userId, gameId, row.getId());
    playthroughRepository.delete(row);
  }

  @Transactional
  public void setManualPlayMinutes(
      String userId, Long gameId, long playthroughId, Integer totalMinutes) {
    Optional<UserGamePlaythrough> opt = findOwned(userId, gameId, playthroughId);
    if (opt.isEmpty()) {
      return;
    }
    UserGamePlaythrough pt = opt.get();
    if (totalMinutes == null || totalMinutes <= 0) {
      pt.setManualPlayMinutes(null);
    } else {
      long v = Math.min(totalMinutes.longValue(), MAX_MANUAL_PLAY_MINUTES);
      pt.setManualPlayMinutes((int) Math.min(v, Integer.MAX_VALUE));
    }
    playthroughRepository.save(pt);
  }

  @Transactional
  public void setProgressNote(
      String userId, Long gameId, Long playthroughId, String progressNoteRaw) {
    if (playthroughId == null) {
      return;
    }
    Optional<UserGamePlaythrough> opt = findOwned(userId, gameId, playthroughId);
    if (opt.isEmpty()) {
      return;
    }
    UserGamePlaythrough pt = opt.get();
    pt.setProgressNote(normalizeProgressNote(progressNoteRaw));
    playthroughRepository.save(pt);
  }

  @Transactional
  public void addDeltaToManualPlayMinutes(
      String userId, Long gameId, long playthroughId, int deltaMinutes) {
    if (deltaMinutes == 0) {
      return;
    }
    Optional<UserGamePlaythrough> opt = findOwned(userId, gameId, playthroughId);
    if (opt.isEmpty()) {
      return;
    }
    UserGamePlaythrough pt = opt.get();
    long cur = pt.getManualPlayMinutes() != null ? pt.getManualPlayMinutes().longValue() : 0L;
    long next = cur + deltaMinutes;
    if (next < 0) {
      next = 0;
    }
    if (next > MAX_MANUAL_PLAY_MINUTES) {
      next = MAX_MANUAL_PLAY_MINUTES;
    }
    if (next <= 0) {
      pt.setManualPlayMinutes(null);
    } else {
      pt.setManualPlayMinutes((int) Math.min(next, Integer.MAX_VALUE));
    }
    playthroughRepository.save(pt);
  }
}
