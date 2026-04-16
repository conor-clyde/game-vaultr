package com.cocoding.playstate.model;

import com.cocoding.playstate.domain.enums.CompletionType;
import com.cocoding.playstate.domain.enums.GameStatus;
import com.cocoding.playstate.domain.enums.OwnershipType;
import com.cocoding.playstate.domain.enums.WhyPlaying;
import com.cocoding.playstate.util.ReflectionTagsJson;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "user_games")
@IdClass(UserGame.UserGameId.class)
public class UserGame {

  @Id
  @Column(name = "user_id", nullable = false)
  private String userId;

  @Id
  @Column(name = "game_id", nullable = false)
  private Long gameId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "game_id", insertable = false, updatable = false)
  private Game game;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private GameStatus status = GameStatus.NOT_PLAYING;

  @Column(name = "platform")
  private String platform;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "completion_type")
  private CompletionType completionType = CompletionType.NOT_COMPLETED;

  @Enumerated(EnumType.STRING)
  @Column(name = "ownership_type")
  private OwnershipType ownershipType;

  @Column(name = "notes", length = 4000)
  private String notes;

  @Column(name = "rating")
  private Integer rating;

  @Column(name = "review", length = 3000)
  private String review;

  @Column(name = "reflection_tags_json", length = 800)
  private String reflectionTagsJson;

  @Column(name = "reflection_highlight", length = 500)
  private String reflectionHighlight;

  /** Comma-separated {@link WhyPlaying} names; DB column predates the rebrand. */
  @Column(name = "play_intent")
  private String whyPlayingCsv;

  @Column(name = "started_date")
  private LocalDate startedDate;

  @Column(name = "finished_date")
  private LocalDate finishedDate;

  @Column(name = "difficulty", length = 128)
  private String difficulty;

  @Column(name = "times_played")
  private Integer timesPlayed;

  @Column(name = "progress_label", length = 500)
  private String progressLabel;

  @Column(name = "progress_percent")
  private Integer progressPercent;

  @Column(name = "progress_updated_at")
  private LocalDateTime progressUpdatedAt;

  @Column(name = "play_time_manual_total_minutes")
  private Integer playTimeManualTotalMinutes;

  @Column(name = "play_time_manual_anchor_log_minutes")
  private Integer playTimeManualAnchorLogMinutes;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    if (completionType == null) {
      completionType = CompletionType.NOT_COMPLETED;
    }
    if (status == null) {
      status = GameStatus.NOT_PLAYING;
    }
  }

  public UserGame() {}

  public UserGame(String userId, Long gameId) {
    this.userId = userId;
    this.gameId = gameId;
    this.status = GameStatus.NOT_PLAYING;
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

  public Game getGame() {
    return game;
  }

  public void setGame(Game game) {
    this.game = game;
  }

  public GameStatus getStatus() {
    return status;
  }

  public void setStatus(GameStatus status) {
    this.status = status;
  }

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public CompletionType getCompletionType() {
    return completionType != null ? completionType : CompletionType.NOT_COMPLETED;
  }

  public void setCompletionType(CompletionType completionType) {
    this.completionType = completionType != null ? completionType : CompletionType.NOT_COMPLETED;
  }

  public OwnershipType getOwnershipType() {
    return ownershipType;
  }

  public void setOwnershipType(OwnershipType ownershipType) {
    this.ownershipType = ownershipType;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Integer getRating() {
    return rating;
  }

  public void setRating(Integer rating) {
    this.rating = rating;
  }

  public String getReview() {
    return review;
  }

  public void setReview(String review) {
    this.review = review;
  }

  public String getReflectionTagsJson() {
    return reflectionTagsJson;
  }

  public void setReflectionTagsJson(String reflectionTagsJson) {
    this.reflectionTagsJson = reflectionTagsJson;
  }

  public String getReflectionHighlight() {
    return reflectionHighlight;
  }

  public void setReflectionHighlight(String reflectionHighlight) {
    this.reflectionHighlight = reflectionHighlight;
  }

  public List<String> getReflectionTags() {
    return ReflectionTagsJson.deserialize(reflectionTagsJson);
  }

  public List<String> getReflectionTagsDisplay() {
    return getReflectionTags().stream()
        .map(t -> ReflectionTagsJson.toDisplayLabel(ReflectionTagsJson.normalizeTag(t, 30)))
        .filter(s -> !s.isEmpty())
        .toList();
  }

  public List<WhyPlaying> getWhyPlayings() {
    if (whyPlayingCsv == null || whyPlayingCsv.isBlank()) {
      return List.of();
    }
    List<WhyPlaying> out = new ArrayList<>();
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    for (String part : whyPlayingCsv.split(",")) {
      String t = part.trim();
      if (t.isEmpty()) {
        continue;
      }
      WhyPlaying resolved = WhyPlaying.fromExternalName(t);
      if (resolved != null && seen.add(resolved.name()) && out.size() < WhyPlaying.MAX_PER_GAME) {
        out.add(resolved);
      }
    }
    return List.copyOf(out);
  }

  public void setWhyPlayings(List<WhyPlaying> reasons) {
    if (reasons == null || reasons.isEmpty()) {
      this.whyPlayingCsv = null;
      return;
    }
    List<String> names = new ArrayList<>();
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    for (WhyPlaying p : reasons) {
      if (p != null && seen.add(p.name()) && names.size() < WhyPlaying.MAX_PER_GAME) {
        names.add(p.name());
      }
    }
    this.whyPlayingCsv = names.isEmpty() ? null : String.join(",", names);
  }

  public void setWhyPlaying(WhyPlaying reason) {
    setWhyPlayings(reason == null ? List.of() : List.of(reason));
  }

  public String getWhyPlayingNamesCsv() {
    List<WhyPlaying> list = getWhyPlayings();
    if (list.isEmpty()) {
      return "";
    }
    List<String> names = new ArrayList<>(list.size());
    for (WhyPlaying p : list) {
      names.add(p.name());
    }
    return String.join(",", names);
  }

  public String getWhyPlayingBadgeSummary() {
    List<WhyPlaying> list = getWhyPlayings();
    if (list.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) {
        sb.append(" · ");
      }
      sb.append(list.get(i).getBadgeShort());
    }
    return sb.toString();
  }

  public LocalDate getStartedDate() {
    return startedDate;
  }

  public void setStartedDate(LocalDate startedDate) {
    this.startedDate = startedDate;
  }

  public LocalDate getFinishedDate() {
    return finishedDate;
  }

  public void setFinishedDate(LocalDate finishedDate) {
    this.finishedDate = finishedDate;
  }

  public String getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(String difficulty) {
    this.difficulty = difficulty;
  }

  public Integer getTimesPlayed() {
    return timesPlayed;
  }

  public void setTimesPlayed(Integer timesPlayed) {
    this.timesPlayed = timesPlayed;
  }

  public String getProgressLabel() {
    return progressLabel;
  }

  public void setProgressLabel(String progressLabel) {
    this.progressLabel = progressLabel;
  }

  public Integer getProgressPercent() {
    return progressPercent;
  }

  public void setProgressPercent(Integer progressPercent) {
    this.progressPercent = progressPercent;
  }

  public LocalDateTime getProgressUpdatedAt() {
    return progressUpdatedAt;
  }

  public void setProgressUpdatedAt(LocalDateTime progressUpdatedAt) {
    this.progressUpdatedAt = progressUpdatedAt;
  }

  public Integer getPlayTimeManualTotalMinutes() {
    return playTimeManualTotalMinutes;
  }

  public void setPlayTimeManualTotalMinutes(Integer playTimeManualTotalMinutes) {
    this.playTimeManualTotalMinutes = playTimeManualTotalMinutes;
  }

  public Integer getPlayTimeManualAnchorLogMinutes() {
    return playTimeManualAnchorLogMinutes;
  }

  public void setPlayTimeManualAnchorLogMinutes(Integer playTimeManualAnchorLogMinutes) {
    this.playTimeManualAnchorLogMinutes = playTimeManualAnchorLogMinutes;
  }

  public static int computeDisplayPlayMinutes(UserGame ug, int logSumMinutes) {
    if (ug == null || ug.getPlayTimeManualTotalMinutes() == null) {
      return Math.max(0, logSumMinutes);
    }
    int anchor =
        ug.getPlayTimeManualAnchorLogMinutes() != null ? ug.getPlayTimeManualAnchorLogMinutes() : 0;
    int manual = ug.getPlayTimeManualTotalMinutes();
    return Math.max(0, manual + (logSumMinutes - anchor));
  }

  public static class UserGameId implements Serializable {

    private String userId;
    private Long gameId;

    public UserGameId() {}

    public UserGameId(String userId, Long gameId) {
      this.userId = userId;
      this.gameId = gameId;
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

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      UserGameId that = (UserGameId) o;
      return Objects.equals(userId, that.userId) && Objects.equals(gameId, that.gameId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(userId, gameId);
    }
  }
}
