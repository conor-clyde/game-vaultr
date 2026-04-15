package com.cocoding.playstate.model;

import com.cocoding.playstate.domain.enums.PlaythroughProgressStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "user_game_playthroughs")
public class UserGamePlaythrough {

  public static final int MAX_PER_USER_GAME = 10;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "game_id", nullable = false)
  private Long gameId;

  @Column(name = "sort_index", nullable = false)
  private int sortIndex;

  @Column(name = "difficulty", length = 128)
  private String difficulty;

  @Column(name = "is_current", nullable = false)
  private boolean current;

  @Column(name = "manual_play_minutes")
  private Integer manualPlayMinutes;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "short_name", length = 64)
  private String shortName;

  @Column(name = "completion_percent")
  private Integer completionPercent;

  @Column(name = "progress_note", length = 512)
  private String progressNote;

  @Convert(converter = PlaythroughProgressStatusConverter.class)
  @Column(name = "progress_status", length = 32)
  private PlaythroughProgressStatus progressStatus;

  @PrePersist
  void onPrePersist() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    if (progressStatus == null) {
      progressStatus = PlaythroughProgressStatus.PLAYING;
    }
  }

  public String getCreatedDateDisplayValue() {
    if (createdAt == null) {
      return null;
    }
    return createdAt.format(DateTimeFormatter.ofPattern("MMM d, yyyy"));
  }

  public String getEndedDateInputValue() {
    if (endedAt == null) {
      return null;
    }
    return LocalDate.ofInstant(endedAt, ZoneId.systemDefault())
        .format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  public String getEndedDateDisplayValue() {
    if (endedAt == null) {
      return null;
    }
    return LocalDate.ofInstant(endedAt, ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy"));
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

  public int getSortIndex() {
    return sortIndex;
  }

  public void setSortIndex(int sortIndex) {
    this.sortIndex = sortIndex;
  }

  public String getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(String difficulty) {
    this.difficulty = difficulty;
  }

  public boolean isCurrent() {
    return current;
  }

  public void setCurrent(boolean current) {
    this.current = current;
  }

  public Integer getManualPlayMinutes() {
    return manualPlayMinutes;
  }

  public void setManualPlayMinutes(Integer manualPlayMinutes) {
    this.manualPlayMinutes = manualPlayMinutes;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public void setEndedAt(Instant endedAt) {
    this.endedAt = endedAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public String getShortName() {
    return shortName;
  }

  public void setShortName(String shortName) {
    this.shortName = shortName;
  }

  public Integer getCompletionPercent() {
    return completionPercent;
  }

  public void setCompletionPercent(Integer completionPercent) {
    this.completionPercent = completionPercent;
  }

  public String getProgressNote() {
    return progressNote;
  }

  public void setProgressNote(String progressNote) {
    this.progressNote = progressNote;
  }

  public PlaythroughProgressStatus getProgressStatus() {
    return progressStatus != null ? progressStatus : PlaythroughProgressStatus.PLAYING;
  }

  public void setProgressStatus(PlaythroughProgressStatus progressStatus) {
    this.progressStatus = progressStatus;
  }
}
