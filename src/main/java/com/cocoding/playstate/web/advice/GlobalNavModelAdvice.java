package com.cocoding.playstate.web.advice;

import com.cocoding.playstate.model.Game;
import com.cocoding.playstate.model.PlayLog;
import com.cocoding.playstate.model.UserGame;
import com.cocoding.playstate.domain.enums.WhyPlaying;
import com.cocoding.playstate.repository.GameRepository;
import com.cocoding.playstate.repository.PlayLogRepository;
import com.cocoding.playstate.repository.UserGameRepository;
import com.cocoding.playstate.security.LibraryUserIds;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalNavModelAdvice {

  private static final int SESSION_AWAITING_END_OPEN_HOURS = 24;

  private final PlayLogRepository playLogRepository;
  private final GameRepository gameRepository;
  private final UserGameRepository userGameRepository;

  public GlobalNavModelAdvice(
      PlayLogRepository playLogRepository,
      GameRepository gameRepository,
      UserGameRepository userGameRepository) {
    this.playLogRepository = playLogRepository;
    this.gameRepository = gameRepository;
    this.userGameRepository = userGameRepository;
  }

  @ModelAttribute
  public void addNavAuth(Authentication authentication, Model model) {
    boolean authed =
        authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
    model.addAttribute("navAuthenticated", authed);
    if (authed && authentication != null) {
      model.addAttribute("navUsername", authentication.getName());
      model.addAttribute("whyPlayings", Arrays.asList(WhyPlaying.values()));
      model.addAttribute("logPlayModalPageHasPlayLogs", false);
      model.addAttribute("logPlayModalPageLastPlayedRel", null);
      model.addAttribute("logPlayModalPagePlaySessionCount", 0);
      model.addAttribute("logPlayModalPageLastPlayedCalendar", null);
      model.addAttribute("logPlayModalPageTotalPlayLabel", null);
    }
  }

  @ModelAttribute
  public void addNavActivePlaySession(Authentication authentication, Model model) {
    model.addAttribute("navActivePlaySession", false);
    model.addAttribute("navActivePlaySessionLogId", null);
    model.addAttribute("navActivePlaySessionGameApiId", null);
    model.addAttribute("navActivePlaySessionGameTitle", null);
    model.addAttribute("navActivePlaySessionStartedAtEpochMs", 0L);
    model.addAttribute("navActivePlaySessionStartedAtIso", null);
    model.addAttribute("navActivePlaySessionPlatform", null);
    model.addAttribute("navActivePlaySessionCoverUrl", null);

    String userId = LibraryUserIds.optional(authentication);
    if (userId == null) {
      return;
    }
    NavActiveSessionState state = loadNavActiveSessionState(userId);
    if (!state.active()) {
      return;
    }
    model.addAttribute("navActivePlaySession", true);
    model.addAttribute("navActivePlaySessionLogId", state.logId());
    model.addAttribute("navActivePlaySessionGameApiId", state.gameApiId());
    model.addAttribute("navActivePlaySessionGameTitle", state.gameTitle());
    model.addAttribute("navActivePlaySessionStartedAtEpochMs", state.startedAtEpochMs());
    model.addAttribute("navActivePlaySessionStartedAtIso", state.startedAtIso());
    model.addAttribute("navActivePlaySessionPlatform", state.platform());
    model.addAttribute("navActivePlaySessionCoverUrl", state.coverUrl());
  }

  private static boolean isSessionAwaitingEndWindowOpen(LocalDateTime sessionStartedAt) {
    return sessionStartedAt.plusHours(SESSION_AWAITING_END_OPEN_HOURS).isAfter(LocalDateTime.now());
  }

  private NavActiveSessionState loadNavActiveSessionState(String userId) {
    Optional<PlayLog> openOpt =
        playLogRepository
            .findFirstByUserIdAndSessionStartedAtIsNotNullAndDurationMinutesIsNullOrderBySessionStartedAtDescIdDesc(
                userId);
    if (openOpt.isEmpty()) {
      return NavActiveSessionState.inactive();
    }
    PlayLog pl = openOpt.get();
    LocalDateTime started = pl.getSessionStartedAt();
    if (started == null || !isSessionAwaitingEndWindowOpen(started)) {
      return NavActiveSessionState.inactive();
    }
    Optional<Game> gameOpt = gameRepository.findById(pl.getGameId());
    if (gameOpt.isEmpty()) {
      return NavActiveSessionState.inactive();
    }
    Game g = gameOpt.get();
    long epochMs = started.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    String cover = g.getImageUrl();
    String normalizedCover = (cover != null && !cover.isBlank()) ? cover : null;
    String platform =
        userGameRepository
            .findByUserIdAndGameId(userId, g.getId())
            .map(UserGame::getPlatform)
            .filter(p -> p != null && !p.isBlank())
            .orElse(null);
    return new NavActiveSessionState(
        true,
        pl.getId(),
        g.getApiId(),
        g.getTitle(),
        epochMs,
        started.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        platform,
        normalizedCover);
  }

  private record NavActiveSessionState(
      boolean active,
      Long logId,
      String gameApiId,
      String gameTitle,
      long startedAtEpochMs,
      String startedAtIso,
      String platform,
      String coverUrl) {
    private static NavActiveSessionState inactive() {
      return new NavActiveSessionState(false, null, null, null, 0L, null, null, null);
    }
  }
}
