package com.cocoding.playstate.repository;

import com.cocoding.playstate.model.Game;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {
  Optional<Game> findByApiId(String apiId);
}
