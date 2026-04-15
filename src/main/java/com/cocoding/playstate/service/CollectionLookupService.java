package com.cocoding.playstate.service;

import com.cocoding.playstate.model.Game;
import com.cocoding.playstate.model.UserGame;
import com.cocoding.playstate.repository.GameRepository;
import com.cocoding.playstate.repository.UserGameRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CollectionLookupService {

    private final GameRepository gameRepository;
    private final UserGameRepository userGameRepository;

    public CollectionLookupService(GameRepository gameRepository, UserGameRepository userGameRepository) {
        this.gameRepository = gameRepository;
        this.userGameRepository = userGameRepository;
    }

    
    public record OwnedGame(String apiIdKey, Game game, UserGame userGame) {}

    
    public Optional<OwnedGame> findOwnedGame(String userId, String apiIdRaw) {
        if (apiIdRaw == null || apiIdRaw.isBlank()) {
            return Optional.empty();
        }
        String key = apiIdRaw.trim();
        return gameRepository
                .findByApiId(key)
                .flatMap(
                        game ->
                                userGameRepository
                                        .findByUserIdAndGameId(userId, game.getId())
                                        .map(ug -> new OwnedGame(key, game, ug)));
    }
}
