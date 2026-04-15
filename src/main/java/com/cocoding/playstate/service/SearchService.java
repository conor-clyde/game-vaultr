package com.cocoding.playstate.service;

import com.cocoding.playstate.dto.SearchGameRow;
import com.cocoding.playstate.dto.SearchResult;
import com.cocoding.playstate.dto.SearchSessionState;
import com.cocoding.playstate.igdb.IgdbConstants;
import com.cocoding.playstate.igdb.IgdbException;
import com.cocoding.playstate.repository.UserGameRepository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);

    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGES = 10;
    private static final int MERGED_FETCH_LIMIT = MAX_PAGES * PAGE_SIZE;

    private static final double POPULAR_BADGE_MIN_SCORE = 0.5 * 0.000248440082941 + 0.5 * 0.000297412905334;
    private static final double RELEVANCE_VERY_LOW_ENGAGEMENT_MAX = POPULAR_BADGE_MIN_SCORE * 0.1d;

    private static final int DISCOVERY_LOVED_MIN_REVIEW_SCORE = 85;
    private static final int DISCOVERY_NEW_RELEASE_LOOKBACK_DAYS = 365;

    private static final String SORT_RELEVANCE = "relevance";
    private static final String SORT_POPULARITY = "popularity";
    private static final String SORT_RELEASE_NEWEST = "release_newest";

    private static final SearchResult EMPTY_SEARCH = new SearchResult(Collections.emptyList(), false, null);

    private static final int MERGED_WINDOW_CACHE_MAX_ENTRIES = 256;
    private static final Duration MERGED_WINDOW_CACHE_TTL = Duration.ofHours(24);

    private final Cache<String, List<SearchGameRow>> mergedWindowCache = Caffeine.newBuilder()
            .maximumSize(MERGED_WINDOW_CACHE_MAX_ENTRIES)
            .expireAfterWrite(MERGED_WINDOW_CACHE_TTL)
            .build();

    private final IgdbService igdbService;
    private final UserGameRepository userGameRepository;

    public SearchService(IgdbService igdbService, UserGameRepository userGameRepository) {
        this.igdbService = igdbService;
        this.userGameRepository = userGameRepository;
    }

    public boolean hasActiveFilters(SearchSessionState state) {
        return !state.platforms.isEmpty()
                || state.yearMin > IgdbConstants.RELEASE_YEAR_MIN
                || state.yearMax < IgdbConstants.RELEASE_YEAR_MAX;
    }

    public void normalizeSessionYearRange(SearchSessionState state) {
        if (state == null) {
            return;
        }
        int lo = IgdbConstants.RELEASE_YEAR_MIN;
        int hi = IgdbConstants.RELEASE_YEAR_MAX;
        int yMin = Math.max(lo, Math.min(hi, state.yearMin));
        int yMax = Math.max(lo, Math.min(hi, state.yearMax));
        if (yMin > yMax) {
            int t = yMin;
            yMin = yMax;
            yMax = t;
        }
        state.yearMin = yMin;
        state.yearMax = yMax;
    }

    @Transactional(readOnly = true)
    public SearchResult search(
            String query, SearchSessionState filters, String sort, int pageIndex, String libraryUserId) {
        if (query == null || query.isBlank()) {
            return EMPTY_SEARCH;
        }
        if (pageIndex >= MAX_PAGES) {
            return EMPTY_SEARCH;
        }

        normalizeSessionYearRange(filters);

        int offset = pageIndex * PAGE_SIZE;
        try {
            return searchPage(query, filters, sort, offset, libraryUserId);
        } catch (IgdbException e) {
            logger.error("Search failed (IGDB): query=\"{}\"", query, e);
            return new SearchResult(Collections.emptyList(), false, IgdbException.MESSAGE);
        }
    }

    private SearchResult searchPage(
            String query, SearchSessionState filters, String sort, int offset, String libraryUserId) {
        List<SearchGameRow> merged = buildMergedResults(query, filters, sort);
        int total = merged.size();
        if (total == 0 || offset >= total) {
            return EMPTY_SEARCH;
        }

        List<SearchGameRow> page = slicePage(merged, offset);
        markGamesInCollection(page, libraryUserId);
        return new SearchResult(page, hasMoreAfterSlice(total, offset), null);
    }

    private List<SearchGameRow> buildMergedResults(String query, SearchSessionState filters, String sort) {
        String key = mergedWindowCacheKey(query, filters, sort);
        return mergedWindowCache.get(key, k -> computeSearchResultList(query, filters, sort));
    }

    private List<SearchGameRow> computeSearchResultList(String query, SearchSessionState filters, String sort) {
        List<Map<String, Object>> raw = igdbService.searchGames(
                query, MERGED_FETCH_LIMIT, 0, filters.platforms, filters.yearMin, filters.yearMax);
        List<SearchGameRow> games = SearchGameRow.fromIgdbMaps(raw != null ? raw : Collections.emptyList());
        if (games.isEmpty()) {
            return games;
        }
        attachPopularityScores(games);
        attachMatchFlags(games, query);
        attachDiscoverySignals(games);
        sortResults(games, sort);
        return games;
    }

    private static String mergedWindowCacheKey(String query, SearchSessionState filters, String sort) {
        String platformsKey = filters.platforms.stream()
                .filter(p -> p != null && !p.isBlank())
                .map(String::trim)
                .sorted()
                .collect(Collectors.joining("\u001e"));
        return query
                + '\u001f' + platformsKey
                + '\u001f' + filters.yearMin
                + '\u001f' + filters.yearMax
                + '\u001f' + sortMode(sort);
    }

    private List<SearchGameRow> slicePage(List<SearchGameRow> games, int offset) {
        int toIndex = Math.min(offset + PAGE_SIZE, games.size());
        List<SearchGameRow> page = new ArrayList<>(games.subList(offset, toIndex));
        for (SearchGameRow g : page) {
            g.inCollection = null;
        }
        return page;
    }

    private static boolean hasMoreAfterSlice(int totalSize, int offset) {
        int nextOffset = offset + PAGE_SIZE;
        return nextOffset < totalSize && nextOffset < MERGED_FETCH_LIMIT;
    }

    private void attachPopularityScores(List<SearchGameRow> games) {
        if (games == null || games.isEmpty()) {
            return;
        }

        Set<Long> ids = new LinkedHashSet<>();
        for (SearchGameRow game : games) {
            if (game.id > 0L) {
                ids.add(game.id);
            }
        }
        if (ids.isEmpty()) {
            return;
        }

        Map<Long, Double> scores = igdbService.fetchPopularityScores(ids);
        if (scores == null || scores.isEmpty()) {
            return;
        }
        for (SearchGameRow game : games) {
            Double score = scores.get(game.id);
            if (score != null && score > 0d) {
                game.popScore = score;
            }
        }
    }

    private void attachMatchFlags(List<SearchGameRow> games, String query) {
        if (games == null || games.isEmpty()) {
            return;
        }

        String qNorm = normalizeTitle(query);
        if (qNorm.isEmpty()) {
            return;
        }

        for (SearchGameRow g : games) {
            if (g.name == null) {
                continue;
            }

            String tNorm = normalizeTitle(g.name);
            if (tNorm.equals(qNorm)) {
                g.exactMatch = true;
            }
            if (qNorm.length() >= 2 && tNorm.startsWith(qNorm)) {
                g.prefixMatch = true;
            }
        }
    }

    private void attachDiscoverySignals(List<SearchGameRow> games) {
        if (games == null || games.isEmpty()) {
            return;
        }
        Instant newReleaseCutoff = Instant.now().minus(DISCOVERY_NEW_RELEASE_LOOKBACK_DAYS, ChronoUnit.DAYS);
        for (SearchGameRow g : games) {
            List<String> signals = new ArrayList<>(3);
            Integer reviewScore = g.getReviewScore();
            if (reviewScore != null && reviewScore >= DISCOVERY_LOVED_MIN_REVIEW_SCORE) {
                signals.add("Loved");
            }
            Double ps = g.popScore;
            if (ps != null && ps >= POPULAR_BADGE_MIN_SCORE) {
                signals.add("Popular");
            }
            Long epoch = g.firstReleaseDateEpoch;
            if (epoch != null && !Instant.ofEpochSecond(epoch).isBefore(newReleaseCutoff)) {
                signals.add("New");
            }
            if (!signals.isEmpty()) {
                g.discoverySignals = signals;
            }
        }
    }

    private static String normalizeTitle(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String sortMode(String sort) {
        if (sort == null || sort.isBlank()) {
            return SORT_RELEVANCE;
        }
        return sort.toLowerCase(Locale.ROOT);
    }

    private void sortResults(List<SearchGameRow> games, String sort) {
        if (games == null || games.isEmpty()) {
            return;
        }
        switch (sortMode(sort)) {
            case SORT_POPULARITY -> sortByPopularity(games);
            case SORT_RELEASE_NEWEST -> sortByReleaseNewest(games);
            default -> sortByRelevance(games);
        }
    }

    private void sortByRelevance(List<SearchGameRow> games) {
        if (games == null || games.isEmpty()) {
            return;
        }

        games.sort(Comparator
                .comparingInt(SearchService::relevancePrimaryGroup)
                .thenComparingDouble((SearchGameRow g) -> -(g.popScore != null ? g.popScore : 0d))
                .thenComparingLong(g -> g.id));
    }

    private static int relevancePrimaryGroup(SearchGameRow g) {
        if (Boolean.TRUE.equals(g.exactMatch)) {
            return 0;
        }
        if (Boolean.TRUE.equals(g.prefixMatch)) {
            return 1;
        }
        Double p = g.popScore;
        if (p != null && p < RELEVANCE_VERY_LOW_ENGAGEMENT_MAX) {
            return 3;
        }
        return 2;
    }

    private static void sortByPopularity(List<SearchGameRow> games) {
        games.sort(Comparator
                .comparingDouble((SearchGameRow g) -> -(g.popScore != null ? g.popScore : 0d))
                .thenComparingLong(g -> g.id));
    }

    private static void sortByReleaseNewest(List<SearchGameRow> games) {
        games.sort(Comparator
                .comparingLong((SearchGameRow g) -> g.firstReleaseDateEpoch != null ? g.firstReleaseDateEpoch : 0L)
                .reversed());
    }

    private void markGamesInCollection(List<SearchGameRow> games, String libraryUserId) {
        if (games == null || games.isEmpty()) {
            return;
        }

        if (libraryUserId == null || libraryUserId.isBlank()) {
            for (SearchGameRow g : games) {
                g.inCollection = false;
            }
            return;
        }

        List<String> apiIds = games.stream().map(g -> String.valueOf(g.id)).toList();
        Set<String> inCollectionApiIds = userGameRepository
                .findByUserIdAndGame_ApiIdIn(libraryUserId.trim(), apiIds)
                .stream()
                .map(ug -> ug.getGame().getApiId())
                .collect(Collectors.toSet());

        for (SearchGameRow g : games) {
            g.inCollection = inCollectionApiIds.contains(String.valueOf(g.id));
        }
    }
}
