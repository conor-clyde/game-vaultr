package com.cocoding.playstate.repository;

import com.cocoding.playstate.model.UserGamePlaythrough;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserGamePlaythroughRepository extends JpaRepository<UserGamePlaythrough, Long> {

  List<UserGamePlaythrough> findByUserIdAndGameIdOrderBySortIndexAscIdAsc(
      String userId, Long gameId);

  long countByUserIdAndGameId(String userId, Long gameId);

  void deleteByUserIdAndGameId(String userId, Long gameId);
}
