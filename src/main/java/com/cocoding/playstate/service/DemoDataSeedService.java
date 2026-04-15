package com.cocoding.playstate.service;

import com.cocoding.playstate.dto.search.SearchGameRow;
import com.cocoding.playstate.igdb.IgdbService;
import com.cocoding.playstate.model.CompletionType;
import com.cocoding.playstate.model.Game;
import com.cocoding.playstate.model.GameStatus;
import com.cocoding.playstate.model.OwnershipType;
import com.cocoding.playstate.model.PlayLog;
import com.cocoding.playstate.model.PlayLogSessionExperience;
import com.cocoding.playstate.model.PlayLogSessionProgress;
import com.cocoding.playstate.model.PlaythroughProgressStatus;
import com.cocoding.playstate.model.PlaythroughRunType;
import com.cocoding.playstate.model.UserAccount;
import com.cocoding.playstate.model.UserGame;
import com.cocoding.playstate.model.UserGamePlaythrough;
import com.cocoding.playstate.model.WhyPlaying;
import com.cocoding.playstate.repository.GameRepository;
import com.cocoding.playstate.repository.PlayLogRepository;
import com.cocoding.playstate.repository.UserAccountRepository;
import com.cocoding.playstate.repository.UserGamePlaythroughRepository;
import com.cocoding.playstate.repository.UserGameRepository;
import com.cocoding.playstate.util.ReflectionTagsJson;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.LongStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a shared demo account and a varied collection (playthroughs, notes, reviews, play logs)
 * using live IGDB payloads so covers and metadata match production API data.
 */
@Service
public class DemoDataSeedService {

  private static final Logger logger = LoggerFactory.getLogger(DemoDataSeedService.class);

  private static final long[] DEMO_GAME_IDS = {
    119133L, // Elden Ring
    194267L, // Baldur's Gate 3
    1877L, // Cyberpunk 2077
    13577L, // Stardew Valley
    18472L, // Hollow Knight
    1068L, // Portal 2
    19560L, // God of War (2018)
  };

  private static final List<String> DEMO_API_IDS =
      LongStream.of(DEMO_GAME_IDS).mapToObj(String::valueOf).toList();

  private final UserAccountRepository userAccountRepository;
  private final PasswordEncoder passwordEncoder;
  private final IgdbService igdbService;
  private final GameRepository gameRepository;
  private final UserGameRepository userGameRepository;
  private final UserGamePlaythroughRepository playthroughRepository;
  private final PlayLogRepository playLogRepository;
  private final GameEnrichmentService gameEnrichmentService;

  @Value("${demo.enabled:true}")
  private boolean demoEnabled;

  @Value("${demo.username:demo}")
  private String demoUsername;

  @Value("${demo.password:}")
  private String demoPassword;

  @Value("${demo.email:demo@example.com}")
  private String demoEmail;

  public DemoDataSeedService(
      UserAccountRepository userAccountRepository,
      PasswordEncoder passwordEncoder,
      IgdbService igdbService,
      GameRepository gameRepository,
      UserGameRepository userGameRepository,
      UserGamePlaythroughRepository playthroughRepository,
      PlayLogRepository playLogRepository,
      GameEnrichmentService gameEnrichmentService) {
    this.userAccountRepository = userAccountRepository;
    this.passwordEncoder = passwordEncoder;
    this.igdbService = igdbService;
    this.gameRepository = gameRepository;
    this.userGameRepository = userGameRepository;
    this.playthroughRepository = playthroughRepository;
    this.playLogRepository = playLogRepository;
    this.gameEnrichmentService = gameEnrichmentService;
  }

  @Transactional
  public void seedIfEnabled() {
    if (!demoEnabled) {
      return;
    }
    if (demoUsername == null
        || demoUsername.isBlank()
        || demoPassword == null
        || demoPassword.length() < 8) {
      logger.warn(
          "Demo seed skipped: configure demo.username and demo.password (min 8 characters).");
      return;
    }
    String username = demoUsername.trim().toLowerCase(Locale.ROOT);
    if (!ensureDemoUserAccount(username)) {
      return;
    }
    if (alreadySeeded(username)) {
      logger.debug("Demo collection already present; skipping seed.");
      return;
    }
    List<Map<String, Object>> rows = igdbService.fetchGamesByIgdbIds(DEMO_GAME_IDS);
    Map<Long, Map<String, Object>> byIgdbId = new HashMap<>();
    for (Map<String, Object> row : rows) {
      Object idObj = row.get("id");
      if (idObj instanceof Number n) {
        byIgdbId.put(n.longValue(), row);
      }
    }
    if (byIgdbId.isEmpty()) {
      logger.warn(
          "Demo seed skipped: IGDB returned no games (check IGDB_CLIENT_ID / IGDB_CLIENT_SECRET).");
      return;
    }
    for (long igdbId : DEMO_GAME_IDS) {
      Map<String, Object> raw = byIgdbId.get(igdbId);
      if (raw == null) {
        raw = igdbService.fetchGameSnapshot(igdbId);
      }
      if (raw == null || raw.isEmpty()) {
        logger.warn("Demo seed: IGDB had no data for game id {}", igdbId);
        continue;
      }
      SearchGameRow sgr = SearchGameRow.fromIgdbMap(raw);
      if (sgr.name == null || sgr.name.isBlank()) {
        continue;
      }
      String apiId = String.valueOf(sgr.id);
      Game game =
          gameRepository
              .findByApiId(apiId)
              .orElseGet(
                  () ->
                      gameRepository.save(
                          new Game(apiId, sgr.name.trim(), sgr.getCoverImageUrl())));
      game.setTitle(sgr.name.trim());
      if (sgr.getCoverImageUrl() != null) {
        game.setImageUrl(sgr.getCoverImageUrl());
      }
      gameRepository.save(game);
      gameEnrichmentService.enrichFromIgdbIfIncomplete(game);

      UserGame ug =
          userGameRepository
              .findByUserIdAndGameId(username, game.getId())
              .orElseGet(() -> new UserGame(username, game.getId()));
      ug.setPlatform(firstPlatformName(sgr));
      ug.setOwnershipType(OwnershipType.DIGITAL);
      applyGameProfile(igdbId, ug);
      ug.setProgressUpdatedAt(LocalDateTime.now().minusDays(1));
      userGameRepository.save(ug);

      seedPlaythroughsAndLogs(username, game.getId(), igdbId);
    }
    logger.info("Demo library seeded for user '{}'.", username);
  }

  private boolean ensureDemoUserAccount(String username) {
    if (userAccountRepository.existsByUsernameIgnoreCase(username)) {
      return true;
    }
    String normalizedEmail = null;
    if (demoEmail != null && !demoEmail.isBlank()) {
      normalizedEmail = demoEmail.trim().toLowerCase(Locale.ROOT);
      if (userAccountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
        logger.warn(
            "Demo user '{}' not created because email '{}' already exists.",
            username,
            normalizedEmail);
        return false;
      }
    }
    UserAccount account = new UserAccount();
    account.setUsername(username);
    account.setPasswordHash(passwordEncoder.encode(demoPassword));
    if (normalizedEmail != null) {
      account.setEmail(normalizedEmail);
    }
    try {
      // Force insert now so uniqueness violations are caught here, not at transaction commit.
      userAccountRepository.saveAndFlush(account);
      logger.info("Created demo user '{}'.", username);
      return true;
    } catch (DataIntegrityViolationException e) {
      // Another startup thread/instance may have created the same username/email first.
      logger.warn("Demo user '{}' not created because username/email already exists.", username);
      return userAccountRepository.existsByUsernameIgnoreCase(username);
    }
  }

  private boolean alreadySeeded(String userId) {
    return userGameRepository.findByUserIdAndGame_ApiIdIn(userId, DEMO_API_IDS).size()
        >= DEMO_API_IDS.size();
  }

  private static String firstPlatformName(SearchGameRow row) {
    if (row.platforms.isEmpty()) {
      return "PC (Microsoft Windows)";
    }
    String n = row.platforms.get(0).name;
    return n != null && !n.isBlank() ? n : "PC (Microsoft Windows)";
  }

  private void applyGameProfile(long igdbId, UserGame ug) {
    switch ((int) igdbId) {
      case 119133 -> applyElden(ug);
      case 194267 -> applyBg3(ug);
      case 1877 -> applyCyberpunk(ug);
      case 13577 -> applyStardew(ug);
      case 18472 -> applyHollowKnight(ug);
      case 1068 -> applyPortal2(ug);
      case 19560 -> applyGodOfWar(ug);
      default -> {
        ug.setStatus(GameStatus.PLAYING);
        ug.setNotes("Demo entry — customize me.");
      }
    }
  }

  private static void applyElden(UserGame ug) {
    ug.setStatus(GameStatus.PLAYING);
    ug.setCompletionType(CompletionType.NOT_COMPLETED);
    ug.setNotes(
        "Taking it slow: I clear every cave before moving the story. The world feels endless in the"
            + " best way.");
    ug.setWhyPlaying(WhyPlaying.EXPLORE);
    ug.setProgressPercent(62);
    ug.setProgressLabel("Approaching Leyndell");
    ug.setDifficulty("Normal");
    ug.setTimesPlayed(1);
    ug.setStartedDate(java.time.LocalDate.of(2025, 8, 12));
    ug.setReflectionHighlight(
        "Level design rewards curiosity without waypoints holding your hand.");
    ug.setReflectionTagsJson(
        ReflectionTagsJson.serializeList(List.of("open-world", "tough-but-fair", "co-op-summons")));
  }

  private static void applyBg3(UserGame ug) {
    ug.setStatus(GameStatus.FINISHED);
    ug.setCompletionType(CompletionType.MAIN_STORY);
    ug.setNotes("Friday night co-op campaign with friends — voice acting carried so many scenes.");
    ug.setRating(10);
    ug.setReviewHeadline("The new gold standard for party-based RPGs");
    ug.setReview(
        "Larian's writing respects player agency without losing heart. Combat stayed fresh through"
            + " Act III, and I actually cared about companion quests. Minor pacing dips in the city"
            + " act, but the highs more than compensate.");
    ug.setWhyPlaying(WhyPlaying.STORY);
    ug.setReflectionTagsJson(
        ReflectionTagsJson.serializeList(List.of("co-op", "turn-based", "branching-story")));
    ug.setStartedDate(java.time.LocalDate.of(2024, 9, 1));
    ug.setFinishedDate(java.time.LocalDate.of(2025, 1, 20));
    ug.setDifficulty("Tactical / Hard");
  }

  private static void applyCyberpunk(UserGame ug) {
    ug.setStatus(GameStatus.PAUSED);
    ug.setCompletionType(CompletionType.NOT_COMPLETED);
    ug.setNotes("Parked after Phantom Liberty — waiting for a quiet week to see the new endings.");
    ug.setRating(7);
    ug.setReviewHeadline("Style and side gigs shine");
    ug.setReview(
        "Night City is still the star. Main story landed better post-2.0; a few rough quest beats"
            + " remain.");
    ug.setWhyPlaying(WhyPlaying.CURIOSITY);
    ug.setProgressPercent(78);
    ug.setProgressLabel("Post-Dogtown wrap-up");
    ug.setStartedDate(java.time.LocalDate.of(2025, 2, 5));
  }

  private static void applyStardew(UserGame ug) {
    ug.setStatus(GameStatus.PLAYING);
    ug.setCompletionType(CompletionType.ENDLESS);
    ug.setNotes("Year 3 on the farm. Fishing mini-game is OP for early gold.");
    ug.setWhyPlaying(WhyPlaying.UNWIND);
    ug.setReflectionHighlight("Perfect 20-minute chill sessions between work blocks.");
    ug.setReflectionTagsJson(
        ReflectionTagsJson.serializeList(List.of("cozy", "farming", "pixel-art")));
    ug.setProgressLabel("Year 3 · Fall");
    ug.setProgressPercent(40);
  }

  private static void applyHollowKnight(UserGame ug) {
    ug.setStatus(GameStatus.FINISHED);
    ug.setCompletionType(CompletionType.MAIN_STORY);
    ug.setNotes("That last stretch in the abyss — brutal but fair.");
    ug.setRating(9);
    ug.setReviewHeadline("Atmosphere and mobility in perfect lockstep");
    ug.setReview(
        "Every new ability recontextualizes old zones. Bosses teach patterns; deaths feel like"
            + " tuition, not punishment.");
    ug.setWhyPlaying(WhyPlaying.CHALLENGE);
    ug.setStartedDate(java.time.LocalDate.of(2024, 4, 10));
    ug.setFinishedDate(java.time.LocalDate.of(2024, 6, 2));
  }

  private static void applyPortal2(UserGame ug) {
    ug.setStatus(GameStatus.FINISHED);
    ug.setCompletionType(CompletionType.HUNDRED_PERCENT);
    ug.setNotes("Co-op puzzles with a friend — communication matters more than aim.");
    ug.setRating(10);
    ug.setReviewHeadline("Pacing and wit still unmatched");
    ug.setReview("Lean, funny, and mechanically transparent. No filler between the good bits.");
    ug.setWhyPlaying(WhyPlaying.SOCIAL);
    ug.setFinishedDate(java.time.LocalDate.of(2023, 11, 18));
  }

  private static void applyGodOfWar(UserGame ug) {
    ug.setStatus(GameStatus.FINISHED);
    ug.setCompletionType(CompletionType.MAIN_STORY);
    ug.setNotes("Boys-trip through the realms — combat weight feels incredible on a controller.");
    ug.setRating(8);
    ug.setReviewHeadline("A confident reinvention");
    ug.setReview(
        "Character work carries the middle acts; optional realms padded the runtime a bit.");
    ug.setWhyPlaying(WhyPlaying.LEGACY);
    ug.setStartedDate(java.time.LocalDate.of(2024, 1, 8));
    ug.setFinishedDate(java.time.LocalDate.of(2024, 2, 14));
  }

  private void seedPlaythroughsAndLogs(String userId, Long gameId, long igdbId) {
    if (playthroughRepository.countByUserIdAndGameId(userId, gameId) > 0) {
      return;
    }
    switch ((int) igdbId) {
      case 119133 -> seedEldenRing(userId, gameId);
      case 194267 -> seedBg3(userId, gameId);
      case 13577 -> seedStardew(userId, gameId);
      case 1877, 18472, 1068, 19560 -> seedSingleCasualLog(userId, gameId, igdbId);
      default -> {}
    }
  }

  private void seedEldenRing(String userId, Long gameId) {
    UserGamePlaythrough main = new UserGamePlaythrough();
    main.setUserId(userId);
    main.setGameId(gameId);
    main.setSortIndex(0);
    main.setShortName("First Tarnished");
    main.setDifficulty("Normal");
    main.setCurrent(true);
    main.setManualPlayMinutes(62 * 60);
    main.setProgressNote("Exploring Altus Plateau — avoiding spoilers online.");
    main.setProgressStatus(PlaythroughProgressStatus.PLAYING);
    main.setRunType(PlaythroughRunType.FIRST_TIME);
    main = playthroughRepository.save(main);

    UserGamePlaythrough shelved = new UserGamePlaythrough();
    shelved.setUserId(userId);
    shelved.setGameId(gameId);
    shelved.setSortIndex(1);
    shelved.setShortName("Mage respec attempt");
    shelved.setDifficulty("Normal");
    shelved.setCurrent(false);
    shelved.setManualPlayMinutes(8 * 60);
    shelved.setProgressNote("Switched to STR mid-game — parked for now.");
    shelved.setProgressStatus(PlaythroughProgressStatus.STOPPED);
    shelved.setRunType(PlaythroughRunType.REPLAY);
    shelved.setEndedAt(Instant.parse("2025-11-02T22:00:00Z"));
    playthroughRepository.save(shelved);

    saveLog(
        userId,
        gameId,
        main.getId(),
        LocalDateTime.of(2026, 3, 1, 20, 15),
        95,
        "Long session in Stormveil — finally clicked with the dodge timing.",
        PlayLogSessionProgress.CONTINUING,
        PlayLogSessionExperience.GREAT);
    saveLog(
        userId,
        gameId,
        main.getId(),
        LocalDateTime.of(2026, 3, 8, 21, 40),
        40,
        "Co-op with a friend: we melted a field boss with rotten breath.",
        PlayLogSessionProgress.CONTINUING,
        PlayLogSessionExperience.GOOD);
    saveLog(
        userId,
        gameId,
        main.getId(),
        LocalDateTime.of(2026, 3, 16, 19, 5),
        120,
        "Quiet night of exploration — found a catacomb I had walked past ten times.",
        PlayLogSessionProgress.CONTINUING,
        PlayLogSessionExperience.OKAY);
  }

  private void seedBg3(String userId, Long gameId) {
    UserGamePlaythrough pt = new UserGamePlaythrough();
    pt.setUserId(userId);
    pt.setGameId(gameId);
    pt.setSortIndex(0);
    pt.setShortName("Tav's campaign");
    pt.setDifficulty("Hard");
    pt.setCurrent(false);
    pt.setManualPlayMinutes(118 * 60);
    pt.setProgressNote("Epilogue choices still echoing — might replay Dark Urge later.");
    pt.setProgressStatus(PlaythroughProgressStatus.COMPLETED);
    pt.setRunType(PlaythroughRunType.FIRST_TIME);
    pt.setEndedAt(Instant.parse("2025-01-20T18:30:00Z"));
    pt = playthroughRepository.save(pt);

    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2024, 10, 12, 17, 0),
        180,
        "Act II crunch — saved before the big decision point.",
        PlayLogSessionProgress.CONTINUING,
        PlayLogSessionExperience.GREAT);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2024, 12, 3, 20, 10),
        240,
        "Final fights + wrap-up. Sat through credits — rare for me.",
        PlayLogSessionProgress.FINISHED,
        PlayLogSessionExperience.GREAT);
  }

  private void seedStardew(String userId, Long gameId) {
    UserGamePlaythrough pt = new UserGamePlaythrough();
    pt.setUserId(userId);
    pt.setGameId(gameId);
    pt.setSortIndex(0);
    pt.setShortName("River farm");
    pt.setDifficulty("Normal");
    pt.setCurrent(true);
    pt.setManualPlayMinutes(55 * 60);
    pt.setProgressNote("Community center done — working on obelisks.");
    pt.setProgressStatus(PlaythroughProgressStatus.PLAYING);
    pt.setRunType(PlaythroughRunType.ONGOING_SANDBOX);
    pt = playthroughRepository.save(pt);

    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2026, 2, 20, 8, 30),
        25,
        "Coffee, crops, and a lucky meteor strike.",
        PlayLogSessionProgress.CONTINUING,
        PlayLogSessionExperience.GOOD);
  }

  private void seedSingleCasualLog(String userId, Long gameId, long igdbId) {
    UserGamePlaythrough pt = new UserGamePlaythrough();
    pt.setUserId(userId);
    pt.setGameId(gameId);
    pt.setSortIndex(0);
    pt.setShortName("Playthrough 1");
    pt.setDifficulty("Normal");
    pt.setCurrent(false);
    pt.setProgressStatus(PlaythroughProgressStatus.COMPLETED);
    pt.setRunType(PlaythroughRunType.FIRST_TIME);
    pt.setEndedAt(Instant.parse("2025-06-01T12:00:00Z"));
    pt = playthroughRepository.save(pt);

    String note =
        switch ((int) igdbId) {
          case 1877 -> "Side gigs in Japantown — photo mode ate 20 minutes.";
          case 18472 -> "Bench rest never felt so earned.";
          case 1068 -> "Wheatley chapters — perfect difficulty curve.";
          case 19560 -> "Baldur fight choreography is still stunning.";
          default -> "Wrapped a memorable run.";
        };
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2025, 5, 14, 19, 45),
        75,
        note,
        PlayLogSessionProgress.FINISHED,
        PlayLogSessionExperience.GOOD);
  }

  private void saveLog(
      String userId,
      Long gameId,
      Long playthroughId,
      LocalDateTime playedAt,
      int durationMinutes,
      String note,
      PlayLogSessionProgress progress,
      PlayLogSessionExperience mood) {
    PlayLog log = new PlayLog();
    log.setUserId(userId);
    log.setGameId(gameId);
    log.setPlaythroughId(playthroughId);
    log.setPlayedAt(playedAt);
    log.setDurationMinutes(durationMinutes);
    log.setNote(note);
    log.setNoteContainsSpoilers(false);
    log.setSessionProgress(progress);
    log.setSessionExperience(mood);
    log.setCountsTowardLibraryPlaytime(true);
    playLogRepository.save(log);
  }
}
