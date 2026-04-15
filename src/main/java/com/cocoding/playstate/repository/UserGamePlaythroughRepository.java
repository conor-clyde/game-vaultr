package com.cocoding.playstate.repository;

import com.cocoding.playstate.model.UserGamePlaythrough;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserGamePlaythroughRepository extends JpaRepository<UserGamePlaythrough, Long> {

  List<UserGamePlaythrough> findByUserIdAndGameIdOrderBySortIndexAscIdAsc(
      String userId, Long gameId);

  long countByUserIdAndGameId(String userId, Long gameId);

  @Query(
      """
      SELECT p.gameId, COUNT(p)
      FROM UserGamePlaythrough p
      WHERE p.userId = :userId AND p.gameId IN :gameIds
      GROUP BY p.gameId
      """)
  List<Object[]> countByUserIdAndGameIdIn(
      @Param("userId") String userId, @Param("gameIds") List<Long> gameIds);

  void deleteByUserIdAndGameId(String userId, Long gameId);
}
