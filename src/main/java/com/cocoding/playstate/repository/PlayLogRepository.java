package com.cocoding.playstate.repository;

import com.cocoding.playstate.model.PlayLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayLogRepository extends JpaRepository<PlayLog, Long> {

    long countByUserId(String userId);

    List<PlayLog> findTop40ByUserIdOrderByPlayedAtDescIdDesc(String userId);

    Page<PlayLog> findByUserIdAndGameIdOrderByPlayedAtDescIdDesc(String userId, Long gameId, Pageable pageable);

    Page<PlayLog> findByUserIdAndGameIdAndPlaythroughIdOrderByPlayedAtDescIdDesc(
            String userId, Long gameId, Long playthroughId, Pageable pageable);

    Optional<PlayLog> findFirstByUserIdAndGameIdAndSessionStartedAtIsNotNullAndDurationMinutesIsNullOrderBySessionStartedAtDescIdDesc(
            String userId, Long gameId);

    /**
     * Most recent open play session for this user (any game), if any — caller should enforce
     * end-session window (e.g. 24h) and ownership.
     */
    Optional<PlayLog> findFirstByUserIdAndSessionStartedAtIsNotNullAndDurationMinutesIsNullOrderBySessionStartedAtDescIdDesc(
            String userId);

    long countByUserIdAndGameId(String userId, Long gameId);

    @Query(
            """
            SELECT COALESCE(SUM(p.durationMinutes), 0L) FROM PlayLog p
            WHERE p.userId = :userId AND p.gameId = :gameId
            AND (p.countsTowardLibraryPlaytime IS NULL OR p.countsTowardLibraryPlaytime = true)
            """)
    long sumDurationMinutesByUserIdAndGameId(@Param("userId") String userId, @Param("gameId") Long gameId);

    @Query(
            """
            SELECT COALESCE(SUM(p.durationMinutes), 0L) FROM PlayLog p
            WHERE p.userId = :userId AND p.gameId = :gameId
            AND (p.countsTowardLibraryPlaytime IS NULL OR p.countsTowardLibraryPlaytime = true)
            AND (:excludeLogId IS NULL OR p.id <> :excludeLogId)
            """)
    long sumDurationMinutesByUserIdAndGameIdExcludingLog(
            @Param("userId") String userId,
            @Param("gameId") Long gameId,
            @Param("excludeLogId") Long excludeLogId);

    void deleteByUserIdAndGameId(String userId, Long gameId);

    
    void deleteByUserIdAndGameIdAndPlaythroughId(String userId, Long gameId, Long playthroughId);

    long countByUserIdAndGameIdAndPlaythroughId(String userId, Long gameId, Long playthroughId);

    @Modifying
    @Query("UPDATE PlayLog p SET p.playthroughId = NULL WHERE p.userId = :userId AND p.gameId = :gameId")
    void clearPlaythroughIdForUserAndGame(@Param("userId") String userId, @Param("gameId") Long gameId);
}
