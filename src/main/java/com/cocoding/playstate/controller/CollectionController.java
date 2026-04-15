package com.cocoding.playstate.controller;

import com.cocoding.playstate.catalog.PlatformCatalog;
import com.cocoding.playstate.dto.collection.CollectionCardView;
import com.cocoding.playstate.dto.collection.CollectionGameForLogDto;
import com.cocoding.playstate.dto.collection.CollectionSectionView;
import com.cocoding.playstate.dto.playlog.PlayLogJsonRow;
import com.cocoding.playstate.dto.playlog.PlayLogPageJson;
import com.cocoding.playstate.dto.playthrough.PlaythroughPickerItem;
import com.cocoding.playstate.format.PlayDurationFormat;
import com.cocoding.playstate.model.CompletionType;
import com.cocoding.playstate.model.GameStatus;
import com.cocoding.playstate.model.OwnershipType;
import com.cocoding.playstate.model.PlayLog;
import com.cocoding.playstate.model.PlayLogSessionExperience;
import com.cocoding.playstate.model.PlayLogSessionProgress;
import com.cocoding.playstate.model.PlaythroughDifficultyPresets;
import com.cocoding.playstate.model.PlaythroughProgressStatus;
import com.cocoding.playstate.model.UserGame;
import com.cocoding.playstate.model.UserGamePlaythrough;
import com.cocoding.playstate.model.WhyPlaying;
import com.cocoding.playstate.repository.GameRepository;
import com.cocoding.playstate.repository.PlayLogRepository;
import com.cocoding.playstate.repository.UserGameRepository;
import com.cocoding.playstate.security.LibraryUserIds;
import com.cocoding.playstate.service.CollectionLookupService;
import com.cocoding.playstate.service.CollectionLookupService.OwnedGame;
import com.cocoding.playstate.service.GameEnrichmentService;
import com.cocoding.playstate.service.UserGamePlaythroughService;
import com.cocoding.playstate.util.ReflectionTagCatalog;
import com.cocoding.playstate.util.ReflectionTagsJson;
import com.cocoding.playstate.web.view.CollectionPlatformHeaderStyle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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
public class CollectionController {

  private final UserGameRepository userGameRepository;
  private final GameRepository gameRepository;
  private final PlayLogRepository playLogRepository;
  private final PlayDurationFormat playDurationFormat;
  private final CollectionLookupService collectionLookup;
  private final GameEnrichmentService gameEnrichmentService;
  private final UserGamePlaythroughService userGamePlaythroughService;
  private final ObjectMapper objectMapper;

  public CollectionController(
      UserGameRepository userGameRepository,
      GameRepository gameRepository,
      PlayLogRepository playLogRepository,
      PlayDurationFormat playDurationFormat,
      CollectionLookupService collectionLookup,
      GameEnrichmentService gameEnrichmentService,
      UserGamePlaythroughService userGamePlaythroughService,
      ObjectMapper objectMapper) {
    this.userGameRepository = userGameRepository;
    this.gameRepository = gameRepository;
    this.playLogRepository = playLogRepository;
    this.playDurationFormat = playDurationFormat;
    this.collectionLookup = collectionLookup;
    this.gameEnrichmentService = gameEnrichmentService;
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
  private static final int PLAY_LOG_NOTE_MAX_LENGTH = 1000;

  private static final int SESSION_AWAITING_END_OPEN_HOURS = 24;

  private static boolean isSessionAwaitingEndWindowOpen(LocalDateTime sessionStartedAt) {
    if (sessionStartedAt == null) {
      return false;
    }
    return sessionStartedAt.plusHours(SESSION_AWAITING_END_OPEN_HOURS).isAfter(LocalDateTime.now());
  }

  /**
   * Open session (started, not ended) in the end-session window — blocks other edits on the game
   * page.
   */
  private boolean hasActivePlaySessionAwaitingEnd(String userId, Long gameId) {
    Optional<PlayLog> openSessionOpt =
        playLogRepository
            .findFirstByUserIdAndGameIdAndSessionStartedAtIsNotNullAndDurationMinutesIsNullOrderBySessionStartedAtDescIdDesc(
                userId, gameId);
    PlayLog candidate = openSessionOpt.orElse(null);
    return candidate != null
        && candidate.getSessionStartedAt() != null
        && isSessionAwaitingEndWindowOpen(candidate.getSessionStartedAt());
  }

  private static final List<GameStatus> COLLECTION_STATUS_ORDER =
      Arrays.asList(
          GameStatus.PLAYING,
          GameStatus.PAUSED,
          GameStatus.FINISHED,
          GameStatus.NOT_PLAYING,
          GameStatus.DROPPED);

  private static void mergeWhyPlayingQueryParams(List<String> rawList, LinkedHashSet<String> out) {
    if (rawList == null) {
      return;
    }
    for (String raw : rawList) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      WhyPlaying w = WhyPlaying.fromExternalName(raw.trim());
      if (w != null) {
        out.add(w.name());
      }
    }
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) {
      return a;
    }
    if (b != null && !b.isBlank()) {
      return b;
    }
    return null;
  }

  @GetMapping
  public String collectionPage(
      Authentication authentication,
      @RequestParam(required = false) List<String> platform,
      @RequestParam(required = false) List<String> whyPlaying,
      @RequestParam(required = false) List<String> playIntent,
      Model model) {
    String userId = LibraryUserIds.require(authentication);
    model.addAttribute("title", "My collection");

    List<UserGame> userGames = userGameRepository.findByUserId(userId);
    int totalLibraryGames = userGames.size();

    LinkedHashSet<String> selectedPlatforms = new LinkedHashSet<>();
    if (platform != null) {
      for (String p : platform) {
        if (p != null && !p.isBlank()) {
          selectedPlatforms.add(p.trim());
        }
      }
    }
    boolean hasPlatformFilter = !selectedPlatforms.isEmpty();

    LinkedHashSet<String> selectedWhyPlayings = new LinkedHashSet<>();
    mergeWhyPlayingQueryParams(whyPlaying, selectedWhyPlayings);
    mergeWhyPlayingQueryParams(playIntent, selectedWhyPlayings);
    boolean hasWhyPlayingFilter = !selectedWhyPlayings.isEmpty();
    boolean hasCollectionFilter = hasPlatformFilter || hasWhyPlayingFilter;

    LinkedHashSet<String> distinctPlatforms = new LinkedHashSet<>();
    for (UserGame ug : userGames) {
      if (ug.getPlatform() != null && !ug.getPlatform().isBlank()) {
        distinctPlatforms.add(ug.getPlatform().trim());
      }
    }
    ArrayList<String> platformOptions = new ArrayList<>(distinctPlatforms);
    platformOptions.sort(String.CASE_INSENSITIVE_ORDER);

    Map<String, List<CollectionCardView>> gamesByStatus = new LinkedHashMap<>();
    for (GameStatus s : COLLECTION_STATUS_ORDER) {
      gamesByStatus.put(s.name(), new ArrayList<>());
    }

    for (UserGame ug : userGames) {
      Optional<com.cocoding.playstate.model.Game> opt = gameRepository.findById(ug.getGameId());
      if (opt.isEmpty()) {
        continue;
      }
      String platformStr = ug.getPlatform() != null ? ug.getPlatform().trim() : "";

      com.cocoding.playstate.model.Game g = opt.get();
      String statusKey =
          ug.getStatus() != null ? ug.getStatus().name() : GameStatus.NOT_PLAYING.name();
      gamesByStatus.computeIfAbsent(statusKey, k -> new ArrayList<>());
      List<String> intentBadges = new ArrayList<>();
      List<String> intentDisplay = new ArrayList<>();
      List<String> intentEmojis = new ArrayList<>();
      List<WhyPlaying> intents = ug.getWhyPlayings();
      String intentName = null;
      String intentNamesCsv = "";
      for (WhyPlaying pi : intents) {
        intentBadges.add(pi.getBadgeShort());
        intentDisplay.add(pi.getTitle());
        intentEmojis.add(pi.getEmoji());
      }
      if (!intents.isEmpty()) {
        intentName = intents.get(0).name();
        List<String> csvParts = new ArrayList<>(intents.size());
        for (WhyPlaying pi : intents) {
          csvParts.add(pi.name());
        }
        intentNamesCsv = String.join(",", csvParts);
      }
      long logSumCardLong =
          playLogRepository.sumDurationMinutesByUserIdAndGameId(userId, g.getId());
      int logSumCard = (int) Math.min(logSumCardLong, Integer.MAX_VALUE);
      int displayPlayMinutesCard = UserGame.computeDisplayPlayMinutes(ug, logSumCard);
      Page<PlayLog> latestCardLog =
          playLogRepository.findByUserIdAndGameIdOrderByPlayedAtDescIdDesc(
              userId, g.getId(), PageRequest.of(0, 1));
      LocalDateTime lastPlayedCard =
          latestCardLog.isEmpty() ? null : latestCardLog.getContent().get(0).getPlayedAt();
      boolean logModalHasPlayLogs = lastPlayedCard != null;
      String logModalLastPlayedRelative = lastPlayedRelativeLabel(lastPlayedCard);
      long logModalSessionCount = playLogRepository.countByUserIdAndGameId(userId, g.getId());
      String logModalLastPlayedCalendarLine = formatLastPlayedCalendarLine(lastPlayedCard);
      String logModalTotalPlayTimeLabel =
          playDurationFormat.forRecordTotalPlaytimeRead(displayPlayMinutesCard);
      long ptCountCard = userGamePlaythroughService.countForGame(userId, g.getId());
      int logModalPlaythroughCount = (int) Math.min(ptCountCard, Integer.MAX_VALUE);
      gamesByStatus
          .get(statusKey)
          .add(
              new CollectionCardView(
                  g.getApiId(),
                  g.getTitle(),
                  g.getImageUrl() != null ? g.getImageUrl() : "",
                  platformStr,
                  CollectionPlatformHeaderStyle.modifierClass(platformStr),
                  intentBadges,
                  intentDisplay,
                  intentEmojis,
                  intentName,
                  intentNamesCsv,
                  displayPlayMinutesCard,
                  logModalHasPlayLogs,
                  logModalLastPlayedRelative,
                  logModalSessionCount,
                  logModalLastPlayedCalendarLine,
                  logModalTotalPlayTimeLabel,
                  logModalPlaythroughCount));
    }

    List<CollectionSectionView> sections = new ArrayList<>();
    for (GameStatus s : COLLECTION_STATUS_ORDER) {
      String key = s.name();
      List<CollectionCardView> games = gamesByStatus.get(key);
      if (games != null && !games.isEmpty()) {
        sections.add(new CollectionSectionView(key, s.getDisplayName(), games));
      }
    }

    model.addAttribute("sections", sections);
    model.addAttribute("totalLibraryGames", totalLibraryGames);
    model.addAttribute("collectionStatusOrder", COLLECTION_STATUS_ORDER);
    model.addAttribute("collectionPlatformOptions", platformOptions);
    model.addAttribute("selectedCollectionPlatforms", selectedPlatforms);
    model.addAttribute("selectedCollectionWhyPlayings", selectedWhyPlayings);
    model.addAttribute("hasWhyPlayingFilter", hasWhyPlayingFilter);
    model.addAttribute("hasCollectionFilter", hasCollectionFilter);

    return "pages/collection";
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

    Page<PlayLog> latestPlayLogPage =
        playLogRepository.findByUserIdAndGameIdOrderByPlayedAtDescIdDesc(
            userId, game.getId(), PageRequest.of(0, 1));
    LocalDateTime lastPlayedAt =
        latestPlayLogPage.isEmpty() ? null : latestPlayLogPage.getContent().get(0).getPlayedAt();
    long playLogSumMinutesLong =
        playLogRepository.sumDurationMinutesByUserIdAndGameId(userId, game.getId());
    int playLogSumMinutes = (int) Math.min(playLogSumMinutesLong, Integer.MAX_VALUE);
    int totalPlayMinutes = UserGame.computeDisplayPlayMinutes(ug, playLogSumMinutes);
    String personalPlayTimeHours = playDurationFormat.forRecordHoursInputValue(totalPlayMinutes);
    String lastPlayedHeroValue = lastPlayedRelativeLabel(lastPlayedAt);
    String lastPlayedAtIso =
        lastPlayedAt == null ? null : lastPlayedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    long playSessionCount = playLogRepository.countByUserIdAndGameId(userId, game.getId());
    String lastPlayedCalendarLine = formatLastPlayedCalendarLine(lastPlayedAt);
    String totalPlayTimeLabel = playDurationFormat.forRecordTotalPlaytimeRead(totalPlayMinutes);
    Optional<PlayLog> openSessionOpt =
        playLogRepository
            .findFirstByUserIdAndGameIdAndSessionStartedAtIsNotNullAndDurationMinutesIsNullOrderBySessionStartedAtDescIdDesc(
                userId, game.getId());
    PlayLog openSessionCandidate = openSessionOpt.orElse(null);
    boolean heroOpenSession =
        openSessionCandidate != null
            && openSessionCandidate.getSessionStartedAt() != null
            && isSessionAwaitingEndWindowOpen(openSessionCandidate.getSessionStartedAt());
    PlayLog openSession = heroOpenSession ? openSessionCandidate : null;
    String openSessionStartedAtIso =
        openSession != null && openSession.getSessionStartedAt() != null
            ? openSession.getSessionStartedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            : null;
    Long openSessionLogId = openSession != null ? openSession.getId() : null;
    long openSessionStartedAtEpochMs =
        openSession != null && openSession.getSessionStartedAt() != null
            ? openSession
                .getSessionStartedAt()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            : 0L;

    model.addAttribute("game", game);
    model.addAttribute("userGame", ug);
    model.addAttribute("gameStatuses", COLLECTION_STATUS_ORDER);
    model.addAttribute("completionTypes", MILESTONE_COMPLETION_OPTIONS);
    model.addAttribute("ownershipTypes", OwnershipType.values());
    model.addAttribute("ownershipPlatformOptions", ownershipPlatformChoices(game, ug));
    model.addAttribute("playLogSumMinutes", playLogSumMinutes);
    model.addAttribute("totalPlayMinutes", totalPlayMinutes);
    model.addAttribute("personalPlayTimeHours", personalPlayTimeHours);
    model.addAttribute("lastPlayedAt", lastPlayedAt);
    model.addAttribute("lastPlayedAtIso", lastPlayedAtIso);
    model.addAttribute("lastPlayedHeroValue", lastPlayedHeroValue);
    model.addAttribute("playSessionCount", playSessionCount);
    model.addAttribute("lastPlayedCalendarLine", lastPlayedCalendarLine);
    model.addAttribute("totalPlayTimeLabel", totalPlayTimeLabel);
    model.addAttribute("logPlayModalPageHasPlayLogs", lastPlayedAt != null);
    model.addAttribute("logPlayModalPageLastPlayedRel", lastPlayedHeroValue);
    model.addAttribute("logPlayModalPagePlaySessionCount", playSessionCount);
    model.addAttribute("logPlayModalPageLastPlayedCalendar", lastPlayedCalendarLine);
    model.addAttribute("logPlayModalPageTotalPlayLabel", totalPlayTimeLabel);
    model.addAttribute("openSession", openSession);
    model.addAttribute("openSessionLogId", openSessionLogId);
    model.addAttribute("openSessionStartedAtIso", openSessionStartedAtIso);
    model.addAttribute("openSessionStartedAtEpochMs", openSessionStartedAtEpochMs);
    List<UserGamePlaythrough> gamePlaythroughs =
        new ArrayList<>(userGamePlaythroughService.findForGame(userId, game.getId()));
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
    Long expandedPlaythroughId;
    if (activePlaythroughId != null) {
      expandedPlaythroughId = activePlaythroughId;
    } else if (!gamePlaythroughs.isEmpty()) {
      expandedPlaythroughId =
          gamePlaythroughs.stream()
              .max(
                  Comparator.comparing(
                          UserGamePlaythrough::getCreatedAt,
                          Comparator.nullsFirst(Comparator.naturalOrder()))
                      .thenComparing(UserGamePlaythrough::getId))
              .map(UserGamePlaythrough::getId)
              .orElse(null);
    } else {
      expandedPlaythroughId = null;
    }
    model.addAttribute("activePlaythroughId", activePlaythroughId);
    model.addAttribute("expandedPlaythroughId", expandedPlaythroughId);
    Map<Long, Long> playthroughLogCounts = new HashMap<>();
    for (UserGamePlaythrough pt : gamePlaythroughs) {
      playthroughLogCounts.put(
          pt.getId(),
          playLogRepository.countByUserIdAndGameIdAndPlaythroughId(
              userId, game.getId(), pt.getId()));
    }
    model.addAttribute("playthroughLogCounts", playthroughLogCounts);
    List<UserGamePlaythrough> playthroughCreationOrder = new ArrayList<>(gamePlaythroughs);
    playthroughCreationOrder.sort(
        Comparator.comparing(
                UserGamePlaythrough::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
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
              userId, game.getId(), pt.getId(), PageRequest.of(0, plogBootstrapSize));
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
        userGamePlaythroughService.toBootstrapPayloadJson(userId, game.getId()));
    model.addAttribute("gamePlaythroughMax", UserGamePlaythrough.MAX_PER_USER_GAME);
    model.addAttribute("playthroughDifficultyOptions", PlaythroughDifficultyPresets.OPTIONS);
    model.addAttribute("reflectionTagSuggestionsMood", ReflectionTagCatalog.MOOD);
    model.addAttribute("reflectionTagSuggestionsGameplay", ReflectionTagCatalog.GAMEPLAY);
    model.addAttribute("reflectionTagSuggestionsMemory", ReflectionTagCatalog.MEMORY);
    model.addAttribute("title", game.getTitle());
    return "pages/collection-game";
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
    String prog =
        log.getSessionProgress() != null ? log.getSessionProgress().getDisplayLabel() : "";
    String exp =
        log.getSessionExperience() != null ? log.getSessionExperience().getDisplayLabel() : "";
    String progCode = log.getSessionProgress() != null ? log.getSessionProgress().name() : null;
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
        prog,
        exp,
        progCode,
        expCode,
        log.isCountsTowardLibraryPlaytime(),
        sessionStartedAtIso,
        sessionAwaitingEnd,
        log.getPlaythroughId());
  }

  @PostMapping("/{apiId}/ownership")
  public String updateOwnership(
      Authentication authentication,
      @PathVariable String apiId,
      @RequestParam(value = "platform", required = false) String platformParam,
      @RequestParam(value = "ownershipType", required = false) String ownershipParam,
      RedirectAttributes redirectAttributes) {
    String userId = LibraryUserIds.require(authentication);
    Optional<OwnedGame> owned = collectionLookup.findOwnedGame(userId, apiId);
    if (owned.isEmpty()) {
      return "redirect:/";
    }
    String key = owned.get().apiIdKey();
    com.cocoding.playstate.model.Game game = owned.get().game();
    if (hasActivePlaySessionAwaitingEnd(userId, game.getId())) {
      redirectAttributes.addFlashAttribute("activeSessionBlocksEdits", true);
      return "redirect:/collection/" + key;
    }
    UserGame ug = owned.get().userGame();
    List<String> allowedPlatforms = ownershipPlatformChoices(game, ug);

    if (platformParam == null || platformParam.isBlank()) {
      redirectAttributes.addFlashAttribute("ownershipError", "Choose a platform.");
      return "redirect:/collection/" + key;
    }
    String canonicalPlatform = resolveCanonicalPlatformName(platformParam, allowedPlatforms);
    if (canonicalPlatform == null) {
      redirectAttributes.addFlashAttribute(
          "ownershipError", "Choose a platform listed for this game.");
      return "redirect:/collection/" + key;
    }
    ug.setPlatform(canonicalPlatform);

    if (ownershipParam == null || ownershipParam.isBlank()) {
      ug.setOwnershipType(null);
    } else {
      try {
        ug.setOwnershipType(OwnershipType.valueOf(ownershipParam.trim()));
      } catch (IllegalArgumentException ex) {
        redirectAttributes.addFlashAttribute("ownershipError", "Choose a valid ownership type.");
        return "redirect:/collection/" + key;
      }
    }

    userGameRepository.save(ug);
    redirectAttributes.addFlashAttribute("ownershipSaved", true);
    return "redirect:/collection/" + key;
  }

  private record PlayLogFormParsed(
      boolean updateLibraryPlaytime,
      boolean updatePlaythroughPlaytime,
      String trimmedNote,
      LocalDateTime playedAt,
      Integer duration,
      boolean spoilers,
      PlayLogSessionProgress sessionProgress,
      PlayLogSessionExperience sessionExperience,
      LocalDateTime sessionStartedAt) {}

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
      String sessionProgressRaw,
      String sessionExperienceRaw,
      String updateLibraryPlaytimeRaw,
      String updatePlaythroughPlaytimeRaw,
      LocalDateTime playedAtForHoursMode,
      PlayLog existingLog,
      RedirectAttributes redirectAttributes) {
    boolean updateLibraryPlaytime = true;
    boolean updatePlaythroughPlaytime = true;

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
        LocalDateTime mergeStart =
            existingLog == null ? null : sessionStartForRangeMerge(existingLog);
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

    PlayLogSessionProgress sessionProgress = PlayLogSessionProgress.fromParam(sessionProgressRaw);
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
            updateLibraryPlaytime,
            updatePlaythroughPlaytime,
            trimmedNote,
            playedAt,
            duration,
            spoilers,
            sessionProgress,
            sessionExperience,
            sessionStartedAtStorage));
  }

  /** Open session row: start time saved, duration not set yet (user has not finished logging). */
  private static boolean isPlaySessionAwaitingEnd(PlayLog log) {
    return log != null && log.getSessionStartedAt() != null && log.getDurationMinutes() == null;
  }

  private static LocalDateTime sessionStartForRangeMerge(PlayLog log) {
    if (log.getSessionStartedAt() != null) {
      return log.getSessionStartedAt();
    }
    return null;
  }

  private static boolean anyNonBlank(String... values) {
    for (String s : values) {
      if (s != null && !s.isBlank()) {
        return true;
      }
    }
    return false;
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
      @RequestParam(value = "sessionProgress", required = false) String sessionProgressRaw,
      @RequestParam(value = "sessionExperience", required = false) String sessionExperienceRaw,
      @RequestParam(value = "redirectTo", required = false) String redirectTo,
      @RequestParam(value = "updateLibraryPlaytime", required = false)
          String updateLibraryPlaytimeRaw,
      @RequestParam(value = "updatePlaythroughPlaytime", required = false)
          String updatePlaythroughPlaytimeRaw,
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
            sessionProgressRaw,
            sessionExperienceRaw,
            updateLibraryPlaytimeRaw,
            updatePlaythroughPlaytimeRaw,
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
            f.sessionProgress(),
            f.sessionExperience());
    log.setSessionStartedAt(f.sessionStartedAt());
    applyCountsTowardLibraryMinutes(log, f);
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
        userId, game.getId(), f, null, null, log, parseAbsolutePlaythroughManualTotal(request));
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
      @RequestParam(value = "sessionProgress", required = false) String sessionProgressRaw,
      @RequestParam(value = "sessionExperience", required = false) String sessionExperienceRaw,
      @RequestParam(value = "redirectTo", required = false) String redirectTo,
      @RequestParam(value = "updateLibraryPlaytime", required = false)
          String updateLibraryPlaytimeRaw,
      @RequestParam(value = "updatePlaythroughPlaytime", required = false)
          String updatePlaythroughPlaytimeRaw,
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
            sessionProgressRaw,
            sessionExperienceRaw,
            updateLibraryPlaytimeRaw,
            updatePlaythroughPlaytimeRaw,
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
    log.setSessionProgress(f.sessionProgress());
    log.setSessionExperience(f.sessionExperience());
    applyCountsTowardLibraryMinutes(log, f);
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
        f,
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
    UserGame ugTouch = ownedDel.get().userGame();
    ugTouch.setUpdatedAt(LocalDateTime.now());
    userGameRepository.save(ugTouch);
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

  private static void applyCountsTowardLibraryMinutes(PlayLog log, PlayLogFormParsed f) {
    log.setCountsTowardLibraryPlaytime(true);
  }

  private void maybeApplyPlaythroughPlaytimeAfterLogSave(
      String userId,
      Long gameId,
      PlayLogFormParsed f,
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

  private record PlayLogSessionRange(
      LocalDateTime sessionStart, LocalDateTime sessionEnd, Integer durationMinutes) {}

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
      /* Same clock minute as start (e.g. HH:mm fields) — sub-minute span stored as 0, clamped when ending open session. */
      return new PlayLogSessionRange(sessionStart, end, validDurationMinutes(0));
    }
    long minsLong = ChronoUnit.MINUTES.between(sessionStart, end);
    if (minsLong > 60L * 24 * 7) {
      return null;
    }
    int mins = (int) minsLong;
    return new PlayLogSessionRange(sessionStart, end, validDurationMinutes(mins));
  }

  private static String lastPlayedRelativeLabel(LocalDateTime playedAt) {
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

  private static String formatLastPlayedCalendarLine(LocalDateTime playedAt) {
    if (playedAt == null) {
      return null;
    }
    return playedAt
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK));
  }

  private static boolean anyBlank(String... values) {
    for (String s : values) {
      if (s == null || s.isBlank()) {
        return true;
      }
    }
    return false;
  }

  @GetMapping(value = "/log-play/games", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public List<CollectionGameForLogDto> logPlayGames(Authentication authentication) {
    String userId = LibraryUserIds.require(authentication);
    List<UserGame> userGames = userGameRepository.findByUserId(userId);
    List<CollectionGameForLogDto> playing = new ArrayList<>();
    List<CollectionGameForLogDto> other = new ArrayList<>();
    for (UserGame ug : userGames) {
      if (ug.getStatus() == GameStatus.DROPPED) {
        continue;
      }
      com.cocoding.playstate.model.Game g = ug.getGame();
      if (g == null) {
        continue;
      }
      String apiId = g.getApiId();
      if (apiId == null || apiId.isBlank()) {
        continue;
      }
      String title = g.getTitle() != null ? g.getTitle() : "";
      String imageUrl = g.getImageUrl() != null ? g.getImageUrl() : "";
      String plat = ug.getPlatform();
      if (plat != null) {
        plat = plat.trim();
        if (plat.isEmpty()) {
          plat = null;
        }
      }
      long sumL = playLogRepository.sumDurationMinutesByUserIdAndGameId(userId, g.getId());
      int logSum = (int) Math.min(sumL, Integer.MAX_VALUE);
      int displayPlayMinutes = UserGame.computeDisplayPlayMinutes(ug, logSum);
      Page<PlayLog> latestPicker =
          playLogRepository.findByUserIdAndGameIdOrderByPlayedAtDescIdDesc(
              userId, g.getId(), PageRequest.of(0, 1));
      LocalDateTime lastPlayedPicker =
          latestPicker.isEmpty() ? null : latestPicker.getContent().get(0).getPlayedAt();
      boolean hasPlayLogs = lastPlayedPicker != null;
      String lastPlayedRelative = lastPlayedRelativeLabel(lastPlayedPicker);
      long playSessionCount = playLogRepository.countByUserIdAndGameId(userId, g.getId());
      String lastPlayedCalendarLine = formatLastPlayedCalendarLine(lastPlayedPicker);
      String totalPlayTimeLabel = playDurationFormat.forRecordTotalPlaytimeRead(displayPlayMinutes);
      long ptCount = userGamePlaythroughService.countForGame(userId, g.getId());
      int playthroughCount = (int) Math.min(ptCount, Integer.MAX_VALUE);
      CollectionGameForLogDto dto =
          new CollectionGameForLogDto(
              apiId,
              title,
              imageUrl,
              plat,
              displayPlayMinutes,
              hasPlayLogs,
              lastPlayedRelative,
              playSessionCount,
              lastPlayedCalendarLine,
              totalPlayTimeLabel,
              playthroughCount);
      if (ug.getStatus() == GameStatus.PLAYING) {
        playing.add(dto);
      } else {
        other.add(dto);
      }
    }
    Comparator<CollectionGameForLogDto> byTitle =
        Comparator.comparing(CollectionGameForLogDto::title, String.CASE_INSENSITIVE_ORDER);
    playing.sort(byTitle);
    other.sort(byTitle);
    List<CollectionGameForLogDto> out = new ArrayList<>(playing.size() + other.size());
    out.addAll(playing);
    out.addAll(other);
    return out;
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

  @PostMapping("/{apiId}/playthrough/seed")
  @Transactional
  public String seedFirstPlaythrough(
      Authentication authentication,
      @PathVariable String apiId,
      RedirectAttributes redirectAttributes) {
    String userId = LibraryUserIds.require(authentication);
    Optional<OwnedGame> owned = collectionLookup.findOwnedGame(userId, apiId);
    if (owned.isEmpty()) {
      return "redirect:/";
    }
    String key = owned.get().apiIdKey();
    com.cocoding.playstate.model.Game game = owned.get().game();
    if (hasActivePlaySessionAwaitingEnd(userId, game.getId())) {
      redirectAttributes.addFlashAttribute("activeSessionBlocksEdits", true);
      return "redirect:/collection/" + key;
    }
    userGamePlaythroughService.createDefaultPlaythroughIfNone(userId, game.getId());
    redirectAttributes.addFlashAttribute("personalReopen", "history");
    return "redirect:/collection/" + key;
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
      @RequestParam(value = "playthroughRunType", required = false) String playthroughRunTypeParam,
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
      GameStatus status;
      try {
        status = GameStatus.valueOf(statusParam != null ? statusParam.trim() : "");
      } catch (IllegalArgumentException ex) {
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
        boolean runStateLockedInPlaySummary =
            currentPt.isPresent()
                && currentPt.get().isCurrent()
                && currentPt.get().getProgressStatus() == PlaythroughProgressStatus.PLAYING;
        if (!runStateLockedInPlaySummary) {
          try {
            userGamePlaythroughService.updateCurrentPlaythroughModeAndRunType(
                userId, game.getId(), playthroughProgressModeParam, playthroughRunTypeParam);
          } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("personalError", ex.getMessage());
            redirectAttributes.addFlashAttribute("personalReopen", "core");
            return "redirect:/collection/" + key;
          }
        }
      }

      if (platformPersonalParam != null && !platformPersonalParam.isBlank()) {
        List<String> allowedPlatforms = ownershipPlatformChoices(game, ug);
        String canonicalPlatform =
            resolveCanonicalPlatformName(platformPersonalParam, allowedPlatforms);
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
          try {
            ug.setOwnershipType(OwnershipType.valueOf(ownershipTypePersonalParam.trim()));
          } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("personalError", "Choose a valid ownership type.");
            redirectAttributes.addFlashAttribute("personalReopen", "core");
            return "redirect:/collection/" + key;
          }
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

  private List<String> ownershipPlatformChoices(
      com.cocoding.playstate.model.Game game, UserGame ug) {
    LinkedHashSet<String> set = new LinkedHashSet<>();
    List<String> storedOptions = game.getPlatformOptionsList();
    if (storedOptions != null) {
      for (String p : storedOptions) {
        if (p != null && !p.isBlank()) {
          set.add(p.trim());
        }
      }
    }
    if (ug.getPlatform() != null && !ug.getPlatform().isBlank()) {
      set.add(ug.getPlatform().trim());
    }
    if (set.isEmpty()) {
      return new ArrayList<>(PlatformCatalog.allIgdbPlatformNamesSorted());
    }
    ArrayList<String> sorted = new ArrayList<>(set);
    sorted.sort(String.CASE_INSENSITIVE_ORDER);
    return sorted;
  }

  private static String resolveCanonicalPlatformName(String submitted, List<String> allowed) {
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
