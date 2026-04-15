package com.cocoding.playstate.config;

import com.cocoding.playstate.model.Game;
import com.cocoding.playstate.model.WhyPlaying;
import com.cocoding.playstate.model.PlayLog;
import com.cocoding.playstate.model.UserGame;
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
        Optional<PlayLog> openOpt =
                playLogRepository
                        .findFirstByUserIdAndSessionStartedAtIsNotNullAndDurationMinutesIsNullOrderBySessionStartedAtDescIdDesc(
                                userId);
        if (openOpt.isEmpty()) {
            return;
        }
        PlayLog pl = openOpt.get();
        LocalDateTime started = pl.getSessionStartedAt();
        if (started == null || !isSessionAwaitingEndWindowOpen(started)) {
            return;
        }
        Optional<Game> gameOpt = gameRepository.findById(pl.getGameId());
        if (gameOpt.isEmpty()) {
            return;
        }
        Game g = gameOpt.get();
        long epochMs = started.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        model.addAttribute("navActivePlaySession", true);
        model.addAttribute("navActivePlaySessionLogId", pl.getId());
        model.addAttribute("navActivePlaySessionGameApiId", g.getApiId());
        model.addAttribute("navActivePlaySessionGameTitle", g.getTitle());
        model.addAttribute("navActivePlaySessionStartedAtEpochMs", epochMs);
        model.addAttribute("navActivePlaySessionStartedAtIso", started.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        String cover = g.getImageUrl();
        model.addAttribute("navActivePlaySessionCoverUrl", (cover != null && !cover.isBlank()) ? cover : null);
        String platform =
                userGameRepository
                        .findByUserIdAndGameId(userId, g.getId())
                        .map(UserGame::getPlatform)
                        .filter(p -> p != null && !p.isBlank())
                        .orElse(null);
        model.addAttribute("navActivePlaySessionPlatform", platform);
    }

    private static boolean isSessionAwaitingEndWindowOpen(LocalDateTime sessionStartedAt) {
        return sessionStartedAt.plusHours(SESSION_AWAITING_END_OPEN_HOURS).isAfter(LocalDateTime.now());
    }
}
