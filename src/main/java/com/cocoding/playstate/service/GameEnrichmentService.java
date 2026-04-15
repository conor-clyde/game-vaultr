package com.cocoding.playstate.service;

import com.cocoding.playstate.model.Game;
import com.cocoding.playstate.repository.GameRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class GameEnrichmentService {
    private static final int IGDB_RETRY_COOLDOWN_MINUTES = 10;

    private final GameRepository gameRepository;
    private final IgdbService igdbService;

    public GameEnrichmentService(GameRepository gameRepository, IgdbService igdbService) {
        this.gameRepository = gameRepository;
        this.igdbService = igdbService;
    }

    
    public void enrichFromIgdbIfIncomplete(Game game) {
        boolean needPublisher = game.getPublisher() == null || game.getPublisher().isBlank();
        boolean needDeveloper = game.getDeveloper() == null || game.getDeveloper().isBlank();
        boolean needYear = game.getReleaseYear() == null;
        boolean needReleaseDate = game.getReleaseDate() == null;
        boolean needGenres = game.getGenres() == null || game.getGenres().isBlank();
        if (!needPublisher && !needDeveloper && !needYear && !needReleaseDate && !needGenres) {
            return;
        }
        try {
            long igdbId = Long.parseLong(game.getApiId().trim());
            Map<String, Object> meta = igdbService.getGamePublisherAndYear(igdbId);
            boolean changed = false;
            if (needPublisher) {
                Object pub = meta.get("publisher");
                if (pub != null) {
                    String p = pub.toString().trim();
                    if (!p.isEmpty()) {
                        game.setPublisher(p);
                        changed = true;
                    }
                }
            }
            if (needDeveloper) {
                Object dev = meta.get("developer");
                if (dev != null) {
                    String d = dev.toString().trim();
                    if (!d.isEmpty()) {
                        game.setDeveloper(d);
                        changed = true;
                    }
                }
            }
            if (needReleaseDate) {
                Object d = meta.get("releaseDate");
                if (d instanceof LocalDate ld) {
                    game.setReleaseDate(ld);
                    if (needYear) {
                        game.setReleaseYear(ld.getYear());
                    }
                    changed = true;
                }
            }
            if (needYear && game.getReleaseYear() == null) {
                Object y = meta.get("releaseYear");
                if (y instanceof Number n) {
                    game.setReleaseYear(n.intValue());
                    changed = true;
                }
            }
            if (needGenres) {
                @SuppressWarnings("unchecked")
                List<String> genreNames = (List<String>) meta.get("genreNames");
                if (genreNames != null && !genreNames.isEmpty()) {
                    game.setGenres(String.join(", ", genreNames));
                    changed = true;
                }
            }
            if (changed) {
                gameRepository.save(game);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    public void refreshPlatformOptionsFromIgdb(Game game, String fallbackPlatform) {
        if (game == null) {
            return;
        }
        List<String> options = igdbService.getPlatformOptionsForGame(game.getApiId(), fallbackPlatform);
        if (options == null || options.isEmpty()) {
            return;
        }
        List<String> existing = game.getPlatformOptionsList();
        if (existing.equals(options)) {
            return;
        }
        game.setPlatformOptionsList(options);
        gameRepository.save(game);
    }

    public void refreshPlatformOptionsFromIgdbIfMissing(Game game, String fallbackPlatform) {
        if (game == null) {
            return;
        }
        if (!game.getPlatformOptionsList().isEmpty()) {
            return;
        }
        refreshPlatformOptionsFromIgdb(game, fallbackPlatform);
    }

    public void enrichFromIgdbIfIncompleteWithCooldown(Game game, String fallbackPlatform) {
        if (game == null) {
            return;
        }
        boolean needsMetadata = isMetadataIncomplete(game);
        boolean needsPlatforms = game.getPlatformOptionsList().isEmpty();
        if (!needsMetadata && !needsPlatforms) {
            return;
        }
        if (!isCooldownElapsed(game.getIgdbLastAttemptAt())) {
            return;
        }
        game.setIgdbLastAttemptAt(LocalDateTime.now());
        gameRepository.save(game);
        if (needsMetadata) {
            enrichFromIgdbIfIncomplete(game);
        }
        if (needsPlatforms) {
            refreshPlatformOptionsFromIgdb(game, fallbackPlatform);
        }
    }

    private static boolean isMetadataIncomplete(Game game) {
        return game.getPublisher() == null
                || game.getPublisher().isBlank()
                || game.getDeveloper() == null
                || game.getDeveloper().isBlank()
                || game.getReleaseYear() == null
                || game.getReleaseDate() == null
                || game.getGenres() == null
                || game.getGenres().isBlank();
    }

    private static boolean isCooldownElapsed(LocalDateTime lastAttemptAt) {
        if (lastAttemptAt == null) {
            return true;
        }
        return lastAttemptAt.plusMinutes(IGDB_RETRY_COOLDOWN_MINUTES).isBefore(LocalDateTime.now());
    }
}
