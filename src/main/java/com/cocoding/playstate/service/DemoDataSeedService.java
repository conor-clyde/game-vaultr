package com.cocoding.playstate.service;

import com.cocoding.playstate.domain.enums.CompletionType;
import com.cocoding.playstate.domain.enums.GameStatus;
import com.cocoding.playstate.domain.enums.OwnershipType;
import com.cocoding.playstate.domain.enums.PlayLogSessionExperience;
import com.cocoding.playstate.domain.enums.PlaythroughProgressStatus;
import com.cocoding.playstate.domain.enums.WhyPlaying;
import com.cocoding.playstate.dto.search.SearchGameRow;
import com.cocoding.playstate.igdb.IgdbService;
import com.cocoding.playstate.model.Game;
import com.cocoding.playstate.model.PlayLog;
import com.cocoding.playstate.model.UserAccount;
import com.cocoding.playstate.model.UserGame;
import com.cocoding.playstate.model.UserGamePlaythrough;
import com.cocoding.playstate.repository.GameRepository;
import com.cocoding.playstate.repository.PlayLogRepository;
import com.cocoding.playstate.repository.UserAccountRepository;
import com.cocoding.playstate.repository.UserGamePlaythroughRepository;
import com.cocoding.playstate.repository.UserGameRepository;
import com.cocoding.playstate.util.ReflectionTagsJson;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    UserAccount account = new UserAccount();
    account.setUsername(username);
    account.setPasswordHash(passwordEncoder.encode(demoPassword));
    try {
      // Force insert now so uniqueness violations are caught here, not at transaction commit.
      userAccountRepository.saveAndFlush(account);
      logger.info("Created demo user '{}'.", username);
      return true;
    } catch (DataIntegrityViolationException e) {
      // Another startup thread/instance may have created the same username first.
      logger.warn("Demo user '{}' not created because username already exists.", username);
      return userAccountRepository.existsByUsernameIgnoreCase(username);
    }
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
    ug.setOwnershipType(OwnershipType.DIGITAL);
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
    ug.setOwnershipType(OwnershipType.PHYSICAL);
    ug.setCompletionType(CompletionType.MAIN_STORY);
    ug.setNotes("Friday night co-op campaign with friends — voice acting carried so many scenes.");
    ug.setRating(10);
    ug.setReview(
        "Larian's writing respects player agency without losing heart. Combat stayed fresh through"
            + " Act III, and I actually cared about companion quests. Minor pacing dips in the city"
            + " act, but the highs more than compensate.");
    ug.setWhyPlayings(List.of(WhyPlaying.STORY, WhyPlaying.SOCIAL, WhyPlaying.PROGRESS));
    ug.setReflectionTagsJson(
        ReflectionTagsJson.serializeList(List.of("co-op", "turn-based", "branching-story")));
    ug.setStartedDate(java.time.LocalDate.of(2024, 9, 1));
    ug.setFinishedDate(java.time.LocalDate.of(2025, 1, 20));
    ug.setDifficulty("Tactical / Hard");
  }

  private static void applyCyberpunk(UserGame ug) {
    ug.setStatus(GameStatus.DROPPED);
    ug.setOwnershipType(OwnershipType.SUBSCRIPTION);
    ug.setCompletionType(CompletionType.NOT_COMPLETED);
    ug.setNotes(
        "Dropped after Phantom Liberty finale. The city is incredible, but I burned out on the"
            + " side-gig cleanup loop.");
    ug.setRating(7);
    ug.setReview(
        "Night City is still the star. Main story landed better post-2.0; a few rough quest beats"
            + " remain.");
    ug.setWhyPlayings(List.of(WhyPlaying.CURIOSITY, WhyPlaying.INTENSITY));
    ug.setProgressPercent(78);
    ug.setProgressLabel("Post-Dogtown wrap-up");
    ug.setStartedDate(java.time.LocalDate.of(2025, 2, 5));
    ug.setFinishedDate(java.time.LocalDate.of(2025, 5, 30));
    ug.setTimesPlayed(1);
  }

  private static void applyStardew(UserGame ug) {
    ug.setStatus(GameStatus.PLAYING);
    ug.setOwnershipType(OwnershipType.FREE);
    ug.setCompletionType(CompletionType.ENDLESS);
    ug.setNotes("Year 3 on the farm. Fishing mini-game is OP for early gold.");
    ug.setWhyPlayings(List.of(WhyPlaying.UNWIND, WhyPlaying.PROGRESS));
    ug.setReflectionHighlight("Perfect 20-minute chill sessions between work blocks.");
    ug.setReflectionTagsJson(
        ReflectionTagsJson.serializeList(List.of("cozy", "farming", "pixel-art")));
    ug.setProgressLabel("Year 3 · Fall");
    ug.setProgressPercent(40);
  }

  private static void applyHollowKnight(UserGame ug) {
    ug.setStatus(GameStatus.PAUSED);
    ug.setOwnershipType(OwnershipType.DIGITAL);
    ug.setCompletionType(CompletionType.MAIN_STORY);
    ug.setNotes("Paused at Godhome content. Main ending done, but pantheons are a longer-term project.");
    ug.setRating(9);
    ug.setReview(
        "Every new ability recontextualizes old zones. Bosses teach patterns; deaths feel like"
            + " tuition, not punishment.");
    ug.setWhyPlayings(List.of(WhyPlaying.CHALLENGE, WhyPlaying.PROGRESS));
    ug.setStartedDate(java.time.LocalDate.of(2024, 4, 10));
    ug.setFinishedDate(java.time.LocalDate.of(2024, 6, 2));
    ug.setProgressPercent(86);
    ug.setProgressLabel("Godhome pantheon attempts");
  }

  private static void applyPortal2(UserGame ug) {
    ug.setStatus(GameStatus.NOT_PLAYING);
    ug.setOwnershipType(OwnershipType.UNOWNED);
    ug.setCompletionType(CompletionType.HUNDRED_PERCENT);
    ug.setNotes(
        "Finished years ago on another account. Keeping it in backlog for a possible co-op replay"
            + " with a new friend.");
    ug.setRating(10);
    ug.setReview("Lean, funny, and mechanically transparent. No filler between the good bits.");
    ug.setWhyPlayings(List.of(WhyPlaying.SOCIAL, WhyPlaying.LEGACY));
    ug.setStartedDate(LocalDate.of(2023, 11, 1));
    ug.setFinishedDate(LocalDate.of(2023, 11, 18));
    ug.setTimesPlayed(2);
  }

  private static void applyGodOfWar(UserGame ug) {
    ug.setStatus(GameStatus.FINISHED);
    ug.setOwnershipType(OwnershipType.PHYSICAL);
    ug.setCompletionType(CompletionType.MAIN_STORY);
    ug.setNotes("Boys-trip through the realms — combat weight feels incredible on a controller.");
    ug.setRating(8);
    ug.setReview(
        "Character work carries the middle acts; optional realms padded the runtime a bit.");
    ug.setWhyPlayings(List.of(WhyPlaying.LEGACY, WhyPlaying.STORY));
    ug.setStartedDate(java.time.LocalDate.of(2024, 1, 8));
    ug.setFinishedDate(java.time.LocalDate.of(2024, 2, 14));
  }

  private void seedPlaythroughsAndLogs(String userId, Long gameId, long igdbId) {
    playLogRepository.deleteByUserIdAndGameId(userId, gameId);
    playthroughRepository.deleteByUserIdAndGameId(userId, gameId);
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
    shelved.setEndedAt(Instant.parse("2025-11-02T22:00:00Z"));
    playthroughRepository.save(shelved);

    saveLog(
        userId,
        gameId,
        main.getId(),
        LocalDateTime.of(2026, 3, 1, 20, 15),
        95,
        "Long session in Stormveil — finally clicked with the dodge timing.",
        PlayLogSessionExperience.GREAT);
    saveLog(
        userId,
        gameId,
        main.getId(),
        LocalDateTime.of(2026, 3, 8, 21, 40),
        40,
        "Co-op with a friend: we melted a field boss with rotten breath.",
        PlayLogSessionExperience.GOOD);
    saveLog(
        userId,
        gameId,
        main.getId(),
        LocalDateTime.of(2026, 3, 16, 19, 5),
        120,
        "Quiet night of exploration — found a catacomb I had walked past ten times.",
        PlayLogSessionExperience.OKAY);
    saveLog(
        userId,
        gameId,
        main.getId(),
        LocalDateTime.of(2026, 3, 24, 22, 10),
        65,
        "Tried a new ash of war setup and bullied two mini-bosses in a row.",
        PlayLogSessionExperience.GREAT);
    saveLog(
        userId,
        gameId,
        main.getId(),
        LocalDateTime.of(2026, 4, 6, 18, 55),
        50,
        "Small progress session before dinner, just map cleanup and materials farming.",
        PlayLogSessionExperience.GOOD);
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
    pt.setEndedAt(Instant.parse("2025-01-20T18:30:00Z"));
    pt = playthroughRepository.save(pt);

    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2024, 10, 12, 17, 0),
        180,
        "Act II crunch — saved before the big decision point.",
        PlayLogSessionExperience.GREAT);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2024, 12, 3, 20, 10),
        240,
        "Final fights + wrap-up. Sat through credits — rare for me.",
        PlayLogSessionExperience.GREAT);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2024, 11, 9, 19, 25),
        205,
        "Companion quest marathon. Several outcomes changed based on one dialogue check.",
        PlayLogSessionExperience.GREAT);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2024, 9, 19, 18, 40),
        130,
        "Act I exploration detour, accidentally found a sequence break.",
        PlayLogSessionExperience.GOOD);
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
    pt = playthroughRepository.save(pt);

    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2026, 2, 20, 8, 30),
        25,
        "Coffee, crops, and a lucky meteor strike.",
        PlayLogSessionExperience.GOOD);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2026, 2, 27, 21, 0),
        45,
        "Skull cavern run: finally reached floor 100 with enough bombs.",
        PlayLogSessionExperience.GREAT);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2026, 3, 5, 7, 50),
        30,
        "Quick before-work session to reorganize farm paths and artisan sheds.",
        PlayLogSessionExperience.OKAY);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2026, 3, 20, 20, 5),
        55,
        "Rainy in-game day, mostly fishing and villager gift route.",
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
    pt.setProgressStatus(
        igdbId == 18472 ? PlaythroughProgressStatus.PLAYING : PlaythroughProgressStatus.COMPLETED);
    if (igdbId != 18472) {
      pt.setEndedAt(Instant.parse("2025-06-01T12:00:00Z"));
    }
    pt = playthroughRepository.save(pt);

    String note =
        switch ((int) igdbId) {
          case 1877 -> "Side gigs in Japantown — photo mode ate 20 minutes.";
          case 18472 -> "Short Godhome attempts tonight. Muscle memory is coming back slowly.";
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
        PlayLogSessionExperience.GOOD);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2025, 5, 28, 21, 5),
        igdbId == 1068 ? 60 : 90,
        switch ((int) igdbId) {
          case 1877 -> "Tried a stealth-heavy build and bounced off it after one mission chain.";
          case 18472 -> "Practiced one boss for an hour. Great combat rhythm, still very rusty.";
          case 1068 -> "Co-op replay with a first-timer. Their reaction to each reveal was gold.";
          case 19560 -> "Cleaned up valkyrie fights. Optional bosses are still a huge spike.";
          default -> "Another evening session to push progress.";
        },
        igdbId == 1877 ? PlayLogSessionExperience.MEH : PlayLogSessionExperience.GREAT);
  }

  private void saveLog(
      String userId,
      Long gameId,
      Long playthroughId,
      LocalDateTime playedAt,
      int durationMinutes,
      String note,
      PlayLogSessionExperience mood) {
    PlayLog log = new PlayLog();
    log.setUserId(userId);
    log.setGameId(gameId);
    log.setPlaythroughId(playthroughId);
    log.setPlayedAt(playedAt);
    log.setDurationMinutes(durationMinutes);
    log.setNote(note);
    log.setNoteContainsSpoilers(false);
    log.setSessionExperience(mood);
    playLogRepository.save(log);
  }
}
