package com.cocoding.playstate.controller;

import com.cocoding.playstate.PlatformCatalog;
import com.cocoding.playstate.dto.SearchResult;
import com.cocoding.playstate.dto.SearchSessionState;
import com.cocoding.playstate.igdb.IgdbConstants;
import com.cocoding.playstate.model.Game;
import com.cocoding.playstate.model.UserGame;
import com.cocoding.playstate.repository.GameRepository;
import com.cocoding.playstate.repository.UserGameRepository;
import com.cocoding.playstate.security.LibraryUserIds;
import com.cocoding.playstate.service.GameEnrichmentService;
import com.cocoding.playstate.service.SearchService;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/search")
public class SearchController {

    private static final String SESSION_SEARCH_STATE = "searchSessionState";
    private static final String SEARCH_PATH = "/search";

    private final SearchService searchService;
    private final GameRepository gameRepository;
    private final UserGameRepository userGameRepository;
    private final GameEnrichmentService gameEnrichmentService;

    public SearchController(
            SearchService searchService,
            GameRepository gameRepository,
            UserGameRepository userGameRepository,
            GameEnrichmentService gameEnrichmentService) {
        this.searchService = searchService;
        this.gameRepository = gameRepository;
        this.userGameRepository = userGameRepository;
        this.gameEnrichmentService = gameEnrichmentService;
    }

    @GetMapping(value = {"", "/"})
    public String searchPage(
            Authentication authentication,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) String removePlatform,
            @RequestParam(required = false) Boolean removeYearFilter,
            @RequestParam(required = false) Boolean clearFilters,
            @RequestParam(required = false, defaultValue = "relevance") String sort,
            HttpSession session,
            Model model) {
        String normalizedQuery = normalizeQuery(query);

        if (handleFilterActions(session, clearFilters, removePlatform, removeYearFilter)) {
            return "redirect:" + buildSearchBaseUrl(normalizedQuery);
        }

        model.addAttribute("title", "Search");
        model.addAttribute("addCollectionPlatformFallback", PlatformCatalog.allIgdbPlatformNamesSorted());
        if (normalizedQuery.isEmpty()) {
            clearSessionFilters(session);
        } else {
            populateSearchResultsView(model, session, normalizedQuery, sort, page, authentication);
        }
        return "pages/search";
    }

    @GetMapping(value = "/api/next", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public SearchResult searchNextPage(
            Authentication authentication,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "relevance") String sort,
            HttpSession session) {

        String q = normalizeQuery(query);
        if (q.isEmpty()) {
            return new SearchResult(Collections.emptyList(), false, null);
        }
        SearchSessionState state = getSearchState(session);
        int pageIndex = nonNegativePageIndex(page);
        return searchService.search(q, state, sort, pageIndex, LibraryUserIds.optional(authentication));
    }

    @PostMapping("/filters")
    public String applyFilters(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<String> platform,
            @RequestParam(required = false) Integer releaseYearMin,
            @RequestParam(required = false) Integer releaseYearMax,
            HttpSession session) {

        String normalizedQuery = normalizeQuery(query);
        SearchSessionState filters = searchFiltersFromForm(platform, releaseYearMin, releaseYearMax);
        searchService.normalizeSessionYearRange(filters);
        session.setAttribute(SESSION_SEARCH_STATE, filters);
        return "redirect:" + buildSearchBaseUrl(normalizedQuery);
    }

    @PostMapping("/add")
    public String addToLibrary(
            Authentication authentication,
            @RequestParam String apiId,
            @RequestParam String title,
            @RequestParam(required = false) String imageUrl,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String platform,
            RedirectAttributes redirectAttributes) {

        String userId = LibraryUserIds.require(authentication);
        Game game = findOrCreateGame(apiId, title, imageUrl);
        gameEnrichmentService.enrichFromIgdbIfIncomplete(game);
        gameEnrichmentService.refreshPlatformOptionsFromIgdb(game, platform);
        String redirectQuery = normalizeQuery(preferredRedirectQuery(query, title));

        String chosenPlatform = trimmedOrNull(platform);
        if (chosenPlatform == null || isNotSpecifiedPlatformLabel(chosenPlatform)) {
            redirectAttributes.addFlashAttribute(
                    "searchCollectionAddError",
                    "Choose a platform before adding to your collection.");
            return "redirect:" + buildSearchBaseUrl(redirectQuery);
        }

        ensureUserHasGame(userId, game, chosenPlatform);

        String addedTitle =
                title != null && !title.isBlank() ? title.trim() : game.getTitle();
        redirectAttributes.addFlashAttribute("searchCollectionAdded", true);
        redirectAttributes.addFlashAttribute("searchCollectionAddedTitle", addedTitle);

        return "redirect:" + buildSearchBaseUrl(redirectQuery);
    }

    private void populateSearchResultsView(
            Model model,
            HttpSession session,
            String normalizedQuery,
            String sort,
            Integer page,
            Authentication authentication) {

        prepareSearchPageModel(model, normalizedQuery, sort);
        SearchSessionState state = getSearchState(session);
        int pageIndex = nonNegativePageIndex(page != null ? page : 0);
        SearchResult result =
                searchService.search(
                        normalizedQuery, state, sort, pageIndex, LibraryUserIds.optional(authentication));
        addFilterStateToModel(model, state, normalizedQuery);
        addSearchResultsToModel(model, result, pageIndex);
    }

    private void prepareSearchPageModel(Model model, String query, String sort) {
        model.addAttribute("query", query);
        model.addAttribute("releaseYearMinBound", IgdbConstants.RELEASE_YEAR_MIN);
        model.addAttribute("releaseYearMaxBound", IgdbConstants.RELEASE_YEAR_MAX);
        model.addAttribute("sort", sort);
    }

    private void addSearchResultsToModel(Model model, SearchResult result, int pageIndex) {
        model.addAttribute("searchResult", result);
        model.addAttribute("page", pageIndex);
    }

    private void addFilterStateToModel(Model model, SearchSessionState state, String query) {
        model.addAttribute("selectedPlatforms", state.platforms);
        model.addAttribute("releaseYearMin", state.yearMin);
        model.addAttribute("releaseYearMax", state.yearMax);
        model.addAttribute("primaryPlatformChips", PlatformCatalog.PRIMARY_CHIPS);
        model.addAttribute("catalogSecondaryPlatformNames", PlatformCatalog.SECONDARY_PLATFORM_NAMES);
        model.addAttribute(
                "hasOtherPlatformSelected",
                state.platforms.stream().anyMatch(PlatformCatalog::isSecondaryCatalogName));
        model.addAttribute("hasActiveFilters", searchService.hasActiveFilters(state));
        model.addAttribute("activeFilterChips", buildActiveFilterChips(query, state));
    }

    private List<Map<String, String>> buildActiveFilterChips(String query, SearchSessionState state) {
        String chipBaseUrl = searchPathWithQuery(query) + "&";
        List<Map<String, String>> chips = new ArrayList<>();

        for (String platform : state.platforms) {
            chips.add(Map.of(
                    "label",
                    platform,
                    "clearUrl",
                    chipBaseUrl + "removePlatform=" + URLEncoder.encode(platform, StandardCharsets.UTF_8)));
        }

        if (hasCustomYearRange(state)) {
            chips.add(Map.of(
                    "label",
                    state.yearMin + "\u2013" + state.yearMax,
                    "clearUrl",
                    chipBaseUrl + "removeYearFilter=true"));
        }

        return chips;
    }

    private static boolean hasCustomYearRange(SearchSessionState state) {
        return state.yearMin > IgdbConstants.RELEASE_YEAR_MIN
                || state.yearMax < IgdbConstants.RELEASE_YEAR_MAX;
    }

    private static SearchSessionState existingSearchState(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object attribute = session.getAttribute(SESSION_SEARCH_STATE);
        return attribute instanceof SearchSessionState state ? state : null;
    }

    private SearchSessionState getSearchState(HttpSession session) {
        if (session == null) {
            return new SearchSessionState();
        }
        SearchSessionState existing = existingSearchState(session);
        if (existing != null) {
            return existing;
        }
        SearchSessionState created = new SearchSessionState();
        session.setAttribute(SESSION_SEARCH_STATE, created);
        return created;
    }

    private void clearSessionFilters(HttpSession session) {
        SearchSessionState state = existingSearchState(session);
        if (state != null) {
            state.clearFiltersOnly();
        }
    }

    private static SearchSessionState searchFiltersFromForm(
            List<String> platform,
            Integer releaseYearMin,
            Integer releaseYearMax) {

        SearchSessionState state = new SearchSessionState();
        if (platform != null) {
            for (String p : platform) {
                if (p != null && !p.isBlank()) {
                    state.platforms.add(p.trim());
                }
            }
        }

        state.yearMin = boundYear(releaseYearMin, IgdbConstants.RELEASE_YEAR_MIN);
        state.yearMax = boundYear(releaseYearMax, IgdbConstants.RELEASE_YEAR_MAX);
        return state;
    }

    private static int boundYear(Integer year, int whenOutOfRange) {
        if (year == null || year < IgdbConstants.RELEASE_YEAR_MIN || year > IgdbConstants.RELEASE_YEAR_MAX) {
            return whenOutOfRange;
        }
        return year;
    }

    private boolean handleFilterActions(
            HttpSession session,
            Boolean clearFilters,
            String removePlatform,
            Boolean removeYearFilter) {

        if (!isTrue(clearFilters)
                && (removePlatform == null || removePlatform.isBlank())
                && !isTrue(removeYearFilter)) {
            return false;
        }

        if (isTrue(clearFilters)) {
            clearSessionFilters(session);
            return true;
        }
        if (removePlatform != null && !removePlatform.isBlank()) {
            getSearchState(session).platforms.remove(removePlatform.trim());
            return true;
        }
        if (isTrue(removeYearFilter)) {
            SearchSessionState state = getSearchState(session);
            state.yearMin = IgdbConstants.RELEASE_YEAR_MIN;
            state.yearMax = IgdbConstants.RELEASE_YEAR_MAX;
            return true;
        }

        return false;
    }

    private Game findOrCreateGame(String apiId, String title, String imageUrl) {
        return gameRepository
                .findByApiId(apiId)
                .orElseGet(() -> gameRepository.save(new Game(apiId, title, imageUrl)));
    }

    private void ensureUserHasGame(String userId, Game game, String platform) {
        if (userGameRepository.existsByUserIdAndGameId(userId, game.getId())) {
            return;
        }
        UserGame userGame = new UserGame(userId, game.getId());
        userGame.setPlatform(trimmedOrNull(platform));
        userGameRepository.save(userGame);
    }

    private static String trimmedOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean isNotSpecifiedPlatformLabel(String value) {
        return "not specified".equalsIgnoreCase(value.trim());
    }

    private static String preferredRedirectQuery(String query, String title) {
        if (query != null && !query.isBlank()) {
            return query.trim();
        }
        return title != null ? title : "";
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().replaceAll("\\s+", " ");
    }

    private static int nonNegativePageIndex(int page) {
        return Math.max(0, page);
    }

    private static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private String buildSearchBaseUrl(String normalizedQuery) {
        return normalizedQuery.isEmpty() ? SEARCH_PATH : searchPathWithQuery(normalizedQuery);
    }

    private static String searchPathWithQuery(String normalizedQuery) {
        return SEARCH_PATH + "?query=" + URLEncoder.encode(normalizedQuery, StandardCharsets.UTF_8);
    }
}
