package com.cocoding.playstate.controller;

import com.cocoding.playstate.dto.home.HomeDashboardDtos.DashboardHeroCopy;
import com.cocoding.playstate.dto.home.HomeDashboardDtos.HomeHeroQuickRow;
import com.cocoding.playstate.dto.home.HomeDashboardDtos.HomeHeroStats;
import com.cocoding.playstate.dto.home.HomeDashboardDtos.HomeInProgressTile;
import com.cocoding.playstate.dto.home.HomeDashboardDtos.HomePreviewTile;
import com.cocoding.playstate.dto.home.HomeDashboardDtos.HomeSessionRow;
import com.cocoding.playstate.format.PlayDurationFormat;
import com.cocoding.playstate.model.GameStatus;
import com.cocoding.playstate.model.PlayLog;
import com.cocoding.playstate.model.UserGame;
import com.cocoding.playstate.model.WhyPlaying;
import com.cocoding.playstate.repository.GameRepository;
import com.cocoding.playstate.repository.PlayLogRepository;
import com.cocoding.playstate.repository.UserAccountRepository;
import com.cocoding.playstate.repository.UserGameRepository;
import com.cocoding.playstate.security.LibraryUserIds;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

  private static final int TRY_NEXT_TILE_COUNT = 8;

  private static final int ACTIVITY_PLAYING_DISPLAY_LIMIT = 5;

  private static final int ACTIVITY_RECENT_PLAY_DISPLAY_LIMIT = 7;

  private static final int HOME_RECENT_SESSION_NOTE_PREVIEW_MAX = 120;

  private static final int HERO_RECENTLY_PLAYED_GAME_LIMIT = 3;

  private final UserGameRepository userGameRepository;
  private final PlayLogRepository playLogRepository;
  private final GameRepository gameRepository;
  private final UserAccountRepository userAccountRepository;
  private final PlayDurationFormat playDurationFormat;

  public HomeController(
      UserGameRepository userGameRepository,
      PlayLogRepository playLogRepository,
      GameRepository gameRepository,
      UserAccountRepository userAccountRepository,
      PlayDurationFormat playDurationFormat) {
    this.userGameRepository = userGameRepository;
    this.playLogRepository = playLogRepository;
    this.gameRepository = gameRepository;
    this.userAccountRepository = userAccountRepository;
    this.playDurationFormat = playDurationFormat;
  }

  @GetMapping("/")
  @Transactional(readOnly = true)
  public String homePage(Authentication authentication, Model model) {
    boolean dashboard = isLoggedIn(authentication);
    model.addAttribute("title", "Home");
    model.addAttribute("homeDashboard", dashboard);
    if (dashboard) {
      populateDashboard(model, authentication);
    } else {
      model.addAttribute("siteUserCount", userAccountRepository.count());
      model.addAttribute("siteSessionsLogged", playLogRepository.count());
    }
    return "pages/home";
  }

  private static boolean isLoggedIn(Authentication authentication) {
    return authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken);
  }

  private void populateDashboard(Model model, Authentication authentication) {
    String userId = LibraryUserIds.require(authentication);
    List<UserGame> library = userGameRepository.findByUserId(userId);
    Map<Long, UserGame> byGameId = new HashMap<>();
    for (UserGame ug : library) {
      byGameId.put(ug.getGameId(), ug);
    }

    List<PlayLog> recentLogs = playLogRepository.findTop40ByUserIdOrderByPlayedAtDescIdDesc(userId);

    long totalPlayLogs = playLogRepository.countByUserId(userId);
    List<HomeSessionRow> sessions = new ArrayList<>();
    for (PlayLog pl : recentLogs) {
      if (sessions.size() >= ACTIVITY_RECENT_PLAY_DISPLAY_LIMIT) {
        break;
      }
      UserGame ug = byGameId.get(pl.getGameId());
      com.cocoding.playstate.model.Game g =
          ug != null ? ug.getGame() : gameRepository.findById(pl.getGameId()).orElse(null);
      if (g == null) {
        continue;
      }
      String rawNote = pl.getNote();
      if (rawNote != null && rawNote.isBlank()) {
        rawNote = null;
      }
      boolean spoilers = pl.isNoteContainsSpoilers();
      String noteDisplay = null;
      boolean noteTruncated = false;
      String anchorTitle = null;
      if (rawNote != null) {
        String t = rawNote.trim();
        if (spoilers) {
          noteDisplay = "Spoiler Note";
          anchorTitle = "Spoiler note — open the game page to read the full text.";
        } else if (t.length() > HOME_RECENT_SESSION_NOTE_PREVIEW_MAX) {
          String stem = t.substring(0, HOME_RECENT_SESSION_NOTE_PREVIEW_MAX).trim();
          noteDisplay = stem;
          noteTruncated = true;
          anchorTitle = "Preview is shortened. Open the game page for the full session note.";
        } else {
          noteDisplay = t;
        }
      }
      sessions.add(
          new HomeSessionRow(
              g.getTitle(),
              g.getApiId(),
              g.getImageUrl(),
              noteDisplay,
              pl.getPlayedAt(),
              pl.getDurationMinutes(),
              spoilers,
              noteTruncated,
              anchorTitle));
    }
    int recentPlayOverflowCount =
        (int) Math.max(0L, totalPlayLogs - ACTIVITY_RECENT_PLAY_DISPLAY_LIMIT);

    long playingTotal = library.stream().filter(ug -> ug.getStatus() == GameStatus.PLAYING).count();

    Map<Long, LocalDateTime> latestPlayedByGameId = new HashMap<>();
    for (PlayLog pl : recentLogs) {
      latestPlayedByGameId.putIfAbsent(pl.getGameId(), pl.getPlayedAt());
    }

    List<HomeInProgressTile> inProgress = new ArrayList<>();
    library.stream()
        .filter(ug -> ug.getStatus() == GameStatus.PLAYING)
        .sorted(
            Comparator.comparing(
                    UserGame::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(
                    UserGame::getProgressUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(
                    UserGame::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed())
        .limit(ACTIVITY_PLAYING_DISPLAY_LIMIT)
        .forEach(
            ug -> {
              com.cocoding.playstate.model.Game g = ug.getGame();
              if (g != null) {
                String plat = ug.getPlatform();
                if (plat != null && plat.isBlank()) {
                  plat = null;
                }
                List<String> intentBadges = new ArrayList<>();
                List<String> intentData = new ArrayList<>();
                for (WhyPlaying p : ug.getWhyPlayings()) {
                  intentBadges.add(p.getBadgeShort());
                  intentData.add(p.getTitle());
                }
                LocalDateTime lastPlayed = latestPlayedByGameId.get(ug.getGameId());
                inProgress.add(
                    new HomeInProgressTile(
                        g.getTitle(),
                        g.getApiId(),
                        g.getImageUrl(),
                        plat,
                        List.copyOf(intentBadges),
                        List.copyOf(intentData),
                        formatPlayingTileRecencyChip(lastPlayed),
                        formatPlayedAgoLabel(lastPlayed)));
              }
            });

    int playingTilesOverflowCount = (int) Math.max(0, playingTotal - inProgress.size());

    List<HomePreviewTile> tryNext = buildTryNextTiles(library);

    int gamesInCollection = (int) library.stream().filter(ug -> ug.getGame() != null).count();
    int playingNow =
        (int) library.stream().filter(ug -> ug.getStatus() == GameStatus.PLAYING).count();
    int finished =
        (int) library.stream().filter(ug -> ug.getStatus() == GameStatus.FINISHED).count();
    long sessionsLogged = playLogRepository.countByUserId(userId);
    long libraryTotalPlayMinutes = sumLibraryDisplayPlayMinutes(userId, library);
    String avgRatingDisplay = computeAvgRatingDisplay(library);
    String playTimeDisplay = playDurationFormat.forLibrarySumMinutes(libraryTotalPlayMinutes);
    String playTimeHoursDisplay = computeHoursOnlyPlayTimeDisplay(libraryTotalPlayMinutes);
    model.addAttribute(
        "heroStats",
        new HomeHeroStats(
            gamesInCollection,
            sessionsLogged,
            playingNow,
            finished,
            avgRatingDisplay,
            playTimeDisplay,
            playTimeHoursDisplay));

    model.addAttribute("dashboardFirstName", formatDashboardFirstName(authentication));
    model.addAttribute(
        "heroQuickRows", buildRecentlyPlayedHeroRows(recentLogs, byGameId, gameRepository));
    model.addAttribute(
        "dashHeroCopy",
        buildDashboardHeroCopy(
            gamesInCollection, sessionsLogged, playingNow, recentLogs, byGameId));

    model.addAttribute("recentSessions", sessions);
    model.addAttribute("recentPlayOverflowCount", recentPlayOverflowCount);
    model.addAttribute("inProgressTiles", inProgress);
    model.addAttribute("playingTilesOverflowCount", playingTilesOverflowCount);
    model.addAttribute("tryNextTiles", tryNext);
  }

  private DashboardHeroCopy buildDashboardHeroCopy(
      int gamesInCollection,
      long sessionsLogged,
      int playingNow,
      List<PlayLog> recentLogs,
      Map<Long, UserGame> byGameId) {
    if (gamesInCollection == 0) {
      return new DashboardHeroCopy(DashboardHeroCopy.Phase.WELCOME_EMPTY, null, null);
    }
    if (sessionsLogged == 0) {
      return new DashboardHeroCopy(DashboardHeroCopy.Phase.COLLECTION_NO_SESSIONS, null, null);
    }
    if (playingNow == 0) {
      return new DashboardHeroCopy(DashboardHeroCopy.Phase.SESSIONS_NO_ACTIVE_GAME, null, null);
    }
    PlayLog latest = recentLogs.isEmpty() ? null : recentLogs.get(0);
    if (latest == null) {
      return new DashboardHeroCopy(DashboardHeroCopy.Phase.ACTIVE_LAST_PLAYED, null, null);
    }
    UserGame ug = byGameId.get(latest.getGameId());
    com.cocoding.playstate.model.Game g =
        ug != null ? ug.getGame() : gameRepository.findById(latest.getGameId()).orElse(null);
    if (g == null) {
      return new DashboardHeroCopy(DashboardHeroCopy.Phase.ACTIVE_LAST_PLAYED, null, null);
    }
    String title = g.getTitle();
    String ago = formatLastPlayedWhenDetail(latest.getPlayedAt());
    return new DashboardHeroCopy(DashboardHeroCopy.Phase.ACTIVE_LAST_PLAYED, title, ago);
  }

  private static String formatLastPlayedWhenDetail(LocalDateTime playedAt) {
    if (playedAt == null) {
      return null;
    }
    ZoneId zone = ZoneId.systemDefault();
    LocalDate playedDay = playedAt.atZone(zone).toLocalDate();
    LocalDate today = LocalDate.now(zone);
    long days = ChronoUnit.DAYS.between(playedDay, today);
    if (days <= 0) {
      return "today";
    }
    if (days == 1) {
      return "1 day ago";
    }
    return days + " days ago";
  }

  private static String computeHoursOnlyPlayTimeDisplay(long totalMinutes) {
    long hours = Math.max(0L, totalMinutes / 60L);
    return hours + "h";
  }

  private static String formatPlayingTileRecencyChip(LocalDateTime playedAt) {
    if (playedAt == null) {
      return null;
    }
    ZoneId zone = ZoneId.systemDefault();
    LocalDate playedDay = playedAt.atZone(zone).toLocalDate();
    LocalDate today = LocalDate.now(zone);
    long days = ChronoUnit.DAYS.between(playedDay, today);
    if (days <= 0) {
      return "TODAY";
    }
    return days + "DAY";
  }

  private static String formatPlayedAgoLabel(LocalDateTime playedAt) {
    if (playedAt == null) {
      return null;
    }
    String detail = formatLastPlayedWhenDetail(playedAt);
    if (detail == null) {
      return null;
    }
    if ("today".equals(detail)) {
      return "Played today";
    }
    return "Played " + detail;
  }

  private static String formatDashboardFirstName(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return "there";
    }
    String name = authentication.getName();
    if (name == null || name.isBlank()) {
      return "there";
    }
    String base = name.contains("@") ? name.substring(0, name.indexOf('@')) : name;
    base = base.replace('.', ' ').replace('_', ' ').trim();
    if (base.isEmpty()) {
      return "there";
    }
    String[] parts = base.split("\\s+");
    String first = parts[0];
    if (first.length() == 1) {
      return first.toUpperCase(Locale.ROOT);
    }
    return first.substring(0, 1).toUpperCase(Locale.ROOT)
        + first.substring(1).toLowerCase(Locale.ROOT);
  }

  private long sumLibraryDisplayPlayMinutes(String userId, List<UserGame> library) {
    long sum = 0;
    for (UserGame ug : library) {
      if (ug.getGame() == null) {
        continue;
      }
      long logSum = playLogRepository.sumDurationMinutesByUserIdAndGameId(userId, ug.getGameId());
      int logSumInt = logSum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) logSum;
      sum += UserGame.computeDisplayPlayMinutes(ug, logSumInt);
    }
    return sum;
  }

  private static String computeAvgRatingDisplay(List<UserGame> library) {
    List<Integer> ratings =
        library.stream().map(UserGame::getRating).filter(r -> r != null && r > 0).toList();
    if (ratings.isEmpty()) {
      return "—";
    }
    double avg = ratings.stream().mapToInt(Integer::intValue).average().orElse(0);
    return String.format(Locale.US, "%.1f", avg);
  }

  private static List<HomeHeroQuickRow> buildRecentlyPlayedHeroRows(
      List<PlayLog> recentLogs, Map<Long, UserGame> byGameId, GameRepository gameRepository) {
    List<Long> recentUniqueIds = new ArrayList<>();
    Set<Long> seenGameIds = new HashSet<>();
    for (PlayLog pl : recentLogs) {
      long gid = pl.getGameId();
      if (seenGameIds.contains(gid)) {
        continue;
      }
      UserGame ug = byGameId.get(gid);
      if (ug != null && ug.getStatus() == GameStatus.DROPPED) {
        continue;
      }
      seenGameIds.add(gid);
      recentUniqueIds.add(gid);
    }
    List<Long> playingIds = new ArrayList<>();
    List<Long> otherIds = new ArrayList<>();
    for (Long gid : recentUniqueIds) {
      UserGame ug = byGameId.get(gid);
      if (ug != null && ug.getStatus() == GameStatus.PLAYING) {
        playingIds.add(gid);
      } else {
        otherIds.add(gid);
      }
    }
    List<Long> pickOrder = new ArrayList<>(playingIds.size() + otherIds.size());
    pickOrder.addAll(playingIds);
    pickOrder.addAll(otherIds);

    List<HomeHeroQuickRow> out = new ArrayList<>();
    for (Long gid : pickOrder) {
      if (out.size() >= HERO_RECENTLY_PLAYED_GAME_LIMIT) {
        break;
      }
      UserGame ug = byGameId.get(gid);
      com.cocoding.playstate.model.Game g =
          ug != null ? ug.getGame() : gameRepository.findById(gid).orElse(null);
      if (g == null || g.getApiId() == null || g.getApiId().isBlank()) {
        continue;
      }
      out.add(ug != null ? toHeroQuickRow(ug) : toHeroQuickRowFromGame(g));
    }
    return out;
  }

  private static HomeHeroQuickRow toHeroQuickRow(UserGame ug) {
    com.cocoding.playstate.model.Game g = ug.getGame();
    if (g == null) {
      return new HomeHeroQuickRow("", "", "", "", "", "");
    }
    String intent = ug.getWhyPlayingBadgeSummary();
    if (intent == null || intent.isBlank()) {
      intent = "";
    }
    String platform = ug.getPlatform();
    if (platform == null || platform.isBlank()) {
      platform = "";
    }
    return new HomeHeroQuickRow(
        g.getTitle(),
        g.getApiId() != null ? g.getApiId() : "",
        g.getImageUrl() != null ? g.getImageUrl() : "",
        ug.getStatus().getDisplayName(),
        platform,
        intent);
  }

  private static HomeHeroQuickRow toHeroQuickRowFromGame(com.cocoding.playstate.model.Game g) {
    if (g == null) {
      return new HomeHeroQuickRow("", "", "", "", "", "");
    }
    return new HomeHeroQuickRow(
        g.getTitle(),
        g.getApiId() != null ? g.getApiId() : "",
        g.getImageUrl() != null ? g.getImageUrl() : "",
        "",
        "",
        "");
  }

  private static List<HomePreviewTile> buildTryNextTiles(List<UserGame> library) {
    List<UserGame> pool = new ArrayList<>();
    for (UserGame ug : library) {
      if (ug.getGame() == null) {
        continue;
      }
      GameStatus st = ug.getStatus();
      if (st != GameStatus.NOT_PLAYING && st != GameStatus.PAUSED) {
        continue;
      }
      pool.add(ug);
    }
    if (pool.isEmpty()) {
      return List.of();
    }
    Collections.shuffle(pool, ThreadLocalRandom.current());
    List<HomePreviewTile> out = new ArrayList<>();
    int n = Math.min(TRY_NEXT_TILE_COUNT, pool.size());
    for (int i = 0; i < n; i++) {
      UserGame ug = pool.get(i);
      com.cocoding.playstate.model.Game g = ug.getGame();
      out.add(
          new HomePreviewTile(
              g.getTitle(), g.getApiId(), g.getImageUrl(), ug.getStatus().getDisplayName()));
    }
    return out;
  }
}
