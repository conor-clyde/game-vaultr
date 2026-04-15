package com.cocoding.playstate.repository;

import com.cocoding.playstate.model.UserGamePlaythrough;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserGamePlaythroughRepository extends JpaRepository<UserGamePlaythrough, Long> {

    List<UserGamePlaythrough> findByUserIdAndGameIdOrderBySortIndexAscIdAsc(String userId, Long gameId);

    long countByUserIdAndGameId(String userId, Long gameId);

    void deleteByUserIdAndGameId(String userId, Long gameId);
}