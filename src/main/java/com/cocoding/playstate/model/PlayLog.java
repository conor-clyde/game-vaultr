package com.cocoding.playstate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_play_logs")
public class PlayLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "played_at", nullable = false)
    private LocalDateTime playedAt;

    
    @Column(name = "session_started_at")
    private LocalDateTime sessionStartedAt;

    @Column(name = "note", length = 1000)
    private String note;

    
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "note_contains_spoilers")
    private Boolean noteContainsSpoilers;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_progress", length = 32)
    private PlayLogSessionProgress sessionProgress;

    @Convert(converter = PlayLogSessionExperienceAttributeConverter.class)
    @Column(name = "session_experience", length = 32)
    private PlayLogSessionExperience sessionExperience;

    
    @Column(name = "counts_toward_library_playtime", nullable = false)
    private boolean countsTowardLibraryPlaytime = true;

    
    @Column(name = "playthrough_id")
    private Long playthroughId;

    public PlayLog() {
    }

    public PlayLog(String userId, Long gameId, LocalDateTime playedAt, Integer durationMinutes, String note) {
        this(userId, gameId, playedAt, durationMinutes, note, false, null, null);
    }

    public PlayLog(
            String userId,
            Long gameId,
            LocalDateTime playedAt,
            Integer durationMinutes,
            String note,
            boolean noteContainsSpoilers) {
        this(userId, gameId, playedAt, durationMinutes, note, noteContainsSpoilers, null, null);
    }

    public PlayLog(
            String userId,
            Long gameId,
            LocalDateTime playedAt,
            Integer durationMinutes,
            String note,
            boolean noteContainsSpoilers,
            PlayLogSessionProgress sessionProgress,
            PlayLogSessionExperience sessionExperience) {
        this.userId = userId;
        this.gameId = gameId;
        this.playedAt = playedAt;
        this.durationMinutes = durationMinutes;
        this.note = note;
        this.noteContainsSpoilers = noteContainsSpoilers;
        this.sessionProgress = sessionProgress;
        this.sessionExperience = sessionExperience;
        this.countsTowardLibraryPlaytime = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getGameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public LocalDateTime getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(LocalDateTime playedAt) {
        this.playedAt = playedAt;
    }

    public LocalDateTime getSessionStartedAt() {
        return sessionStartedAt;
    }

    public void setSessionStartedAt(LocalDateTime sessionStartedAt) {
        this.sessionStartedAt = sessionStartedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public boolean isNoteContainsSpoilers() {
        return Boolean.TRUE.equals(noteContainsSpoilers);
    }

    public void setNoteContainsSpoilers(boolean noteContainsSpoilers) {
        this.noteContainsSpoilers = noteContainsSpoilers;
    }

    public PlayLogSessionProgress getSessionProgress() {
        return sessionProgress;
    }

    public void setSessionProgress(PlayLogSessionProgress sessionProgress) {
        this.sessionProgress = sessionProgress;
    }

    public PlayLogSessionExperience getSessionExperience() {
        return sessionExperience;
    }

    public void setSessionExperience(PlayLogSessionExperience sessionExperience) {
        this.sessionExperience = sessionExperience;
    }

    public boolean isCountsTowardLibraryPlaytime() {
        return countsTowardLibraryPlaytime;
    }

    public void setCountsTowardLibraryPlaytime(boolean countsTowardLibraryPlaytime) {
        this.countsTowardLibraryPlaytime = countsTowardLibraryPlaytime;
    }

    public Long getPlaythroughId() {
        return playthroughId;
    }

    public void setPlaythroughId(Long playthroughId) {
        this.playthroughId = playthroughId;
    }
}
