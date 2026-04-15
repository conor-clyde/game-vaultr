package com.cocoding.playstate.igdb;

import com.cocoding.playstate.catalog.PlatformCatalog;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class IgdbService {

  private static final Logger logger = LoggerFactory.getLogger(IgdbService.class);

  private static final String IGDB_API_BASE_URL = "https://api.igdb.com/v4";
  private static final String IGDB_GAMES_PATH = "/games";
  private static final String IGDB_POPULARITY_PRIMITIVES_PATH = "/popularity_primitives";

  private static final String SEARCH_WHERE_BASE =
      "game_type = (0, 4, 8, 9, 10, 11) & version_parent = null";

  private static final String GAME_LIST_FIELDS =
      "id,name,cover.image_id,platforms.name,genres.name,game_modes,first_release_date,total_rating";

  private static final String GAME_LIST_FIELDS_WITH_SUMMARY = GAME_LIST_FIELDS + ",summary";

  private static final int IGDB_RETRY_COUNT = 3;
  private static final long IGDB_RETRY_PAUSE_MS = 400L;
  private static final int POPULARITY_TYPE_PLAYING = 3;
  private static final int POPULARITY_TYPE_PLAYED = 4;
  private static final double POP_WEIGHT_PLAYING = 0.5d;
  private static final double POP_WEIGHT_PLAYED = 0.5d;

  private final IgdbTokenService tokenService;
  private final RestTemplate restTemplate = new RestTemplate();

  public IgdbService(IgdbTokenService tokenService) {
    this.tokenService = tokenService;
  }

  public List<Map<String, Object>> searchGames(
      String query, int limit, int offset, Set<String> platformNames, int yearMin, int yearMax) {
    if (query == null || query.trim().isEmpty()) {
      return Collections.emptyList();
    }

    Set<Integer> platformIds = mapPlatformNamesToIds(platformNames);
    String whereClause = buildSearchWhereClause(platformIds, yearMin, yearMax);
    int boundedLimit = Math.min(Math.max(limit, 1), 50);
    int boundedOffset = Math.max(offset, 0);

    String body =
        String.format(
            "fields %s;search \"%s\";where %s;limit %d;offset %d;",
            GAME_LIST_FIELDS,
            escapeIgdbQuotedString(query),
            whereClause,
            boundedLimit,
            boundedOffset);

    logger.info(
        "IGDB search request (Postman): POST {} body: {}",
        IGDB_API_BASE_URL + IGDB_GAMES_PATH,
        body);

    List<Map<String, Object>> response = postIgdbQuery(IGDB_GAMES_PATH, body);

    logger.info("IGDB search: {} offset {} -> {} results", query, offset, response.size());
    return response;
  }

  public Map<Long, Double> fetchPopularityScores(Set<Long> gameIds) {
    if (gameIds == null || gameIds.isEmpty()) {
      return Collections.emptyMap();
    }

    String idList = gameIds.stream().map(String::valueOf).collect(Collectors.joining(","));

    String requestBody =
        String.format(
            "fields game_id,value,popularity_type;"
                + "where popularity_type = (%d,%d) & game_id = (%s);"
                + "limit %d;",
            POPULARITY_TYPE_PLAYING, POPULARITY_TYPE_PLAYED, idList, gameIds.size() * 2);

    try {
      List<Map<String, Object>> response =
          postIgdbQuery(IGDB_POPULARITY_PRIMITIVES_PATH, requestBody);
      if (response.isEmpty()) {
        return Collections.emptyMap();
      }

      Map<Long, Double> playingByGameId = new HashMap<>();
      Map<Long, Double> playedByGameId = new HashMap<>();
      for (Map<String, Object> row : response) {
        Object gameIdObj = row.get("game_id");
        Object valueObj = row.get("value");
        Object typeObj = row.get("popularity_type");
        if (!(gameIdObj instanceof Number gid)
            || !(valueObj instanceof Number val)
            || !(typeObj instanceof Number typ)) {
          continue;
        }
        long gameId = gid.longValue();
        double value = val.doubleValue();
        int type = typ.intValue();
        if (type == POPULARITY_TYPE_PLAYING) {
          playingByGameId.put(gameId, value);
        } else if (type == POPULARITY_TYPE_PLAYED) {
          playedByGameId.put(gameId, value);
        }
      }

      Map<Long, Double> scores = new HashMap<>();
      for (Long id : gameIds) {
        double playing = playingByGameId.getOrDefault(id, 0d);
        double played = playedByGameId.getOrDefault(id, 0d);
        double score = POP_WEIGHT_PLAYED * played + POP_WEIGHT_PLAYING * playing;
        if (score > 0d) {
          scores.put(id, score);
        }
      }
      return scores;
    } catch (Exception e) {
      logger.warn(
          "Failed to fetch popularity primitives for games {}: {}", gameIds, e.getMessage());
      return Collections.emptyMap();
    }
  }

  public List<String> getPlatformOptionsForGame(String apiId, String fallbackPlatform) {
    if (apiId == null || apiId.isBlank()) {
      return fallbackPlatformList(fallbackPlatform);
    }
    try {
      long igdbId = Long.parseLong(apiId.trim());
      Map<String, Object> game = fetchGameByIdWithSummary(igdbId);
      if (game != null) {
        List<String> names = extractPlatformNames(game.get("platforms"));
        if (!names.isEmpty()) {
          return names;
        }
      }
    } catch (NumberFormatException ignored) {
    }
    return fallbackPlatformList(fallbackPlatform);
  }

  /**
   * Fetches several games in one IGDB request (used for demo seeding). Order of results is not
   * guaranteed.
   */
  public List<Map<String, Object>> fetchGamesByIgdbIds(long[] ids) {
    if (ids == null || ids.length == 0) {
      return List.of();
    }
    String idList = Arrays.stream(ids).mapToObj(String::valueOf).collect(Collectors.joining(","));
    int limit = Math.min(ids.length, 50);
    String body =
        String.format(
            "fields %s;where id = (%s);limit %d;", GAME_LIST_FIELDS_WITH_SUMMARY, idList, limit);
    try {
      return postIgdbQuery(IGDB_GAMES_PATH, body);
    } catch (IgdbException e) {
      logger.warn("IGDB batch game fetch failed: {}", e.getMessage());
      return List.of();
    }
  }

  /** Single-game snapshot (same payload as search rows / enrichment). */
  public Map<String, Object> fetchGameSnapshot(long igdbId) {
    return fetchGameByIdWithSummary(igdbId);
  }

  public Map<String, Object> getGamePublisherAndYear(long igdbId) {
    String fields =
        "first_release_date,involved_companies.company.name,involved_companies.publisher,involved_companies.developer,genres.name";
    Map<String, Object> result = new HashMap<>();
    String body = String.format("fields %s;where id = %d;limit 1;", fields, igdbId);
    try {
      List<Map<String, Object>> response = postIgdbQuery(IGDB_GAMES_PATH, body);
      if (response.isEmpty()) {
        return result;
      }

      Map<String, Object> game = response.get(0);
      Object dateObj = game.get("first_release_date");
      if (dateObj instanceof Number) {
        long epochSec = ((Number) dateObj).longValue();
        LocalDate releaseDate =
            Instant.ofEpochSecond(epochSec).atZone(ZoneOffset.UTC).toLocalDate();
        result.put("releaseDate", releaseDate);
        result.put("releaseYear", releaseDate.getYear());
      }
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> involved =
          (List<Map<String, Object>>) game.get("involved_companies");
      if (involved != null) {
        for (Map<String, Object> inv : involved) {
          if (Boolean.TRUE.equals(inv.get("publisher"))) {
            Map<?, ?> company = (Map<?, ?>) inv.get("company");
            if (company != null && company.get("name") != null) {
              result.put("publisher", company.get("name").toString());
              break;
            }
          }
        }
        for (Map<String, Object> inv : involved) {
          if (Boolean.TRUE.equals(inv.get("developer"))) {
            Map<?, ?> company = (Map<?, ?>) inv.get("company");
            if (company != null && company.get("name") != null) {
              result.put("developer", company.get("name").toString());
              break;
            }
          }
        }
      }
      List<String> genreNames = extractPlatformNames(game.get("genres"));
      if (!genreNames.isEmpty()) {
        result.put("genreNames", genreNames);
      }
    } catch (Exception e) {
      logger.warn("Failed to fetch game {} publisher/year: {}", igdbId, e.getMessage());
    }
    return result;
  }

  private HttpHeaders buildIgdbHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Client-ID", tokenService.getClientId());
    headers.set("Authorization", "Bearer " + tokenService.getAccessToken());
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private List<Map<String, Object>> postIgdbQuery(String igdbPath, String body) {
    String fullUrl = IGDB_API_BASE_URL + igdbPath;
    Exception lastError = null;

    for (int attempt = 1; attempt <= IGDB_RETRY_COUNT; attempt++) {
      try {
        HttpEntity<String> entity = new HttpEntity<>(body, buildIgdbHeaders());
        ResponseEntity<List<Map<String, Object>>> response =
            restTemplate.exchange(
                fullUrl,
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        List<Map<String, Object>> responseBody = response.getBody();
        return responseBody != null ? responseBody : Collections.emptyList();
      } catch (Exception e) {
        lastError = e;
        logger.warn(
            "IGDB request failed (try {}/{}): {} — {}",
            attempt,
            IGDB_RETRY_COUNT,
            fullUrl,
            e.toString());
        if (e instanceof HttpClientErrorException h && h.getStatusCode().value() == 401) {
          tokenService.invalidateAccessToken();
        }
        if (attempt < IGDB_RETRY_COUNT) {
          sleepQuiet(IGDB_RETRY_PAUSE_MS * attempt);
        }
      }
    }

    logger.error("IGDB still failing after {} tries: {}", IGDB_RETRY_COUNT, fullUrl, lastError);
    throw new IgdbException(lastError);
  }

  private static void sleepQuiet(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IgdbException(e);
    }
  }

  private Set<Integer> mapPlatformNamesToIds(Set<String> platformNames) {
    if (platformNames == null || platformNames.isEmpty()) {
      return Collections.emptySet();
    }
    Set<Integer> idSet = new LinkedHashSet<>();
    for (String name : platformNames) {
      String key = name != null ? name.trim() : "";
      if (key.isEmpty()) {
        continue;
      }
      Integer id = PlatformCatalog.IDS_BY_IGDB_NAME.get(key);
      if (id != null) {
        idSet.add(id);
      }
    }
    return idSet;
  }

  private static String escapeIgdbQuotedString(String s) {
    return s == null ? "" : s.replace("\"", "\\\"");
  }

  private static void appendYearRangeClause(StringBuilder where, int yearMin, int yearMax) {
    if (yearMin <= IgdbConstants.RELEASE_YEAR_MIN && yearMax >= IgdbConstants.RELEASE_YEAR_MAX) {
      return;
    }

    long startSec =
        java.time.Year.of(Math.max(yearMin, 1970))
            .atDay(1)
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toEpochSecond();
    long endSec =
        java.time.Year.of(Math.min(yearMax, 2100))
            .atMonth(12)
            .atEndOfMonth()
            .atTime(23, 59, 59)
            .atZone(java.time.ZoneOffset.UTC)
            .toEpochSecond();
    where
        .append(" & first_release_date >= ")
        .append(startSec)
        .append(" & first_release_date <= ")
        .append(endSec);
  }

  private static String buildSearchWhereClause(Set<Integer> platformIds, int yearMin, int yearMax) {
    StringBuilder where = new StringBuilder(SEARCH_WHERE_BASE);
    if (!platformIds.isEmpty()) {
      where
          .append(" & platforms = (")
          .append(platformIds.stream().map(String::valueOf).collect(Collectors.joining(", ")))
          .append(")");
    }
    appendYearRangeClause(where, yearMin, yearMax);
    return where.toString();
  }

  private static List<String> extractPlatformNames(Object platformsField) {
    if (!(platformsField instanceof List<?>)) {
      return Collections.emptyList();
    }
    List<?> list = (List<?>) platformsField;
    List<String> names = new ArrayList<>();
    for (Object p : list) {
      if (p instanceof Map<?, ?> pm) {
        Object nameObj = pm.get("name");
        if (nameObj != null) {
          String n = nameObj.toString().trim();
          if (!n.isEmpty()) {
            names.add(n);
          }
        }
      }
    }
    return names;
  }

  private Map<String, Object> fetchGameByIdWithSummary(long gameId) {
    String body =
        String.format("fields %s;where id = %d;limit 1;", GAME_LIST_FIELDS_WITH_SUMMARY, gameId);

    try {
      List<Map<String, Object>> response = postIgdbQuery(IGDB_GAMES_PATH, body);
      if (!response.isEmpty()) {
        return response.get(0);
      }
    } catch (Exception e) {
      logger.warn("Failed to fetch game {} with summary: {}", gameId, e.getMessage());
    }
    return null;
  }

  private static List<String> fallbackPlatformList(String fallbackPlatform) {
    if (fallbackPlatform != null && !fallbackPlatform.isBlank()) {
      return Collections.singletonList(fallbackPlatform.trim());
    }
    return Collections.emptyList();
  }
}
