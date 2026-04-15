package com.cocoding.playstate.controller;

import com.cocoding.playstate.domain.enums.GameStatus;
import com.cocoding.playstate.domain.enums.WhyPlaying;
import com.cocoding.playstate.dto.collection.CollectionCardView;
import com.cocoding.playstate.dto.collection.CollectionGameForLogDto;
import com.cocoding.playstate.dto.collection.CollectionSectionView;
import com.cocoding.playstate.format.LastPlayedFormat;
import com.cocoding.playstate.format.PlayDurationFormat;
import com.cocoding.playstate.model.UserGame;
import com.cocoding.playstate.repository.GameRepository;
import com.cocoding.playstate.repository.PlayLogRepository;
import com.cocoding.playstate.repository.UserGameRepository;
import com.cocoding.playstate.security.LibraryUserIds;
import com.cocoding.playstate.service.UserGamePlaythroughService;
import com.cocoding.playstate.web.view.CollectionPlatformHeaderStyle;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/collection")
public class CollectionController {

  private final UserGameRepository userGameRepository;
  private final GameRepository gameRepository;
  private final PlayLogRepository playLogRepository;
  private final PlayDurationFormat playDurationFormat;
  private final UserGamePlaythroughService userGamePlaythroughService;

  private static final List<GameStatus> COLLECTION_STATUS_ORDER =
      Arrays.asList(
          GameStatus.PLAYING,
          GameStatus.PAUSED,
          GameStatus.FINISHED,
          GameStatus.NOT_PLAYING,
          GameStatus.DROPPED);

  public CollectionController(
      UserGameRepository userGameRepository,
      GameRepository gameRepository,
      PlayLogRepository playLogRepository,
      PlayDurationFormat playDurationFormat,
      UserGamePlaythroughService userGamePlaythroughService) {
    this.userGameRepository = userGameRepository;
    this.gameRepository = gameRepository;
    this.playLogRepository = playLogRepository;
    this.playDurationFormat = playDurationFormat;
    this.userGamePlaythroughService = userGamePlaythroughService;
  }

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
    List<Long> gameIds = userGames.stream().map(UserGame::getGameId).distinct().toList();
    Map<Long, com.cocoding.playstate.model.Game> gamesById = loadGamesById(gameIds);
    Map<Long, Long> logMinutesByGameId =
        toLongMap(playLogRepository.sumDurationMinutesByUserIdAndGameIdIn(userId, gameIds));
    Map<Long, Long> logSessionCountByGameId =
        toLongMap(playLogRepository.countByUserIdAndGameIdIn(userId, gameIds));
    Map<Long, LocalDateTime> latestPlayedAtByGameId =
        toLatestPlayedAtMap(playLogRepository.latestPlayedAtByUserIdAndGameIdIn(userId, gameIds));
    Map<Long, Long> playthroughCountByGameId =
        userGamePlaythroughService.countForGames(userId, gameIds);

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
      com.cocoding.playstate.model.Game g = gamesById.get(ug.getGameId());
      if (g == null) {
        continue;
      }
      String platformStr = ug.getPlatform() != null ? ug.getPlatform().trim() : "";
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
      long logSumCardLong = logMinutesByGameId.getOrDefault(g.getId(), 0L);
      int logSumCard = (int) Math.min(logSumCardLong, Integer.MAX_VALUE);
      int displayPlayMinutesCard = UserGame.computeDisplayPlayMinutes(ug, logSumCard);
      LocalDateTime lastPlayedCard = latestPlayedAtByGameId.get(g.getId());
      boolean logModalHasPlayLogs = lastPlayedCard != null;
      String logModalLastPlayedRelative = LastPlayedFormat.relativeLabel(lastPlayedCard);
      long logModalSessionCount = logSessionCountByGameId.getOrDefault(g.getId(), 0L);
      String logModalLastPlayedCalendarLine = LastPlayedFormat.calendarLine(lastPlayedCard);
      String logModalTotalPlayTimeLabel =
          playDurationFormat.forRecordTotalPlaytimeRead(displayPlayMinutesCard);
      long ptCountCard = playthroughCountByGameId.getOrDefault(g.getId(), 0L);
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

  @GetMapping(value = "/log-play/games", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public List<CollectionGameForLogDto> logPlayGames(Authentication authentication) {
    String userId = LibraryUserIds.require(authentication);
    List<UserGame> userGames = userGameRepository.findByUserId(userId);
    List<Long> gameIds = userGames.stream().map(UserGame::getGameId).distinct().toList();
    Map<Long, com.cocoding.playstate.model.Game> gamesById = loadGamesById(gameIds);
    Map<Long, Long> logMinutesByGameId =
        toLongMap(playLogRepository.sumDurationMinutesByUserIdAndGameIdIn(userId, gameIds));
    Map<Long, Long> logSessionCountByGameId =
        toLongMap(playLogRepository.countByUserIdAndGameIdIn(userId, gameIds));
    Map<Long, LocalDateTime> latestPlayedAtByGameId =
        toLatestPlayedAtMap(playLogRepository.latestPlayedAtByUserIdAndGameIdIn(userId, gameIds));
    Map<Long, Long> playthroughCountByGameId =
        userGamePlaythroughService.countForGames(userId, gameIds);
    List<CollectionGameForLogDto> playing = new ArrayList<>();
    List<CollectionGameForLogDto> other = new ArrayList<>();
    for (UserGame ug : userGames) {
      if (ug.getStatus() == GameStatus.DROPPED) {
        continue;
      }
      com.cocoding.playstate.model.Game g = gamesById.get(ug.getGameId());
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
      long sumL = logMinutesByGameId.getOrDefault(g.getId(), 0L);
      int logSum = (int) Math.min(sumL, Integer.MAX_VALUE);
      int displayPlayMinutes = UserGame.computeDisplayPlayMinutes(ug, logSum);
      LocalDateTime lastPlayedPicker = latestPlayedAtByGameId.get(g.getId());
      boolean hasPlayLogs = lastPlayedPicker != null;
      String lastPlayedRelative = LastPlayedFormat.relativeLabel(lastPlayedPicker);
      long playSessionCount = logSessionCountByGameId.getOrDefault(g.getId(), 0L);
      String lastPlayedCalendarLine = LastPlayedFormat.calendarLine(lastPlayedPicker);
      String totalPlayTimeLabel = playDurationFormat.forRecordTotalPlaytimeRead(displayPlayMinutes);
      long ptCount = playthroughCountByGameId.getOrDefault(g.getId(), 0L);
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

  private Map<Long, com.cocoding.playstate.model.Game> loadGamesById(List<Long> gameIds) {
    if (gameIds == null || gameIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, com.cocoding.playstate.model.Game> gamesById = new HashMap<>();
    for (com.cocoding.playstate.model.Game game : gameRepository.findAllById(gameIds)) {
      gamesById.put(game.getId(), game);
    }
    return gamesById;
  }

  private static Map<Long, Long> toLongMap(List<Object[]> rows) {
    Map<Long, Long> out = new HashMap<>();
    for (Object[] row : rows) {
      out.put((Long) row[0], (Long) row[1]);
    }
    return out;
  }

  private static Map<Long, LocalDateTime> toLatestPlayedAtMap(List<Object[]> rows) {
    Map<Long, LocalDateTime> out = new HashMap<>();
    for (Object[] row : rows) {
      out.put((Long) row[0], (LocalDateTime) row[1]);
    }
    return out;
  }
}
