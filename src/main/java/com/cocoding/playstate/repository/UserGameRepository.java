package com.cocoding.playstate.repository;

import com.cocoding.playstate.model.UserGame;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserGameRepository extends JpaRepository<UserGame, UserGame.UserGameId> {
  List<UserGame> findByUserId(String userId);

  boolean existsByUserIdAndGameId(String userId, Long gameId);

  Optional<UserGame> findByUserIdAndGameId(String userId, Long gameId);

  void deleteByUserIdAndGameId(String userId, Long gameId);

  List<UserGame> findByUserIdAndGame_ApiIdIn(String userId, Collection<String> apiIds);
}
