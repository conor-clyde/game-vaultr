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
    7346L, // Zelda: Breath of the Wild
    76253L, // Devil May Cry 5
    472L, // Skyrim
    14593L, // Hollow Knight
    113112L, // Hades
    1009L, // The Last of Us
    11169L, // Final Fantasy VII Remake
    1879L, // Terraria
    119133L, // Elden Ring
    1020L, // GTA V
    125174L, // Overwatch
    119171L, // Baldur's Gate 3
    186725L, // Vampire Survivors
    17000L, // Stardew Valley
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
      case 7346 -> applyZeldaBotw(ug);
      case 76253 -> applyDevilMayCry5(ug);
      case 472 -> applySkyrim(ug);
      case 14593 -> applyHollowKnight(ug);
      case 113112 -> applyHades(ug);
      case 1009 -> applyLastOfUs(ug);
      case 11169 -> applyFf7Remake(ug);
      case 1879 -> applyTerraria(ug);
      case 119133 -> applyEldenRing(ug);
      case 1020 -> applyGtaV(ug);
      case 125174 -> applyOverwatch(ug);
      case 119171 -> applyBg3(ug);
      case 186725 -> applyVampireSurvivors(ug);
      case 17000 -> applyStardewValley(ug);
      default -> {
        ug.setStatus(GameStatus.PLAYING);
        ug.setNotes("Demo entry — customize me.");
      }
    }
  }

  private static void applyZeldaBotw(UserGame ug) {
    ug.setStatus(GameStatus.PLAYING);
    ug.setOwnershipType(OwnershipType.DIGITAL);
    ug.setCompletionType(CompletionType.NOT_COMPLETED);
    ug.setNotes(
        "Wandering shrines before tackling Divine Beasts. I keep getting distracted by side routes.");
    ug.setWhyPlayings(List.of(WhyPlaying.EXPLORE, WhyPlaying.UNWIND));
    ug.setProgressPercent(54);
    ug.setProgressLabel("Two Divine Beasts cleared");
    ug.setDifficulty("Normal");
    ug.setTimesPlayed(1);
    ug.setStartedDate(LocalDate.of(2026, 1, 9));
    ug.setReflectionHighlight(
        "The world rewards curiosity better than objective-chasing.");
    ug.setReflectionTagsJson(
        ReflectionTagsJson.serializeList(List.of("open-world", "exploration", "sandbox")));
  }

  private static void applyDevilMayCry5(UserGame ug) {
    ug.setStatus(GameStatus.PAUSED);
    ug.setOwnershipType(OwnershipType.DIGITAL);
    ug.setCompletionType(CompletionType.NOT_COMPLETED);
    ug.setNotes("Stopped at Mission 11 while learning style-switch combos.");
    ug.setRating(8);
    ug.setWhyPlayings(List.of(WhyPlaying.CHALLENGE, WhyPlaying.INTENSITY));
    ug.setProgressPercent(48);
    ug.setProgressLabel("Nero route mid-campaign");
    ug.setStartedDate(LocalDate.of(2025, 12, 4));
    ug.setTimesPlayed(1);
  }

  private static void applySkyrim(UserGame ug) {
    ug.setStatus(GameStatus.NOT_PLAYING);
    ug.setOwnershipType(OwnershipType.PHYSICAL);
    ug.setCompletionType(CompletionType.MAIN_STORY);
    ug.setNotes("Legacy save from years ago. Keeping it installed for modded wandering nights.");
    ug.setRating(9);
    ug.setReview("Still unmatched for sandbox roleplay freedom despite showing its age.");
    ug.setWhyPlayings(List.of(WhyPlaying.LEGACY, WhyPlaying.EXPLORE));
    ug.setStartedDate(LocalDate.of(2023, 2, 10));
    ug.setFinishedDate(LocalDate.of(2023, 5, 21));
    ug.setTimesPlayed(3);
  }

  private static void applyHollowKnight(UserGame ug) {
    ug.setStatus(GameStatus.PLAYING);
    ug.setOwnershipType(OwnershipType.DIGITAL);
    ug.setCompletionType(CompletionType.NOT_COMPLETED);
    ug.setNotes("Main path done before, now pushing through Pantheon attempts.");
    ug.setRating(9);
    ug.setWhyPlayings(List.of(WhyPlaying.CHALLENGE, WhyPlaying.PROGRESS));
    ug.setProgressPercent(83);
    ug.setProgressLabel("Godhome pantheon grind");
    ug.setStartedDate(LocalDate.of(2025, 10, 15));
  }

  private static void applyHades(UserGame ug) {
    ug.setStatus(GameStatus.FINISHED);
    ug.setOwnershipType(OwnershipType.DIGITAL);
    ug.setCompletionType(CompletionType.MAIN_STORY);
    ug.setNotes("Heat runs after credits were just as fun as the first clear.");
    ug.setRating(9);
    ug.setReview("Every run feels meaningful thanks to smart build variety and pacing.");
    ug.setWhyPlayings(List.of(WhyPlaying.CHALLENGE, WhyPlaying.UNWIND));
    ug.setStartedDate(LocalDate.of(2025, 6, 6));
    ug.setFinishedDate(LocalDate.of(2025, 9, 1));
    ug.setTimesPlayed(1);
  }

  private static void applyLastOfUs(UserGame ug) {
    ug.setStatus(GameStatus.FINISHED);
    ug.setOwnershipType(OwnershipType.PHYSICAL);
    ug.setCompletionType(CompletionType.MAIN_STORY);
    ug.setNotes("Played over two weekends and let the ending sit for a while.");
    ug.setRating(9);
    ug.setReview("Excellent tension and character payoff. Combat remains grounded and tense.");
    ug.setWhyPlayings(List.of(WhyPlaying.STORY, WhyPlaying.LEGACY));
    ug.setStartedDate(LocalDate.of(2025, 3, 14));
    ug.setFinishedDate(LocalDate.of(2025, 3, 30));
    ug.setTimesPlayed(1);
  }

  private static void applyFf7Remake(UserGame ug) {
    ug.setStatus(GameStatus.DROPPED);
    ug.setOwnershipType(OwnershipType.SUBSCRIPTION);
    ug.setCompletionType(CompletionType.NOT_COMPLETED);
    ug.setNotes("Loved the combat system, but sidequest pacing in the middle chapters lost me.");
    ug.setWhyPlayings(List.of(WhyPlaying.CURIOSITY, WhyPlaying.STORY));
    ug.setProgressPercent(45);
    ug.setProgressLabel("Chapter 10");
    ug.setStartedDate(LocalDate.of(2025, 7, 2));
    ug.setFinishedDate(LocalDate.of(2025, 7, 28));
    ug.setTimesPlayed(1);
  }

  private static void applyTerraria(UserGame ug) {
    ug.setStatus(GameStatus.PLAYING);
    ug.setOwnershipType(OwnershipType.DIGITAL);
    ug.setCompletionType(CompletionType.ENDLESS);
    ug.setNotes("Building a sky bridge and prepping for hardmode bosses.");
    ug.setWhyPlayings(List.of(WhyPlaying.EXPLORE, WhyPlaying.SOCIAL));
    ug.setProgressLabel("Pre-hardmode base setup");
    ug.setProgressPercent(38);
    ug.setStartedDate(LocalDate.of(2026, 2, 1));
    ug.setTimesPlayed(2);
  }

  private static void applyEldenRing(UserGame ug) {
    ug.setStatus(GameStatus.PLAYING);
    ug.setOwnershipType(OwnershipType.DIGITAL);
    ug.setCompletionType(CompletionType.NOT_COMPLETED);
    ug.setNotes("Taking it slow: every cave before story bosses.");
    ug.setWhyPlayings(List.of(WhyPlaying.CHALLENGE, WhyPlaying.EXPLORE));
    ug.setProgressPercent(68);
    ug.setProgressLabel("Leyndell approach");
    ug.setDifficulty("Normal");
    ug.setStartedDate(LocalDate.of(2025, 8, 12));
    ug.setReflectionHighlight("Open areas still reward wandering more than checklist play.");
    ug.setReflectionTagsJson(
        ReflectionTagsJson.serializeList(List.of("open-world", "tough-but-fair", "build-testing")));
  }

  private static void applyGtaV(UserGame ug) {
    ug.setStatus(GameStatus.NOT_PLAYING);
    ug.setOwnershipType(OwnershipType.DIGITAL);
    ug.setCompletionType(CompletionType.MAIN_STORY);
    ug.setNotes("Finished story years ago, now mostly revisit for occasional chaos sessions.");
    ug.setRating(8);
    ug.setWhyPlayings(List.of(WhyPlaying.LEGACY, WhyPlaying.UNWIND));
    ug.setStartedDate(LocalDate.of(2022, 9, 18));
    ug.setFinishedDate(LocalDate.of(2022, 11, 2));
    ug.setTimesPlayed(2);
  }

  private static void applyOverwatch(UserGame ug) {
    ug.setStatus(GameStatus.PAUSED);
    ug.setOwnershipType(OwnershipType.FREE);
    ug.setCompletionType(CompletionType.ENDLESS);
    ug.setNotes("Taking a break between ranked seasons after support-heavy grind.");
    ug.setWhyPlayings(List.of(WhyPlaying.SOCIAL, WhyPlaying.INTENSITY));
    ug.setProgressLabel("Ranked placements done");
    ug.setTimesPlayed(1);
    ug.setStartedDate(LocalDate.of(2025, 11, 3));
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

  private static void applyVampireSurvivors(UserGame ug) {
    ug.setStatus(GameStatus.DROPPED);
    ug.setOwnershipType(OwnershipType.DIGITAL);
    ug.setCompletionType(CompletionType.NOT_COMPLETED);
    ug.setNotes(
        "Super fun loop but I bounced after unlock grind felt repetitive.");
    ug.setRating(7);
    ug.setWhyPlayings(List.of(WhyPlaying.UNWIND, WhyPlaying.CURIOSITY));
    ug.setProgressPercent(52);
    ug.setProgressLabel("Most base unlocks");
    ug.setStartedDate(LocalDate.of(2025, 4, 8));
    ug.setFinishedDate(LocalDate.of(2025, 5, 10));
    ug.setTimesPlayed(1);
  }

  private static void applyStardewValley(UserGame ug) {
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

  private void seedPlaythroughsAndLogs(String userId, Long gameId, long igdbId) {
    playLogRepository.deleteByUserIdAndGameId(userId, gameId);
    playthroughRepository.deleteByUserIdAndGameId(userId, gameId);
    switch ((int) igdbId) {
      case 7346 -> seedZelda(userId, gameId);
      case 113112 -> seedHades(userId, gameId);
      case 1009 -> seedLastOfUs(userId, gameId);
      case 1879 -> seedTerraria(userId, gameId);
      case 119133 -> seedEldenRing(userId, gameId);
      case 119171 -> seedBg3(userId, gameId);
      case 17000 -> seedStardewValley(userId, gameId);
      case 76253, 472, 14593, 11169, 1020, 125174, 186725 -> seedSingleCasualLog(userId, gameId, igdbId);
      default -> {}
    }
  }

  private void seedZelda(String userId, Long gameId) {
    UserGamePlaythrough pt = new UserGamePlaythrough();
    pt.setUserId(userId);
    pt.setGameId(gameId);
    pt.setSortIndex(0);
    pt.setShortName("Shrine route");
    pt.setDifficulty("Normal");
    pt.setCurrent(true);
    pt.setManualPlayMinutes(42 * 60);
    pt.setProgressNote("Collecting towers and shrine clusters before final push.");
    pt.setProgressStatus(PlaythroughProgressStatus.PLAYING);
    pt = playthroughRepository.save(pt);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2026, 2, 12, 20, 20),
        85,
        "Shrine chain in Hebra and one accidental guardian panic sprint.",
        PlayLogSessionExperience.GOOD);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2026, 3, 2, 19, 45),
        70,
        "Farmed materials and mapped side quests near Gerudo.",
        PlayLogSessionExperience.OKAY);
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

  private void seedHades(String userId, Long gameId) {
    UserGamePlaythrough pt = new UserGamePlaythrough();
    pt.setUserId(userId);
    pt.setGameId(gameId);
    pt.setSortIndex(0);
    pt.setShortName("First clear");
    pt.setDifficulty("Normal");
    pt.setCurrent(false);
    pt.setManualPlayMinutes(28 * 60);
    pt.setProgressNote("Beat final boss and unlocked heat runs.");
    pt.setProgressStatus(PlaythroughProgressStatus.COMPLETED);
    pt.setEndedAt(Instant.parse("2025-09-01T22:20:00Z"));
    pt = playthroughRepository.save(pt);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2025, 8, 20, 21, 5),
        55,
        "Strong Artemis build carried two clean clears.",
        PlayLogSessionExperience.GREAT);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2025, 8, 29, 22, 15),
        45,
        "First post-credits heat attempt, still fun even when it fails.",
        PlayLogSessionExperience.GOOD);
  }

  private void seedLastOfUs(String userId, Long gameId) {
    UserGamePlaythrough pt = new UserGamePlaythrough();
    pt.setUserId(userId);
    pt.setGameId(gameId);
    pt.setSortIndex(0);
    pt.setShortName("Story run");
    pt.setDifficulty("Normal");
    pt.setCurrent(false);
    pt.setManualPlayMinutes(16 * 60);
    pt.setProgressNote("Single playthrough focused on story pacing.");
    pt.setProgressStatus(PlaythroughProgressStatus.COMPLETED);
    pt.setEndedAt(Instant.parse("2025-03-30T20:10:00Z"));
    pt = playthroughRepository.save(pt);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2025, 3, 15, 19, 10),
        100,
        "Stealth section took longer than expected but nailed it eventually.",
        PlayLogSessionExperience.GOOD);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2025, 3, 30, 21, 0),
        110,
        "Wrapped the finale and sat with credits for a while.",
        PlayLogSessionExperience.GREAT);
  }

  private void seedTerraria(String userId, Long gameId) {
    UserGamePlaythrough pt = new UserGamePlaythrough();
    pt.setUserId(userId);
    pt.setGameId(gameId);
    pt.setSortIndex(0);
    pt.setShortName("Co-op world");
    pt.setDifficulty("Normal");
    pt.setCurrent(true);
    pt.setManualPlayMinutes(24 * 60);
    pt.setProgressNote("Preparing hardmode boss path with shared base upgrades.");
    pt.setProgressStatus(PlaythroughProgressStatus.PLAYING);
    pt = playthroughRepository.save(pt);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2026, 2, 9, 20, 0),
        60,
        "Built arena and gathered potion mats for next boss attempts.",
        PlayLogSessionExperience.GOOD);
    saveLog(
        userId,
        gameId,
        pt.getId(),
        LocalDateTime.of(2026, 2, 23, 22, 5),
        75,
        "Mining run turned into an accidental biome tunnel project.",
        PlayLogSessionExperience.OKAY);
  }

  private void seedStardewValley(String userId, Long gameId) {
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
        (igdbId == 14593 || igdbId == 125174)
            ? PlaythroughProgressStatus.PLAYING
            : PlaythroughProgressStatus.COMPLETED);
    if (igdbId != 14593 && igdbId != 125174) {
      pt.setEndedAt(Instant.parse("2025-06-01T12:00:00Z"));
    }
    pt = playthroughRepository.save(pt);

    String note =
        switch ((int) igdbId) {
          case 76253 -> "Training mode + combo challenges before returning to campaign.";
          case 472 -> "Short dungeon crawl to test a mod list refresh.";
          case 14593 -> "Pantheon attempts; one clean run, two fast wipes.";
          case 11169 -> "Boss encounter felt great, chapter pacing still dragging.";
          case 1020 -> "One chaos sandbox session and a few stunt challenges.";
          case 125174 -> "Quick support queue block to finish weekly tasks.";
          case 186725 -> "One more unlock run before deciding to shelve it.";
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
        igdbId == 125174 ? 55 : 90,
        switch ((int) igdbId) {
          case 76253 -> "Can execute the flashy stuff in training, still messy under pressure.";
          case 472 -> "Spent most of the night crafting and forgot the main quest existed.";
          case 14593 -> "Close on one pantheon clear — reaction timing still inconsistent.";
          case 11169 -> "Took a break after long corridor chapter; might revisit later.";
          case 1020 -> "Fun in short bursts, but no pull for long campaign replay now.";
          case 125174 -> "Placements felt uneven; pausing ranked for now.";
          case 186725 -> "Loop is satisfying, but progression goals started to feel repetitive.";
          default -> "Another evening session to push progress.";
        },
        (igdbId == 11169 || igdbId == 186725) ? PlayLogSessionExperience.MEH : PlayLogSessionExperience.GREAT);
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
