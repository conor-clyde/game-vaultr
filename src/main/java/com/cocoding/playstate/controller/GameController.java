package com.cocoding.playstate.controller;

import com.cocoding.playstate.dto.playlog.PlayLogJsonRow;
import com.cocoding.playstate.dto.playlog.PlayLogPageJson;
import com.cocoding.playstate.dto.playthrough.PlaythroughPickerItem;
import com.cocoding.playstate.format.LastPlayedFormat;
import com.cocoding.playstate.format.PlayDurationFormat;
import com.cocoding.playstate.domain.enums.CompletionType;
import com.cocoding.playstate.domain.enums.GameStatus;
import com.cocoding.playstate.domain.enums.OwnershipType;
import com.cocoding.playstate.model.PlayLog;
import com.cocoding.playstate.model.PlaythroughDifficultyPresets;
import com.cocoding.playstate.model.UserGame;
import com.cocoding.playstate.model.UserGamePlaythrough;
import com.cocoding.playstate.domain.enums.WhyPlaying;
import com.cocoding.playstate.repository.PlayLogRepository;
import com.cocoding.playstate.repository.UserGameRepository;
import com.cocoding.playstate.security.LibraryUserIds;
import com.cocoding.playstate.service.CollectionLookupService;
import com.cocoding.playstate.service.CollectionLookupService.OwnedGame;
import com.cocoding.playstate.service.GameEnrichmentService;
import com.cocoding.playstate.service.PlatformSelectionService;
import com.cocoding.playstate.service.PlayLogFormService;
import com.cocoding.playstate.service.PlayLogFormService.PlayLogFormParsed;
import com.cocoding.playstate.service.UserGamePlaythroughService;
import com.cocoding.playstate.util.ReflectionTagCatalog;
import com.cocoding.playstate.util.ReflectionTagsJson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/collection")
public class GameController {

  private final UserGameRepository userGameRepository;
  private final PlayLogRepository playLogRepository;
  private final PlayDurationFormat playDurationFormat;
  private final CollectionLookupService collectionLookup;
  private final GameEnrichmentService gameEnrichmentService;
  private final PlatformSelectionService platformSelectionService;
  private final PlayLogFormService playLogFormService;
  private final UserGamePlaythroughService userGamePlaythroughService;
  private final ObjectMapper objectMapper;

  public GameController(
      UserGameRepository userGameRepository,
      PlayLogRepository playLogRepository,
      PlayDurationFormat playDurationFormat,
      CollectionLookupService collectionLookup,
      GameEnrichmentService gameEnrichmentService,
      PlatformSelectionService platformSelectionService,
      PlayLogFormService playLogFormService,
      UserGamePlaythroughService userGamePlaythroughService,
      ObjectMapper objectMapper) {
    this.userGameRepository = userGameRepository;
    this.playLogRepository = playLogRepository;
    this.playDurationFormat = playDurationFormat;
    this.collectionLookup = collectionLookup;
    this.gameEnrichmentService = gameEnrichmentService;
    this.platformSelectionService = platformSelectionService;
    this.playLogFormService = playLogFormService;
    this.userGamePlaythroughService = userGamePlaythroughService;
    this.objectMapper = objectMapper;
  }

  private static final DateTimeFormatter PLAY_LOG_FULL_AT =
      DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US);

  private static <E extends Enum<E>> E parseEnumOrNull(Class<E> type, String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Enum.valueOf(type, raw.trim());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  /** Play Summary milestone dropdown: Started, Endless, Story complete, 100%. */
  private static final List<CompletionType> MILESTONE_COMPLETION_OPTIONS =
      List.of(
          CompletionType.NOT_COMPLETED,
          CompletionType.ENDLESS,
          CompletionType.MAIN_STORY,
          CompletionType.HUNDRED_PERCENT);

  private static final int PLAY_TIME_LOG_MATCH_TOLERANCE_MINUTES = 2;
  private static final double MAX_TOTAL_PLAY_HOURS_INPUT = 50_000d;

  private static final int REFLECTION_MAX_TAGS = 6;
  private static final int REFLECTION_MAX_TAG_LENGTH = 30;
  private static final int NOTES_MAX_LENGTH = 4000;
  private static final int REVIEW_MAX_LENGTH = 3000;
  private static final int REVIEW_HEADLINE_MAX_LENGTH = 160;
  private static final int REFLECTION_HIGHLIGHT_MAX_LENGTH = 500;
  private static final int SESSION_AWAITING_END_OPEN_HOURS = 24;

  private static boolean isSessionAwaitingEndWindowOpen(LocalDateTime sessionStartedAt) {
    if (sessionStartedAt == null) {
      return false;
    }
    return sessionStartedAt.plusHours(SESSION_AWAITING_END_OPEN_HOURS).isAfter(LocalDateTime.now());
  }

  private PlayLog findOpenSessionAwaitingEnd(String userId, Long gameId) {
    Optional<PlayLog> openSessionOpt =
        playLogRepository
            .findFirstByUserIdAndGameIdAndSessionStartedAtIsNotNullAndDurationMinutesIsNullOrderBySessionStartedAtDescIdDesc(
                userId, gameId);
    PlayLog candidate = openSessionOpt.orElse(null);
    if (candidate == null || candidate.getSessionStartedAt() == null) {
      return null;
    }
    return isSessionAwaitingEndWindowOpen(candidate.getSessionStartedAt()) ? candidate : null;
  }

  private static String toIsoLocalDateTimeOrNull(LocalDateTime value) {
    return value == null ? null : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }

  private static long toEpochMillisOrZero(LocalDateTime value) {
    return value == null ? 0L : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }

  /**
   * Open session (started, not ended) in the end-session window — blocks other edits on the game
   * page.
   */
  private boolean hasActivePlaySessionAwaitingEnd(String userId, Long gameId) {
    return findOpenSessionAwaitingEnd(userId, gameId) != null;
  }

  private static final List<GameStatus> COLLECTION_STATUS_ORDER =
      Arrays.asList(
          GameStatus.PLAYING,
          GameStatus.PAUSED,
          GameStatus.FINISHED,
          GameStatus.NOT_PLAYING,
          GameStatus.DROPPED);


  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) {
      return a;
    }
    if (b != null && !b.isBlank()) {
      return b;
    }
    return null;
  }


  @GetMapping("/{apiId}")
  public String collectionGame(
      Authentication authentication, @PathVariable String apiId, Model model) {
    String userId = LibraryUserIds.require(authentication);
    Optional<OwnedGame> owned = collectionLookup.findOwnedGame(userId, apiId);
    if (owned.isEmpty()) {
      return "redirect:/";
    }
    com.cocoding.playstate.model.Game game = owned.get().game();
    UserGame ug = owned.get().userGame();
    gameEnrichmentService.enrichFromIgdbIfIncompleteWithCooldown(game, ug.getPlatform());

    GamePageStats stats = loadGamePageStats(userId, game.getId(), ug);
    addCoreGameModelAttributes(model, game, ug, stats);
    addPlaythroughModelAttributes(model, userId, game.getId());

    model.addAttribute("title", game.getTitle());
    return "pages/collection-game";
  }

  private record GamePageStats(
      int playLogSumMinutes,
      int totalPlayMinutes,
      String personalPlayTimeHours,
      LocalDateTime lastPlayedAt,
      String lastPlayedAtIso,
      String lastPlayedHeroValue,
      long playSessionCount,
      String lastPlayedCalendarLine,
      String totalPlayTimeLabel,
      PlayLog openSession,
      Long openSessionLogId,
      String openSessionStartedAtIso,
      long openSessionStartedAtEpochMs) {}

  private GamePageStats loadGamePageStats(String userId, Long gameId, UserGame ug) {
    Page<PlayLog> latestPlayLogPage =
        playLogRepository.findByUserIdAndGameIdOrderByPlayedAtDescIdDesc(
            userId, gameId, PageRequest.of(0, 1));
    LocalDateTime lastPlayedAt =
        latestPlayLogPage.isEmpty() ? null : latestPlayLogPage.getContent().get(0).getPlayedAt();
    long playLogSumMinutesLong = playLogRepository.sumDurationMinutesByUserIdAndGameId(userId, gameId);
    int playLogSumMinutes = (int) Math.min(playLogSumMinutesLong, Integer.MAX_VALUE);
    int totalPlayMinutes = UserGame.computeDisplayPlayMinutes(ug, playLogSumMinutes);
    String personalPlayTimeHours = playDurationFormat.forRecordHoursInputValue(totalPlayMinutes);
    String lastPlayedHeroValue = LastPlayedFormat.relativeLabel(lastPlayedAt);
    String lastPlayedAtIso =
        lastPlayedAt == null ? null : lastPlayedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    long playSessionCount = playLogRepository.countByUserIdAndGameId(userId, gameId);
    String lastPlayedCalendarLine = LastPlayedFormat.calendarLine(lastPlayedAt);
    String totalPlayTimeLabel = playDurationFormat.forRecordTotalPlaytimeRead(totalPlayMinutes);
    PlayLog openSession = findOpenSessionAwaitingEnd(userId, gameId);
    String openSessionStartedAtIso =
        toIsoLocalDateTimeOrNull(openSession != null ? openSession.getSessionStartedAt() : null);
    Long openSessionLogId = openSession != null ? openSession.getId() : null;
    long openSessionStartedAtEpochMs =
        toEpochMillisOrZero(openSession != null ? openSession.getSessionStartedAt() : null);

    return new GamePageStats(
        playLogSumMinutes,
        totalPlayMinutes,
        personalPlayTimeHours,
        lastPlayedAt,
        lastPlayedAtIso,
        lastPlayedHeroValue,
        playSessionCount,
        lastPlayedCalendarLine,
        totalPlayTimeLabel,
        openSession,
        openSessionLogId,
        openSessionStartedAtIso,
        openSessionStartedAtEpochMs);
  }

  private void addCoreGameModelAttributes(
      Model model, com.cocoding.playstate.model.Game game, UserGame ug, GamePageStats stats) {
    model.addAttribute("game", game);
    model.addAttribute("userGame", ug);
    model.addAttribute("gameStatuses", COLLECTION_STATUS_ORDER);
    model.addAttribute("completionTypes", MILESTONE_COMPLETION_OPTIONS);
    model.addAttribute("ownershipTypes", OwnershipType.values());
    model.addAttribute("ownershipPlatformOptions", platformSelectionService.ownershipPlatformChoices(game, ug));
    model.addAttribute("playLogSumMinutes", stats.playLogSumMinutes());
    model.addAttribute("totalPlayMinutes", stats.totalPlayMinutes());
    model.addAttribute("personalPlayTimeHours", stats.personalPlayTimeHours());
    model.addAttribute("lastPlayedAt", stats.lastPlayedAt());
    model.addAttribute("lastPlayedAtIso", stats.lastPlayedAtIso());
    model.addAttribute("lastPlayedHeroValue", stats.lastPlayedHeroValue());
    model.addAttribute("playSessionCount", stats.playSessionCount());
    model.addAttribute("lastPlayedCalendarLine", stats.lastPlayedCalendarLine());
    model.addAttribute("totalPlayTimeLabel", stats.totalPlayTimeLabel());
    model.addAttribute("logPlayModalPageHasPlayLogs", stats.lastPlayedAt() != null);
    model.addAttribute("logPlayModalPageLastPlayedRel", stats.lastPlayedHeroValue());
    model.addAttribute("logPlayModalPagePlaySessionCount", stats.playSessionCount());
    model.addAttribute("logPlayModalPageLastPlayedCalendar", stats.lastPlayedCalendarLine());
    model.addAttribute("logPlayModalPageTotalPlayLabel", stats.totalPlayTimeLabel());
    model.addAttribute("openSession", stats.openSession());
    model.addAttribute("openSessionLogId", stats.openSessionLogId());
    model.addAttribute("openSessionStartedAtIso", stats.openSessionStartedAtIso());
    model.addAttribute("openSessionStartedAtEpochMs", stats.openSessionStartedAtEpochMs());
  }

  private void addPlaythroughModelAttributes(Model model, String userId, Long gameId) {
    List<UserGamePlaythrough> gamePlaythroughs =
        new ArrayList<>(userGamePlaythroughService.findForGame(userId, gameId));
    gamePlaythroughs.sort(
        Comparator.comparing((UserGamePlaythrough p) -> !p.isCurrent())
            .thenComparingInt(UserGamePlaythrough::getSortIndex));
    model.addAttribute("gamePlaythroughs", gamePlaythroughs);

    Long activePlaythroughId =
        gamePlaythroughs.stream()
            .filter(UserGamePlaythrough::isCurrent)
            .map(UserGamePlaythrough::getId)
            .findFirst()
            .orElse(null);
    Long expandedPlaythroughId = activePlaythroughId;
    if (expandedPlaythroughId == null && !gamePlaythroughs.isEmpty()) {
      expandedPlaythroughId =
          gamePlaythroughs.stream()
              .max(
                  Comparator.comparingInt(UserGamePlaythrough::getSortIndex)
                      .thenComparingLong(UserGamePlaythrough::getId))
              .map(UserGamePlaythrough::getId)
              .orElse(null);
    }
    model.addAttribute("activePlaythroughId", activePlaythroughId);
    model.addAttribute("expandedPlaythroughId", expandedPlaythroughId);

    Map<Long, Long> playthroughLogCounts = new HashMap<>();
    for (UserGamePlaythrough pt : gamePlaythroughs) {
      playthroughLogCounts.put(
          pt.getId(),
          playLogRepository.countByUserIdAndGameIdAndPlaythroughId(userId, gameId, pt.getId()));
    }
    model.addAttribute("playthroughLogCounts", playthroughLogCounts);

    List<UserGamePlaythrough> playthroughCreationOrder = new ArrayList<>(gamePlaythroughs);
    playthroughCreationOrder.sort(
        Comparator.comparingInt(UserGamePlaythrough::getSortIndex)
            .thenComparingLong(UserGamePlaythrough::getId));
    Map<Long, Integer> playthroughNumberById = new HashMap<>();
    for (int i = 0; i < playthroughCreationOrder.size(); i++) {
      playthroughNumberById.put(playthroughCreationOrder.get(i).getId(), i + 1);
    }
    model.addAttribute("playthroughNumberById", playthroughNumberById);

    Optional<UserGamePlaythrough> currentPlaythroughOpt =
        gamePlaythroughs.stream().filter(UserGamePlaythrough::isCurrent).findFirst();
    final int plogBootstrapSize = 10;
    Map<String, PlayLogPageJson> playthroughLogsBootstrap = new LinkedHashMap<>();
    DateTimeFormatter progressSessionDateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
    Map<Long, String> playthroughLastPlayedDisplay = new LinkedHashMap<>();
    for (UserGamePlaythrough pt : gamePlaythroughs) {
      Page<PlayLog> plogPage =
          playLogRepository.findByUserIdAndGameIdAndPlaythroughIdOrderByPlayedAtDescIdDesc(
              userId, gameId, pt.getId(), PageRequest.of(0, plogBootstrapSize));
      if (!plogPage.isEmpty()) {
        LocalDateTime newest = plogPage.getContent().get(0).getPlayedAt();
        if (newest != null) {
          playthroughLastPlayedDisplay.put(pt.getId(), newest.format(progressSessionDateFmt));
        }
      }
      List<PlayLogJsonRow> items =
          plogPage.getContent().stream().map(this::toPlayLogJsonRow).toList();
      playthroughLogsBootstrap.put(
          String.valueOf(pt.getId()), new PlayLogPageJson(items, plogPage.hasNext()));
    }
    model.addAttribute("playthroughLastPlayedDisplay", playthroughLastPlayedDisplay);
    try {
      model.addAttribute(
          "playthroughLogsBootstrapJson",
          jsonForHtmlScriptElement(objectMapper.writeValueAsString(playthroughLogsBootstrap)));
    } catch (JsonProcessingException e) {
      model.addAttribute("playthroughLogsBootstrapJson", "{}");
    }
    model.addAttribute("currentPlaythrough", currentPlaythroughOpt.orElse(null));
    model.addAttribute(
        "otherPlaythroughs", gamePlaythroughs.stream().filter(p -> !p.isCurrent()).toList());
    model.addAttribute(
        "gamePlaythroughsPayloadJson",
        userGamePlaythroughService.toBootstrapPayloadJson(userId, gameId));
    model.addAttribute("gamePlaythroughMax", UserGamePlaythrough.MAX_PER_USER_GAME);
    model.addAttribute("playthroughDifficultyOptions", PlaythroughDifficultyPresets.OPTIONS);
    model.addAttribute("reflectionTagSuggestionsMood", ReflectionTagCatalog.MOOD);
    model.addAttribute("reflectionTagSuggestionsGameplay", ReflectionTagCatalog.GAMEPLAY);
    model.addAttribute("reflectionTagSuggestionsMemory", ReflectionTagCatalog.MEMORY);
  }

  @GetMapping(value = "/{apiId}/play-logs", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public PlayLogPageJson playLogsJson(
      Authentication authentication,
      @PathVariable String apiId,
      @RequestParam(required = false) Long playthroughId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    String userId = LibraryUserIds.require(authentication);
    Optional<OwnedGame> owned = collectionLookup.findOwnedGame(userId, apiId);
    if (owned.isEmpty()) {
      return new PlayLogPageJson(List.of(), false);
    }
    com.cocoding.playstate.model.Game game = owned.get().game();
    int pageSize = Math.min(Math.max(size, 1), 50);
    int pageIndex = Math.max(page, 0);
    if (playthroughId != null) {
      if (userGamePlaythroughService.findOwned(userId, game.getId(), playthroughId).isEmpty()) {
        return new PlayLogPageJson(List.of(), false);
      }
      Page<PlayLog> p =
          playLogRepository.findByUserIdAndGameIdAndPlaythroughIdOrderByPlayedAtDescIdDesc(
              userId, game.getId(), playthroughId, PageRequest.of(pageIndex, pageSize));
      List<PlayLogJsonRow> items = p.getContent().stream().map(this::toPlayLogJsonRow).toList();
      return new PlayLogPageJson(items, p.hasNext());
    }
    Page<PlayLog> p =
        playLogRepository.findByUserIdAndGameIdOrderByPlayedAtDescIdDesc(
            userId, game.getId(), PageRequest.of(pageIndex, pageSize));
    List<PlayLogJsonRow> items = p.getContent().stream().map(this::toPlayLogJsonRow).toList();
    return new PlayLogPageJson(items, p.hasNext());
  }

  /**
   * Single play log row for the log modal. Used when opening "End play" so we resolve the active
   * session by id; the paged play-logs list can omit it because open sessions sort by {@code
   * playedAt} (session start), which may be older than many completed logs on the same game.
   */
  @GetMapping(value = "/{apiId}/play-log/{logId}/json", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public ResponseEntity<PlayLogJsonRow> playLogJsonRow(
      Authentication authentication, @PathVariable String apiId, @PathVariable long logId) {
    String userId = LibraryUserIds.require(authentication);
    Optional<OwnedGame> owned = collectionLookup.findOwnedGame(userId, apiId);
    if (owned.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    com.cocoding.playstate.model.Game game = owned.get().game();
    Optional<PlayLog> logOpt = playLogRepository.findById(logId);
    if (logOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    PlayLog log = logOpt.get();
    if (!userId.equals(log.getUserId()) || !game.getId().equals(log.getGameId())) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(toPlayLogJsonRow(log));
  }

  @GetMapping(value = "/{apiId}/playthroughs-for-log", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public List<PlaythroughPickerItem> playthroughsForLogModal(
      Authentication authentication, @PathVariable String apiId) {
    String userId = LibraryUserIds.require(authentication);
    Optional<OwnedGame> owned = collectionLookup.findOwnedGame(userId, apiId);
    if (owned.isEmpty()) {
      return List.of();
    }
    return playthroughPickerItems(userId, owned.get().game().getId());
  }

  private List<PlaythroughPickerItem> playthroughPickerItems(String userId, Long gameId) {
    List<UserGamePlaythrough> list = userGamePlaythroughService.findForGame(userId, gameId);
    List<PlaythroughPickerItem> out = new ArrayList<>(list.size());
    int n = 0;
    for (UserGamePlaythrough p : list) {
      n++;
      String diff = p.getDifficulty();
      String diffPart = diff != null && !diff.isBlank() ? diff.trim() : "Normal";
      String sn = p.getShortName();
      String namePart = sn != null && !sn.isBlank() ? sn.trim() : ("Playthrough " + n);
      String label = "#" + n + " \u00B7 " + namePart + " / " + diffPart;
      Integer mm = p.getManualPlayMinutes();
      out.add(
          new PlaythroughPickerItem(
              p.getId(),
              label,
              p.isCurrent(),
              mm,
              sn != null && !sn.isBlank() ? sn.trim() : null,
              p.getProgressNote()));
    }
    return out;
  }

  private boolean applyPlaythroughSelection(
      String userId,
      Long gameId,
      PlayLog log,
      boolean isNew,
      String playthroughIdRaw,
      boolean playthroughParamPresent,
      RedirectAttributes redirectAttributes) {
    List<UserGamePlaythrough> pts = userGamePlaythroughService.findForGame(userId, gameId);
    boolean hasPts = !pts.isEmpty();
    boolean submittedId = playthroughIdRaw != null && !playthroughIdRaw.isBlank();
    if (submittedId) {
      try {
        long pid = Long.parseLong(Objects.requireNonNull(playthroughIdRaw).trim());
        if (userGamePlaythroughService.findOwned(userId, gameId, pid).isEmpty()) {
          redirectAttributes.addFlashAttribute(
              "playLogError", "That playthrough is not valid for this game.");
          return false;
        }
        log.setPlaythroughId(pid);
        return true;
      } catch (NumberFormatException e) {
        redirectAttributes.addFlashAttribute("playLogError", "Invalid playthrough.");
        return false;
      }
    }
    if (!hasPts && isNew) {
      UserGamePlaythrough pt =
          userGamePlaythroughService.createDefaultPlaythroughIfNone(userId, gameId);
      log.setPlaythroughId(pt.getId());
      return true;
    }
    if (playthroughParamPresent && !submittedId) {
      log.setPlaythroughId(null);
      return true;
    }
    if (hasPts && !isNew) {
      return true;
    }
    if (hasPts && isNew && !playthroughParamPresent) {
      Optional<UserGamePlaythrough> activeOpt =
          userGamePlaythroughService.findActivePlaythrough(userId, gameId);
      if (activeOpt.isPresent()) {
        log.setPlaythroughId(activeOpt.get().getId());
      } else {
        userGamePlaythroughService
            .findNewestPlaythrough(userId, gameId)
            .ifPresent(p -> log.setPlaythroughId(p.getId()));
      }
    }
    return true;
  }

  @GetMapping(value = "/{apiId}/play-time-display", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public Map<String, Integer> playTimeDisplayForLogModal(
      Authentication authentication,
      @PathVariable String apiId,
      @RequestParam(required = false) Long excludeLogId) {
    String userId = LibraryUserIds.require(authentication);
    Optional<OwnedGame> owned = collectionLookup.findOwnedGame(userId, apiId);
    if (owned.isEmpty()) {
      return Map.of("displayMinutes", 0);
    }
    com.cocoding.playstate.model.Game game = owned.get().game();
    UserGame ug = owned.get().userGame();
    long sum =
        playLogRepository.sumDurationMinutesByUserIdAndGameIdExcludingLog(
            userId, game.getId(), excludeLogId);
    int logSum = (int) Math.min(sum, Integer.MAX_VALUE);
    return Map.of("displayMinutes", UserGame.computeDisplayPlayMinutes(ug, logSum));
  }

  private static String jsonForHtmlScriptElement(String json) {
    if (json == null || json.isEmpty()) {
      return "{}";
    }
    return json.replace("<", "\\u003c");
  }

  private PlayLogJsonRow toPlayLogJsonRow(PlayLog log) {
    LocalDateTime at = log.getPlayedAt();
    String iso = at != null ? at.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "";
    int day = at != null ? at.getDayOfMonth() : 0;
    String monthShort = at != null ? at.format(DateTimeFormatter.ofPattern("MMM", Locale.US)) : "";
    String fullAt = at != null ? at.format(PLAY_LOG_FULL_AT) : "";
    String note = log.getNote() != null ? log.getNote() : "";
    String dur = playDurationFormat.forLogMinutesRowDisplay(log.getDurationMinutes());
    String exp =
        log.getSessionExperience() != null ? log.getSessionExperience().getDisplayLabel() : "";
    String expCode =
        log.getSessionExperience() != null ? log.getSessionExperience().name() : "OKAY";
    LocalDateTime sessionStart = log.getSessionStartedAt();
    String sessionStartedAtIso =
        sessionStart != null ? sessionStart.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "";
    boolean sessionAwaitingEnd =
        sessionStart != null
            && log.getDurationMinutes() == null
            && isSessionAwaitingEndWindowOpen(sessionStart);
    return new PlayLogJsonRow(
        log.getId() != null ? log.getId() : 0L,
        iso,
        day,
        monthShort,
        fullAt,
        note,
        log.isNoteContainsSpoilers(),
        log.getDurationMinutes(),
        dur,
        exp,
        expCode,
        sessionStartedAtIso,
        sessionAwaitingEnd,
        log.getPlaythroughId());
  }

  private Optional<PlayLogFormParsed> parsePlayLogForm(
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
    return playLogFormService.parsePlayLogForm(
        note,
        durationMinutesRaw,
        timeInputModeRaw,
        sessionStartDate,
        sessionStartTime,
        sessionEndDate,
        sessionEndTime,
        clientLocalToday,
        noteContainsSpoilers,
        sessionExperienceRaw,
        playedAtForHoursMode,
        existingLog,
        redirectAttributes);
  }

  @Transactional
  @PostMapping("/{apiId}/play-log")
  public String addPlayLog(
      Authentication authentication,
      @PathVariable String apiId,
      @RequestParam(value = "note", required = false) String note,
      @RequestParam(value = "durationMinutes", required = false) String durationMinutesRaw,
      @RequestParam(value = "timeInputMode", required = false) String timeInputModeRaw,
      @RequestParam(value = "sessionStartDate", required = false) String sessionStartDate,
      @RequestParam(value = "sessionStartTime", required = false) String sessionStartTime,
      @RequestParam(value = "sessionEndDate", required = false) String sessionEndDate,
      @RequestParam(value = "sessionEndTime", required = false) String sessionEndTime,
      @RequestParam(value = "clientLocalToday", required = false) String clientLocalToday,
      @RequestParam(value = "noteContainsSpoilers", defaultValue = "false")
          boolean noteContainsSpoilers,
      @RequestParam(value = "sessionExperience", required = false) String sessionExperienceRaw,
      @RequestParam(value = "redirectTo", required = false) String redirectTo,
      @RequestParam(value = "playthroughId", required = false) String playthroughIdRaw,
      HttpServletRequest request,
      RedirectAttributes redirectAttributes) {
    String userId = LibraryUserIds.require(authentication);
    Optional<OwnedGame> ownedAdd = collectionLookup.findOwnedGame(userId, apiId);
    if (ownedAdd.isEmpty()) {
      return "redirect:/";
    }
    String key = ownedAdd.get().apiIdKey();
    com.cocoding.playstate.model.Game game = ownedAdd.get().game();

    Optional<PlayLogFormParsed> parsed =
        parsePlayLogForm(
            note,
            durationMinutesRaw,
            timeInputModeRaw,
            sessionStartDate,
            sessionStartTime,
            sessionEndDate,
            sessionEndTime,
            clientLocalToday,
            noteContainsSpoilers,
            sessionExperienceRaw,
            LocalDateTime.now(),
            null,
            redirectAttributes);
    if (parsed.isEmpty()) {
      return playLogRedirectAfterSave(redirectTo, key);
    }
    PlayLogFormParsed f = parsed.get();

    PlayLog log =
        new PlayLog(
            userId,
            game.getId(),
            f.playedAt(),
            f.duration(),
            f.trimmedNote().isEmpty() ? null : f.trimmedNote(),
            f.spoilers(),
            f.sessionExperience());
    log.setSessionStartedAt(f.sessionStartedAt());
    if (!applyPlaythroughSelection(
        userId,
        game.getId(),
        log,
        true,
        playthroughIdRaw,
        request.getParameterMap().containsKey("playthroughId"),
        redirectAttributes)) {
      return playLogRedirectAfterSave(redirectTo, key);
    }
    playLogRepository.save(log);

    maybeApplyPlaythroughPlaytimeAfterLogSave(
        userId, game.getId(), null, null, log, parseAbsolutePlaythroughManualTotal(request));
    maybeApplyPlaythroughProgressNoteAfterLogSave(userId, game.getId(), log, request);

    boolean startedSessionAwaitingEnd = f.duration() == null && f.sessionStartedAt() != null;
    if (!startedSessionAwaitingEnd) {
      redirectAttributes.addFlashAttribute("playLogSaved", true);
    }

    return playLogRedirectAfterSave(redirectTo, key);
  }

  @Transactional
  @PostMapping("/{apiId}/play-log/{logId}")
  public String updatePlayLog(
      Authentication authentication,
      @PathVariable String apiId,
      @PathVariable long logId,
      @RequestParam(value = "note", required = false) String note,
      @RequestParam(value = "durationMinutes", required = false) String durationMinutesRaw,
      @RequestParam(value = "timeInputMode", required = false) String timeInputModeRaw,
      @RequestParam(value = "sessionStartDate", required = false) String sessionStartDate,
      @RequestParam(value = "sessionStartTime", required = false) String sessionStartTime,
      @RequestParam(value = "sessionEndDate", required = false) String sessionEndDate,
      @RequestParam(value = "sessionEndTime", required = false) String sessionEndTime,
      @RequestParam(value = "clientLocalToday", required = false) String clientLocalToday,
      @RequestParam(value = "noteContainsSpoilers", defaultValue = "false")
          boolean noteContainsSpoilers,
      @RequestParam(value = "sessionExperience", required = false) String sessionExperienceRaw,
      @RequestParam(value = "redirectTo", required = false) String redirectTo,
      @RequestParam(value = "playthroughId", required = false) String playthroughIdRaw,
      HttpServletRequest request,
      RedirectAttributes redirectAttributes) {
    String userId = LibraryUserIds.require(authentication);
    Optional<OwnedGame> ownedUpd = collectionLookup.findOwnedGame(userId, apiId);
    if (ownedUpd.isEmpty()) {
      return "redirect:/";
    }
    String key = ownedUpd.get().apiIdKey();
    com.cocoding.playstate.model.Game game = ownedUpd.get().game();

    Optional<PlayLog> existingOpt = playLogRepository.findById(logId);
    if (existingOpt.isEmpty()) {
      redirectAttributes.addFlashAttribute("playLogError", "That play log was not found.");
      return "redirect:/collection/" + key;
    }
    PlayLog log = existingOpt.get();
    if (!userId.equals(log.getUserId()) || !game.getId().equals(log.getGameId())) {
      redirectAttributes.addFlashAttribute("playLogError", "You cannot edit that play log.");
      return "redirect:/collection/" + key;
    }

    Integer previousLogDuration = log.getDurationMinutes();
    Long previousPlaythroughId = log.getPlaythroughId();

    Optional<PlayLogFormParsed> parsed =
        parsePlayLogForm(
            note,
            durationMinutesRaw,
            timeInputModeRaw,
            sessionStartDate,
            sessionStartTime,
            sessionEndDate,
            sessionEndTime,
            clientLocalToday,
            noteContainsSpoilers,
            sessionExperienceRaw,
            log.getPlayedAt(),
            log,
            redirectAttributes);
    if (parsed.isEmpty()) {
      return playLogRedirectAfterSave(redirectTo, key);
    }
    PlayLogFormParsed f = parsed.get();

    log.setPlayedAt(f.playedAt());
    log.setDurationMinutes(f.duration());
    log.setSessionStartedAt(f.sessionStartedAt());
    log.setNote(f.trimmedNote().isEmpty() ? null : f.trimmedNote());
    log.setNoteContainsSpoilers(f.spoilers());
    log.setSessionExperience(f.sessionExperience());
    if (!applyPlaythroughSelection(
        userId,
        game.getId(),
        log,
        false,
        playthroughIdRaw,
        request.getParameterMap().containsKey("playthroughId"),
        redirectAttributes)) {
      return playLogRedirectAfterSave(redirectTo, key);
    }
    playLogRepository.save(log);

    maybeApplyPlaythroughPlaytimeAfterLogSave(
        userId,
        game.getId(),
        previousLogDuration,
        previousPlaythroughId,
        log,
        parseAbsolutePlaythroughManualTotal(request));
    maybeApplyPlaythroughProgressNoteAfterLogSave(userId, game.getId(), log, request);

    redirectAttributes.addFlashAttribute("playLogSaved", true);

    return playLogRedirectAfterSave(redirectTo, key);
  }

  @PostMapping("/{apiId}/play-log/{logId}/delete")
  public String deletePlayLog(
      Authentication authentication,
      @PathVariable String apiId,
      @PathVariable long logId,
      RedirectAttributes redirectAttributes) {
    String userId = LibraryUserIds.require(authentication);
    Optional<OwnedGame> ownedDel = collectionLookup.findOwnedGame(userId, apiId);
    if (ownedDel.isEmpty()) {
      return "redirect:/";
    }
    String key = ownedDel.get().apiIdKey();
    com.cocoding.playstate.model.Game game = ownedDel.get().game();
    Optional<PlayLog> logOpt = playLogRepository.findById(logId);
    if (logOpt.isEmpty()) {
      redirectAttributes.addFlashAttribute("playLogError", "That play log was not found.");
      return "redirect:/collection/" + key;
    }
    PlayLog pl = logOpt.get();
    if (!userId.equals(pl.getUserId()) || !game.getId().equals(pl.getGameId())) {
      redirectAttributes.addFlashAttribute("playLogError", "You cannot remove that play log.");
      return "redirect:/collection/" + key;
    }
    if (pl.getPlaythroughId() != null
        && pl.getDurationMinutes() != null
        && pl.getDurationMinutes() > 0) {
      userGamePlaythroughService.addDeltaToManualPlayMinutes(
          userId, game.getId(), pl.getPlaythroughId(), -pl.getDurationMinutes());
    }
    playLogRepository.delete(pl);
    redirectAttributes.addFlashAttribute("playLogDeleted", true);
    return "redirect:/collection/" + key;
  }

  private static String playLogRedirectAfterSave(String redirectTo, String apiIdKey) {
    if ("collection".equals(redirectTo)) {
      return "redirect:/collection";
    }
    if ("home".equals(redirectTo)) {
      return "redirect:/";
    }
    return "redirect:/collection/" + apiIdKey;
  }

  private void maybeApplyPlaythroughPlaytimeAfterLogSave(
      String userId,
      Long gameId,
      Integer previousDuration,
      Long previousPlaythroughId,
      PlayLog saved,
      Optional<Integer> absolutePlaythroughManualMinutes) {
    Long newPt = saved.getPlaythroughId();
    if (absolutePlaythroughManualMinutes.isPresent()) {
      if (newPt == null) {
        return;
      }
      userGamePlaythroughService.setManualPlayMinutes(
          userId, gameId, newPt, absolutePlaythroughManualMinutes.get());
      return;
    }
    int oldM = previousDuration != null ? previousDuration : 0;
    int newM = saved.getDurationMinutes() != null ? saved.getDurationMinutes() : 0;
    if (oldM < 0) {
      oldM = 0;
    }
    if (newM < 0) {
      newM = 0;
    }
    if (previousPlaythroughId != null) {
      if (Objects.equals(previousPlaythroughId, newPt)) {
        userGamePlaythroughService.addDeltaToManualPlayMinutes(
            userId, gameId, previousPlaythroughId, newM - oldM);
        return;
      }
      if (oldM > 0) {
        userGamePlaythroughService.addDeltaToManualPlayMinutes(
            userId, gameId, previousPlaythroughId, -oldM);
      }
    }
    if (newPt != null && newM > 0) {
      userGamePlaythroughService.addDeltaToManualPlayMinutes(userId, gameId, newPt, newM);
    }
  }

  private static Optional<Integer> parseAbsolutePlaythroughManualTotal(HttpServletRequest request) {
    var map = request.getParameterMap();
    if (!map.containsKey("playthroughManualHours")
        && !map.containsKey("playthroughManualMinutes")) {
      return Optional.empty();
    }
    return Optional.of(
        parsePlaythroughManualTotalMinutes(
            request.getParameter("playthroughManualHours"),
            request.getParameter("playthroughManualMinutes")));
  }

  private void maybeApplyPlaythroughProgressNoteAfterLogSave(
      String userId, Long gameId, PlayLog saved, HttpServletRequest request) {
    if (saved == null || saved.getPlaythroughId() == null || request == null) {
      return;
    }
    if (!request.getParameterMap().containsKey("playthroughProgressNote")) {
      return;
    }
    userGamePlaythroughService.setProgressNote(
        userId, gameId, saved.getPlaythroughId(), request.getParameter("playthroughProgressNote"));
  }

  private static final long MAX_PLAYTHROUGH_MANUAL_MINUTES = 50_000L * 60L;

  private static int parsePlaythroughManualTotalMinutes(String hoursRaw, String minutesRaw) {
    int h = parseNonNegativeInt(hoursRaw, 0);
    int mi = parseNonNegativeInt(minutesRaw, 0);
    if (mi > 59) {
      mi = 59;
    }
    long total = (long) h * 60L + mi;
    if (total > MAX_PLAYTHROUGH_MANUAL_MINUTES) {
      total = MAX_PLAYTHROUGH_MANUAL_MINUTES;
    }
    return (int) Math.min(total, Integer.MAX_VALUE);
  }

  private static int parseNonNegativeInt(String raw, int defaultValue) {
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      int v = Integer.parseInt(raw.trim());
      return Math.max(0, v);
    } catch (NumberFormatException ex) {
      return defaultValue;
    }
  }


  @PostMapping("/{apiId}/remove")
  @Transactional
  public String removeFromCollection(
      Authentication authentication,
      @PathVariable String apiId,
      RedirectAttributes redirectAttributes) {
    String userId = LibraryUserIds.require(authentication);
    Optional<OwnedGame> ownedRm = collectionLookup.findOwnedGame(userId, apiId);
    if (ownedRm.isEmpty()) {
      return "redirect:/collection";
    }
    com.cocoding.playstate.model.Game game = ownedRm.get().game();
    if (hasActivePlaySessionAwaitingEnd(userId, game.getId())) {
      redirectAttributes.addFlashAttribute("activeSessionBlocksEdits", true);
      return "redirect:/collection/" + ownedRm.get().apiIdKey();
    }
    playLogRepository.deleteByUserIdAndGameId(userId, game.getId());
    userGamePlaythroughService.deleteForGame(userId, game.getId());
    userGameRepository.deleteByUserIdAndGameId(userId, game.getId());
    redirectAttributes.addFlashAttribute("collectionRemoved", true);
    return "redirect:/collection";
  }

  @PostMapping("/{apiId}/playthrough/delete")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> deleteSinglePlaythrough(
      Authentication authentication,
      @PathVariable String apiId,
      @RequestParam("playthroughId") long playthroughId) {
    String userId = LibraryUserIds.require(authentication);
    Optional<OwnedGame> owned = collectionLookup.findOwnedGame(userId, apiId);
    if (owned.isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("ok", false, "error", "Game not in collection."));
    }
    com.cocoding.playstate.model.Game game = owned.get().game();
    if (hasActivePlaySessionAwaitingEnd(userId, game.getId())) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(
              Map.of(
                  "ok",
                  false,
                  "error",
                  "End your active play session before editing playthroughs."));
    }
    try {
      userGamePlaythroughService.deleteOwnedPlaythrough(userId, game.getId(), playthroughId);
    } catch (IllegalArgumentException ex) {
      String err = ex.getMessage() != null ? ex.getMessage() : "Invalid request.";
      return ResponseEntity.badRequest().body(Map.of("ok", false, "error", err));
    }
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @PostMapping("/{apiId}/personal")
  public String updatePersonalRecord(
      Authentication authentication,
      @PathVariable String apiId,
      @RequestParam(value = "syncCore", defaultValue = "false") boolean syncCoreParam,
      @RequestParam(value = "syncNotes", defaultValue = "false") boolean syncNotesParam,
      @RequestParam(value = "syncRating", defaultValue = "false") boolean syncRatingParam,
      @RequestParam(value = "status", required = false) String statusParam,
      @RequestParam(value = "completionType", required = false) String completionTypeParam,
      @RequestParam(value = "whyPlaying", required = false) String whyPlayingParam,
      @RequestParam(value = "playIntent", required = false) String playIntentLegacyParam,
      @RequestParam(value = "startedDate", required = false) String startedDateParam,
      @RequestParam(value = "finishedDate", required = false) String finishedDateParam,
      @RequestParam(value = "difficulty", required = false) String difficultyParam,
      @RequestParam(value = "timesPlayed", required = false) String timesPlayedParam,
      @RequestParam(value = "progressLabel", required = false) String progressLabelParam,
      @RequestParam(value = "progressPercent", required = false) String progressPercentParam,
      @RequestParam(value = "totalPlayHours", required = false) String totalPlayHoursParam,
      @RequestParam(value = "notes", required = false) String notesParam,
      @RequestParam(value = "rating", required = false) Integer ratingParam,
      @RequestParam(value = "review", required = false) String reviewParam,
      @RequestParam(value = "reviewHeadline", required = false) String reviewHeadlineParam,
      @RequestParam(value = "reflectionTagsJson", required = false) String reflectionTagsJsonParam,
      @RequestParam(value = "reflectionHighlight", required = false)
          String reflectionHighlightParam,
      @RequestParam(value = "playthroughsJson", required = false) String playthroughsJsonParam,
      @RequestParam(value = "syncPlaythroughs", defaultValue = "false")
          boolean syncPlaythroughsParam,
      @RequestParam(value = "playthroughProgressMode", required = false)
          String playthroughProgressModeParam,
      @RequestParam(value = "platform", required = false) String platformPersonalParam,
      @RequestParam(value = "ownershipType", required = false) String ownershipTypePersonalParam,
      RedirectAttributes redirectAttributes) {
    String userId = LibraryUserIds.require(authentication);
    Optional<OwnedGame> ownedPersonal = collectionLookup.findOwnedGame(userId, apiId);
    if (ownedPersonal.isEmpty()) {
      return "redirect:/";
    }
    String key = ownedPersonal.get().apiIdKey();
    com.cocoding.playstate.model.Game game = ownedPersonal.get().game();
    if (hasActivePlaySessionAwaitingEnd(userId, game.getId())) {
      redirectAttributes.addFlashAttribute("activeSessionBlocksEdits", true);
      return "redirect:/collection/" + key;
    }

    boolean syncCore = syncCoreParam;
    boolean syncNotes = syncNotesParam;
    boolean syncRating = syncRatingParam;
    if (!syncCore && !syncNotes && !syncRating && !syncPlaythroughsParam) {
      syncCore = true;
      syncNotes = true;
      syncRating = true;
    }

    UserGame ug = ownedPersonal.get().userGame();

    if (syncCore) {
      GameStatus status = parseEnumOrNull(GameStatus.class, statusParam);
      if (status == null) {
        redirectAttributes.addFlashAttribute("personalError", "Choose a valid status.");
        redirectAttributes.addFlashAttribute("personalReopen", "core");
        return "redirect:/collection/" + key;
      }

      ug.setStatus(status);

      CompletionType completionType = parseEnumOrNull(CompletionType.class, completionTypeParam);
      if (completionTypeParam != null && !completionTypeParam.isBlank() && completionType == null) {
        redirectAttributes.addFlashAttribute("personalError", "Choose a valid milestone.");
        redirectAttributes.addFlashAttribute("personalReopen", "core");
        return "redirect:/collection/" + key;
      }
      ug.setCompletionType(completionType != null ? completionType : CompletionType.NOT_COMPLETED);

      String whyPlayingCsvRaw = firstNonBlank(whyPlayingParam, playIntentLegacyParam);
      List<WhyPlaying> parsedReasons = new ArrayList<>();
      if (whyPlayingCsvRaw != null && !whyPlayingCsvRaw.isBlank()) {
        LinkedHashSet<String> seenNames = new LinkedHashSet<>();
        for (String piece : whyPlayingCsvRaw.trim().split(",")) {
          WhyPlaying p = WhyPlaying.fromExternalName(piece.trim());
          if (p != null
              && seenNames.add(p.name())
              && parsedReasons.size() < WhyPlaying.MAX_PER_GAME) {
            parsedReasons.add(p);
          }
        }
      }
      if (whyPlayingCsvRaw != null && !whyPlayingCsvRaw.isBlank() && parsedReasons.isEmpty()) {
        redirectAttributes.addFlashAttribute(
            "personalError", "Choose one or more valid play intent options.");
        redirectAttributes.addFlashAttribute("personalReopen", "core");
        return "redirect:/collection/" + key;
      }
      ug.setWhyPlayings(parsedReasons);

      if (startedDateParam == null || startedDateParam.isBlank()) {
        ug.setStartedDate(null);
      } else {
        try {
          ug.setStartedDate(LocalDate.parse(startedDateParam.trim()));
        } catch (DateTimeParseException ex) {
          redirectAttributes.addFlashAttribute("personalError", "Choose a valid started date.");
          redirectAttributes.addFlashAttribute("personalReopen", "core");
          return "redirect:/collection/" + key;
        }
      }

      if (finishedDateParam == null || finishedDateParam.isBlank()) {
        ug.setFinishedDate(null);
      } else {
        try {
          ug.setFinishedDate(LocalDate.parse(finishedDateParam.trim()));
        } catch (DateTimeParseException ex) {
          redirectAttributes.addFlashAttribute("personalError", "Choose a valid finished date.");
          redirectAttributes.addFlashAttribute("personalReopen", "core");
          return "redirect:/collection/" + key;
        }
      }

      if (ug.getStartedDate() != null
          && ug.getFinishedDate() != null
          && ug.getFinishedDate().isBefore(ug.getStartedDate())) {
        redirectAttributes.addFlashAttribute(
            "personalError", "Finished date can't be before started date.");
        redirectAttributes.addFlashAttribute("personalReopen", "core");
        return "redirect:/collection/" + key;
      }

      String diffTrim = difficultyParam != null ? difficultyParam.trim() : "";
      if (diffTrim.length() > 128) {
        diffTrim = diffTrim.substring(0, 128);
      }
      ug.setDifficulty(diffTrim.isEmpty() ? null : diffTrim);

      if (timesPlayedParam == null || timesPlayedParam.isBlank()) {
        ug.setTimesPlayed(null);
      } else {
        try {
          int tp = Integer.parseInt(timesPlayedParam.trim());
          if (tp < 1 || tp > 99) {
            redirectAttributes.addFlashAttribute(
                "personalError", "Times played must be from 1 to 99.");
            redirectAttributes.addFlashAttribute("personalReopen", "core");
            return "redirect:/collection/" + key;
          }
          ug.setTimesPlayed(tp);
        } catch (NumberFormatException ex) {
          redirectAttributes.addFlashAttribute(
              "personalError", "Times played must be a whole number.");
          redirectAttributes.addFlashAttribute("personalReopen", "core");
          return "redirect:/collection/" + key;
        }
      }

      long logSumLong = playLogRepository.sumDurationMinutesByUserIdAndGameId(userId, game.getId());
      int logSumAtSave = (int) Math.min(logSumLong, Integer.MAX_VALUE);
      String hoursRaw =
          totalPlayHoursParam != null ? totalPlayHoursParam.trim().replace(',', '.') : "";
      if (hoursRaw.isEmpty()) {
        ug.setPlayTimeManualTotalMinutes(null);
        ug.setPlayTimeManualAnchorLogMinutes(null);
      } else {
        BigDecimal hoursBd;
        try {
          hoursBd = new BigDecimal(hoursRaw);
        } catch (NumberFormatException ex) {
          redirectAttributes.addFlashAttribute(
              "personalError", "Play time must be a number of hours (e.g. 25 or 25.5).");
          redirectAttributes.addFlashAttribute("personalReopen", "core");
          return "redirect:/collection/" + key;
        }
        if (hoursBd.signum() < 0) {
          redirectAttributes.addFlashAttribute("personalError", "Play time cannot be negative.");
          redirectAttributes.addFlashAttribute("personalReopen", "core");
          return "redirect:/collection/" + key;
        }
        if (hoursBd.compareTo(BigDecimal.valueOf(MAX_TOTAL_PLAY_HOURS_INPUT)) > 0) {
          redirectAttributes.addFlashAttribute(
              "personalError", "Play time cannot exceed 50,000 hours.");
          redirectAttributes.addFlashAttribute("personalReopen", "core");
          return "redirect:/collection/" + key;
        }
        BigDecimal minutesBd =
            hoursBd.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP);
        long maxMinutes = (long) MAX_TOTAL_PLAY_HOURS_INPUT * 60L;
        if (minutesBd.compareTo(BigDecimal.valueOf(maxMinutes)) > 0) {
          redirectAttributes.addFlashAttribute("personalError", "Total play time is too large.");
          redirectAttributes.addFlashAttribute("personalReopen", "core");
          return "redirect:/collection/" + key;
        }
        if (minutesBd.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
          redirectAttributes.addFlashAttribute("personalError", "Total play time is too large.");
          redirectAttributes.addFlashAttribute("personalReopen", "core");
          return "redirect:/collection/" + key;
        }
        if (minutesBd.signum() <= 0) {
          ug.setPlayTimeManualTotalMinutes(null);
          ug.setPlayTimeManualAnchorLogMinutes(null);
        } else {
          int parsedMinutes = minutesBd.intValueExact();
          int effectiveBeforeSave = UserGame.computeDisplayPlayMinutes(ug, logSumAtSave);
          boolean hadManualPlayTotal = ug.getPlayTimeManualTotalMinutes() != null;
          boolean clearBecauseMatchesLogSum =
              !hadManualPlayTotal
                  && Math.abs(parsedMinutes - logSumAtSave)
                      <= PLAY_TIME_LOG_MATCH_TOLERANCE_MINUTES;
          boolean keepManualBecauseMatchesDisplayedTotal =
              hadManualPlayTotal
                  && Math.abs(parsedMinutes - effectiveBeforeSave)
                      <= PLAY_TIME_LOG_MATCH_TOLERANCE_MINUTES;
          if (clearBecauseMatchesLogSum) {
            ug.setPlayTimeManualTotalMinutes(null);
            ug.setPlayTimeManualAnchorLogMinutes(null);
          } else if (!keepManualBecauseMatchesDisplayedTotal) {
            ug.setPlayTimeManualTotalMinutes(parsedMinutes);
            ug.setPlayTimeManualAnchorLogMinutes(logSumAtSave);
          }
        }
      }

      if (playthroughProgressModeParam != null && !playthroughProgressModeParam.isBlank()) {
        Optional<UserGamePlaythrough> currentPt =
            userGamePlaythroughService.findActivePlaythrough(userId, game.getId());
      boolean runStateLockedInPlaySummary = currentPt.isPresent() && currentPt.get().isCurrent();
        if (!runStateLockedInPlaySummary) {
          try {
            userGamePlaythroughService.updateCurrentPlaythroughMode(
                userId, game.getId(), playthroughProgressModeParam);
          } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("personalError", ex.getMessage());
            redirectAttributes.addFlashAttribute("personalReopen", "core");
            return "redirect:/collection/" + key;
          }
        }
      }

      if (platformPersonalParam != null && !platformPersonalParam.isBlank()) {
        List<String> allowedPlatforms = platformSelectionService.ownershipPlatformChoices(game, ug);
        String canonicalPlatform =
            platformSelectionService.resolveCanonicalPlatformName(
                platformPersonalParam, allowedPlatforms);
        if (canonicalPlatform == null) {
          redirectAttributes.addFlashAttribute(
              "personalError", "Choose a platform listed for this game.");
          redirectAttributes.addFlashAttribute("personalReopen", "core");
          return "redirect:/collection/" + key;
        }
        ug.setPlatform(canonicalPlatform);
      }

      if (ownershipTypePersonalParam != null) {
        if (ownershipTypePersonalParam.isBlank()) {
          ug.setOwnershipType(null);
        } else {
          OwnershipType ownershipType =
              parseEnumOrNull(OwnershipType.class, ownershipTypePersonalParam);
          if (ownershipType == null) {
            redirectAttributes.addFlashAttribute("personalError", "Choose a valid ownership type.");
            redirectAttributes.addFlashAttribute("personalReopen", "core");
            return "redirect:/collection/" + key;
          }
          ug.setOwnershipType(ownershipType);
        }
      }

      boolean progressRelevant = status == GameStatus.PLAYING || status == GameStatus.PAUSED;
      if (progressRelevant) {
        String prevLabel = ug.getProgressLabel();
        Integer prevPct = ug.getProgressPercent();

        String plTrim = progressLabelParam != null ? progressLabelParam.trim() : "";
        if (plTrim.length() > 500) {
          plTrim = plTrim.substring(0, 500);
        }
        ug.setProgressLabel(plTrim.isEmpty() ? null : plTrim);

        if (progressPercentParam == null || progressPercentParam.isBlank()) {
          ug.setProgressPercent(null);
        } else {
          try {
            int pp = Integer.parseInt(progressPercentParam.trim());
            if (pp < 0 || pp > 100) {
              redirectAttributes.addFlashAttribute(
                  "personalError", "Progress must be from 0 to 100.");
              redirectAttributes.addFlashAttribute("personalReopen", "core");
              return "redirect:/collection/" + key;
            }
            ug.setProgressPercent(pp);
          } catch (NumberFormatException ex) {
            redirectAttributes.addFlashAttribute(
                "personalError", "Progress percent must be a whole number.");
            redirectAttributes.addFlashAttribute("personalReopen", "core");
            return "redirect:/collection/" + key;
          }
        }

        if (!Objects.equals(prevLabel, ug.getProgressLabel())
            || !Objects.equals(prevPct, ug.getProgressPercent())) {
          ug.setProgressUpdatedAt(LocalDateTime.now());
        }
      }
    }

    if (syncPlaythroughsParam && playthroughsJsonParam != null) {
      try {
        userGamePlaythroughService.replaceFromJson(userId, game.getId(), playthroughsJsonParam);
      } catch (IllegalArgumentException ex) {
        redirectAttributes.addFlashAttribute("personalError", ex.getMessage());
        redirectAttributes.addFlashAttribute("personalReopen", "history");
        return "redirect:/collection/" + key;
      } catch (IOException ex) {
        redirectAttributes.addFlashAttribute("personalError", "Could not save playthroughs.");
        redirectAttributes.addFlashAttribute("personalReopen", "history");
        return "redirect:/collection/" + key;
      }
    }

    if (syncNotes) {
      String trimmedNotes = notesParam != null ? notesParam.trim() : "";
      if (trimmedNotes.length() > NOTES_MAX_LENGTH) {
        trimmedNotes = trimmedNotes.substring(0, NOTES_MAX_LENGTH);
      }
      String newNotes = trimmedNotes.isEmpty() ? null : trimmedNotes;
      if (!Objects.equals(newNotes, ug.getNotes())) {
        ug.setNotes(newNotes);
      }
    }

    if (syncRating) {
      String tagsJson =
          ReflectionTagsJson.serializeValidated(
              reflectionTagsJsonParam, REFLECTION_MAX_TAGS, REFLECTION_MAX_TAG_LENGTH);
      ug.setReflectionTagsJson(tagsJson);

      String hlTrim = reflectionHighlightParam != null ? reflectionHighlightParam.trim() : "";
      if (hlTrim.length() > REFLECTION_HIGHLIGHT_MAX_LENGTH) {
        hlTrim = hlTrim.substring(0, REFLECTION_HIGHLIGHT_MAX_LENGTH);
      }
      ug.setReflectionHighlight(hlTrim.isEmpty() ? null : hlTrim);

      String trimmedReview = reviewParam != null ? reviewParam.strip() : "";
      if (trimmedReview.length() > REVIEW_MAX_LENGTH) {
        trimmedReview = trimmedReview.substring(0, REVIEW_MAX_LENGTH);
      }
      String trimmedHeadline = reviewHeadlineParam != null ? reviewHeadlineParam.strip() : "";
      if (trimmedHeadline.length() > REVIEW_HEADLINE_MAX_LENGTH) {
        trimmedHeadline = trimmedHeadline.substring(0, REVIEW_HEADLINE_MAX_LENGTH);
      }
      boolean hasBody = !trimmedReview.isEmpty();
      boolean hasHeadline = !trimmedHeadline.isEmpty();
      boolean reviewPartial = hasHeadline != hasBody;
      if (reviewPartial) {
        redirectAttributes.addFlashAttribute(
            "personalError",
            "Enter both a review headline and review text together, or leave both empty.");
        redirectAttributes.addFlashAttribute("personalReopen", "reflection");
        return "redirect:/collection/" + key;
      }
      boolean hasFullReview = hasHeadline && hasBody;

      if (ratingParam == null || ratingParam == 0) {
        if (hasFullReview) {
          redirectAttributes.addFlashAttribute(
              "personalError",
              "Choose a star rating (1–10) to save your review. You can save a rating on its own"
                  + " without review text.");
          redirectAttributes.addFlashAttribute("personalReopen", "reflection");
          return "redirect:/collection/" + key;
        }
        ug.setRating(null);
        ug.setReview(null);
        ug.setReviewHeadline(null);
      } else {
        if (ratingParam < 1 || ratingParam > 10) {
          redirectAttributes.addFlashAttribute("personalError", "Rating must be between 1 and 10.");
          redirectAttributes.addFlashAttribute("personalReopen", "reflection");
          return "redirect:/collection/" + key;
        }
        ug.setRating(ratingParam);
        if (hasFullReview) {
          ug.setReview(trimmedReview);
          ug.setReviewHeadline(trimmedHeadline);
        } else {
          ug.setReview(null);
          ug.setReviewHeadline(null);
        }
      }
    }

    userGameRepository.save(ug);
    redirectAttributes.addFlashAttribute("personalSaved", true);
    return "redirect:/collection/" + key;
  }

}
