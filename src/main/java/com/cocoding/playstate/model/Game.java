package com.cocoding.playstate.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "game")
public class Game {
  private static final String PLATFORM_OPTIONS_SEPARATOR = "\u001f";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "api_id", unique = true, nullable = false)
  private String apiId;

  @Column(nullable = false)
  private String title;

  @Column(name = "image_url")
  private String imageUrl;

  @Column(name = "publisher")
  private String publisher;

  @Column(name = "developer")
  private String developer;

  @Column(name = "release_year")
  private Integer releaseYear;

  /** IGDB {@code first_release_date} when known; display as full calendar date. */
  @Column(name = "release_date")
  private LocalDate releaseDate;

  @Column(name = "genres", length = 2000)
  private String genres;

  @Column(name = "platform_options", length = 4000)
  private String platformOptions;

  @Column(name = "igdb_last_attempt_at")
  private LocalDateTime igdbLastAttemptAt;

  @Column(name = "last_updated")
  private LocalDateTime lastUpdated;

  public Game() {}

  public Game(String apiId, String title, String imageUrl) {
    this.apiId = apiId;
    this.title = title;
    this.imageUrl = imageUrl;
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getApiId() {
    return apiId;
  }

  public void setApiId(String apiId) {
    this.apiId = apiId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public String getPublisher() {
    return publisher;
  }

  public void setPublisher(String publisher) {
    this.publisher = publisher;
  }

  public String getDeveloper() {
    return developer;
  }

  public void setDeveloper(String developer) {
    this.developer = developer;
  }

  public Integer getReleaseYear() {
    return releaseYear;
  }

  public void setReleaseYear(Integer releaseYear) {
    this.releaseYear = releaseYear;
  }

  public LocalDate getReleaseDate() {
    return releaseDate;
  }

  public void setReleaseDate(LocalDate releaseDate) {
    this.releaseDate = releaseDate;
  }

  public String getGenres() {
    return genres;
  }

  public void setGenres(String genres) {
    this.genres = genres;
  }

  public List<String> getPlatformOptionsList() {
    if (platformOptions == null || platformOptions.isBlank()) {
      return List.of();
    }
    String[] parts = platformOptions.split(PLATFORM_OPTIONS_SEPARATOR);
    List<String> out = new ArrayList<>(parts.length);
    for (String part : parts) {
      if (part != null) {
        String p = part.trim();
        if (!p.isEmpty()) {
          out.add(p);
        }
      }
    }
    return out;
  }

  public void setPlatformOptionsList(List<String> options) {
    if (options == null || options.isEmpty()) {
      this.platformOptions = null;
      return;
    }
    Set<String> unique = new LinkedHashSet<>();
    for (String option : options) {
      if (option == null) {
        continue;
      }
      String normalized = option.trim();
      if (!normalized.isEmpty()) {
        unique.add(normalized);
      }
    }
    if (unique.isEmpty()) {
      this.platformOptions = null;
      return;
    }
    this.platformOptions = String.join(PLATFORM_OPTIONS_SEPARATOR, unique);
  }

  public LocalDateTime getIgdbLastAttemptAt() {
    return igdbLastAttemptAt;
  }

  public void setIgdbLastAttemptAt(LocalDateTime igdbLastAttemptAt) {
    this.igdbLastAttemptAt = igdbLastAttemptAt;
  }

  public LocalDateTime getLastUpdated() {
    return lastUpdated;
  }

  public void setLastUpdated(LocalDateTime lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  @PrePersist
  @PreUpdate
  protected void touchLastUpdated() {
    lastUpdated = LocalDateTime.now();
  }
}
