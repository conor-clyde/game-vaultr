/**
 * Search page JS
 *
 * Sections:
 * 1. DOM helpers
 * 2. Constants
 * 3. Filter overlay (mobile)
 * 4. Platform "Other" dropdown (sidebar + overlay)
 * 5. Sort dropdown
 * 6. Search form (submit, loading, clear button)
 * 7. Platform tag collapsing (result cards)
 * 8. Release year range (sidebar + overlay filter forms)
 * 9. Load more results (JSON API + card template)
 *
 * On the search landing (no query), most listeners no-op: sidebar filters and results
 * are not in the DOM; overlay filters and the hero row still use overlay + sliders below.
 */
(() => {
  const $ = (id) => document.getElementById(id);
  const qs = (s, r = document) => r.querySelector(s);
  const qsa = (s, r = document) => [...r.querySelectorAll(s)];
  const optimizeIgdbCoverUrl = (url, sizePreset = "t_cover_big") => {
    const raw = String(url ?? "").trim();
    if (!raw) return "";
    return raw.replace(/\/t_[^/]+\//, `/${sizePreset}/`);
  };

  const toggleClass = (el, cls, force) => el?.classList.toggle(cls, force);
  const setAriaHidden = (el, hidden) =>
    el?.setAttribute("aria-hidden", hidden ? "true" : "false");

  const ID = {
    filterOverlay: "filterOverlay",
    filterToggle: "filterToggle",
    filterOverlayClose: "filterOverlayClose",
    filterOverlayBackdrop: "filterOverlayBackdrop",
    platformOtherOverlay: "platformOtherOverlay",
    platformAllOverlay: "platformAllOverlay",
    platformOtherSidebar: "platformOtherSidebar",
    platformAllSidebar: "platformAllSidebar",
    sortDropdownTrigger: "sortDropdownTrigger",
    sortDropdownPanel: "sortDropdownPanel",
    searchForm: "searchForm",
    searchResultsContainer: "searchResultsContainer",
    searchLoadingTemplate: "searchLoadingTemplate",
    searchQueryInput: "searchQueryInput",
    searchClearBtn: "searchClearBtn",
    searchResultsList: "searchResultsList",
    searchResultsLoadMore: "searchResultsLoadMore",
    loadMoreResultsBtn: "loadMoreResultsBtn",
    searchResultCardTemplate: "searchResultCardTemplate",
    releaseYearFillSidebar: "releaseYearFillSidebar",
    releaseYearMinSidebar: "releaseYearMinSidebar",
    releaseYearMaxSidebar: "releaseYearMaxSidebar",
    releaseYearFillOverlay: "releaseYearFillOverlay",
    releaseYearMinOverlay: "releaseYearMinOverlay",
    releaseYearMaxOverlay: "releaseYearMaxOverlay",
  };

  const SELECTOR = {
    sortOption: ".sort-option",
    searchButton: 'button[type="submit"]',
    platformsContainer: ".search-result-card .platforms",
    tag: ".tag",
    tagMore: ".tag-more",
    cardImg: ".js-card-img",
    cardImgPlaceholder: ".js-card-img-placeholder",
    cardTitle: ".js-card-title",
    cardYear: ".js-card-year",
    cardRating: ".js-card-rating",
    cardRatingScore: ".js-card-rating-score",
    cardReviewScore: ".js-card-review-score",
    cardDiscoverySignals: ".js-card-discovery-signals",
    cardPlatforms: ".js-card-platforms",
    cardInCollection: ".js-card-in-collection",
    cardAddForm: ".js-card-add-form",
    searchResultCard: ".search-result-card",
  };

  const ARIA = {
    isHidden: (el) => el?.getAttribute("aria-hidden") === "true",
  };

  const API = { loadMore: "/search/api/next" };
  const LIMITS = { loadMorePageSize: 20 };

  const toSignalBadgeModifier = (signal) => {
    const s = String(signal ?? "")
      .trim()
      .toLowerCase();
    if (s === "loved") return "discovery-signal-badge--loved";
    if (s === "popular") return "discovery-signal-badge--popular";
    if (s === "new") return "discovery-signal-badge--new";
    return "";
  };

  const overlay = $(ID.filterOverlay);
  const toggle = $(ID.filterToggle);
  const closeBtn = $(ID.filterOverlayClose);
  const backdrop = $(ID.filterOverlayBackdrop);
  const overlayOtherBtn = $(ID.platformOtherOverlay);
  const overlayOtherPanel = $(ID.platformAllOverlay);

  const openFilter = () => {
    if (!overlay) return;
    overlay.classList.add("open");
    setAriaHidden(overlay, false);
    document.body.style.overflow = "hidden";
  };

  const closeFilter = () => {
    if (!overlay) return;
    overlay.classList.remove("open");
    setAriaHidden(overlay, true);
    document.body.style.overflow = "";
    setAriaHidden(overlayOtherPanel, true);
    toggleClass(overlayOtherBtn, "is-open", false);
  };

  toggle?.addEventListener("click", openFilter);
  closeBtn?.addEventListener("click", closeFilter);
  backdrop?.addEventListener("click", closeFilter);
  document.addEventListener("keydown", (e) => {
    if (e.key !== "Escape") return;
    if (overlay?.classList.contains("open")) closeFilter();
  });

  const otherPairs = [
    { btn: $(ID.platformOtherSidebar), panel: $(ID.platformAllSidebar) },
    { btn: overlayOtherBtn, panel: overlayOtherPanel },
  ];

  otherPairs.forEach(({ btn, panel }) => {
    if (!btn || !panel) return;
    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      const hidden = ARIA.isHidden(panel);
      setAriaHidden(panel, !hidden);
      toggleClass(btn, "is-open", hidden);
    });

    panel.querySelectorAll('input[name="platform"]').forEach((cb) =>
      cb.addEventListener("change", () => {
        const count = [
          ...panel.querySelectorAll('input[name="platform"]'),
        ].filter((c) => c.checked).length;
        toggleClass(btn, "has-selection", count > 0);
      }),
    );
  });

  document.addEventListener("click", (e) => {
    otherPairs.forEach(({ btn, panel }) => {
      if (!btn || !panel || ARIA.isHidden(panel)) return;
      if (btn.contains(e.target) || panel.contains(e.target)) return;
      setAriaHidden(panel, true);
      toggleClass(btn, "is-open", false);
    });

    if (
      sortTrigger &&
      sortPanel &&
      !ARIA.isHidden(sortPanel) &&
      !sortTrigger.contains(e.target) &&
      !sortPanel.contains(e.target)
    ) {
      setAriaHidden(sortPanel, true);
      sortTrigger.setAttribute("aria-expanded", "false");
    }
  });

  const sortTrigger = $(ID.sortDropdownTrigger);
  const sortPanel = $(ID.sortDropdownPanel);
  const sortForm = sortTrigger?.closest("form");

  if (sortTrigger && sortPanel && sortForm) {
    sortTrigger.addEventListener("click", (e) => {
      e.stopPropagation();
      const expanded = sortTrigger.getAttribute("aria-expanded") === "true";
      sortTrigger.setAttribute("aria-expanded", String(!expanded));
      setAriaHidden(sortPanel, expanded);
    });

    qsa(SELECTOR.sortOption, sortPanel).forEach((opt) => {
      opt.addEventListener("click", () => {
        const sort = opt.dataset.sort;
        if (sort) sortForm.querySelector('input[name="sort"]').value = sort;
        sortForm.submit();
        setAriaHidden(sortPanel, true);
        sortTrigger.setAttribute("aria-expanded", "false");
      });
    });
  }

  const form = $(ID.searchForm);
  const resultsContainer = $(ID.searchResultsContainer);
  const loadingTemplate = $(ID.searchLoadingTemplate);
  const searchInput = $(ID.searchQueryInput);
  const searchButton = form ? qs(SELECTOR.searchButton, form) : null;
  const clearButton = $(ID.searchClearBtn);

  if (form && searchInput && clearButton) {
    const updateClearButton = () => {
      clearButton.hidden = searchInput.value.trim().length === 0;
    };

    searchInput.addEventListener("input", updateClearButton);
    searchInput.addEventListener("change", updateClearButton);

    clearButton.addEventListener("click", (e) => {
      e.preventDefault();
      searchInput.value = "";
      updateClearButton();
      searchInput.focus();
    });

    updateClearButton();
  }

  if (form && resultsContainer && loadingTemplate && searchInput) {
    form.addEventListener("submit", (e) => {
      const query = searchInput.value.trim();
      if (!query) return;

      e.preventDefault(); // prevent immediate submission to show loading
      resultsContainer.innerHTML = ""; // clear previous results
      resultsContainer.appendChild(loadingTemplate.content.cloneNode(true));

      if (searchButton) searchButton.disabled = true;

      form.submit(); // submit after UI updates
    });
  }

  function updatePlatformTags() {
    qsa(SELECTOR.platformsContainer).forEach((container) => {
      const tags = qsa(SELECTOR.tag, container);
      const more = qs(SELECTOR.tagMore, container);
      if (!more || tags.length === 0) return;

      tags.forEach((t) => (t.style.display = ""));
      more.style.display = "none";
      more.setAttribute("aria-hidden", "true");

      const isOverflowing = () =>
        container.scrollWidth > container.clientWidth ||
        container.scrollHeight > container.clientHeight;

      if (!isOverflowing()) return;

      let visibleCount = tags.length;
      for (let i = 1; i <= tags.length; i++) {
        tags.forEach((t, idx) => {
          t.style.display = idx < i ? "" : "none";
        });

        if (isOverflowing()) {
          visibleCount = i - 1; // previous count fits
          break;
        }
      }

      tags.forEach(
        (t, i) => (t.style.display = i < visibleCount ? "" : "none"),
      );
      const hiddenCount = tags.length - visibleCount;
      if (hiddenCount > 0) {
        more.textContent = `+${hiddenCount}`;
        more.style.display = "";
        more.setAttribute("aria-hidden", "false");
      } else {
        more.style.display = "none";
        more.setAttribute("aria-hidden", "true");
      }
    });
  }

  function runPlatformTagUpdate() {
    requestAnimationFrame(() => updatePlatformTags());
  }

  runPlatformTagUpdate();
  window.addEventListener("resize", runPlatformTagUpdate);

  function initReleaseYearRange(minId, maxId, fillId, minLabelId, maxLabelId) {
    const minInput = $(minId);
    const maxInput = $(maxId);
    const fill = $(fillId);
    const minLabel = document.getElementById(minLabelId);
    const maxLabel = document.getElementById(maxLabelId);

    if (!minInput || !maxInput || !fill || !minLabel || !maxLabel) return;

    const clampValues = () => {
      let minVal = Number(minInput.value);
      let maxVal = Number(maxInput.value);

      if (minVal > maxVal) minVal = maxVal;
      if (maxVal < minVal) maxVal = minVal;

      minInput.value = minVal;
      maxInput.value = maxVal;
    };

    const updateFill = () => {
      clampValues();

      const minBound = Number(minInput.min);
      const maxBound = Number(maxInput.max);
      const range = maxBound - minBound || 1;

      const left = ((Number(minInput.value) - minBound) / range) * 100;
      const width =
        ((Number(maxInput.value) - Number(minInput.value)) / range) * 100;

      fill.style.left = `${left}%`;
      fill.style.width = `${width}%`;

      minLabel.textContent = minInput.value;
      maxLabel.textContent = maxInput.value;
    };

    [minInput, maxInput].forEach((input) => {
      input.addEventListener("input", updateFill);
    });

    updateFill();
  }

  initReleaseYearRange(
    ID.releaseYearMinSidebar,
    ID.releaseYearMaxSidebar,
    ID.releaseYearFillSidebar,
    "releaseYearMinValueSidebar",
    "releaseYearMaxValueSidebar",
  );

  initReleaseYearRange(
    ID.releaseYearMinOverlay,
    ID.releaseYearMaxOverlay,
    ID.releaseYearFillOverlay,
    "releaseYearMinValueOverlay",
    "releaseYearMaxValueOverlay",
  );

  const resultsList = $(ID.searchResultsList);
  const loadMoreContainer = $(ID.searchResultsLoadMore);
  const loadMoreBtn = $(ID.loadMoreResultsBtn);
  const cardTemplate = $(ID.searchResultCardTemplate);
  let isLoadingMore = false;

  function buildCard(game, query) {
    if (!cardTemplate?.content) return null;

    const {
      id = "",
      name = "",
      coverImageUrl = "",
      releaseYear,
      platforms = [],
      inCollection,
      reviewScore,
      discoverySignals = null,
    } = game;
    const clone = cardTemplate.content.cloneNode(true);

    const imgEl = qs(SELECTOR.cardImg, clone);
    const placeholder = qs(SELECTOR.cardImgPlaceholder, clone);
    if (imgEl && placeholder) {
      if (coverImageUrl) {
        imgEl.src = optimizeIgdbCoverUrl(coverImageUrl);
        imgEl.width = 90;
        imgEl.height = 120;
        imgEl.loading = "lazy";
        imgEl.decoding = "async";
        imgEl.style.display = "";
        placeholder.style.display = "none";
      } else {
        imgEl.style.display = "none";
        placeholder.style.display = "";
      }
    }

    const titleEl = qs(SELECTOR.cardTitle, clone);
    if (titleEl) titleEl.textContent = name;

    const yearEl = qs(SELECTOR.cardYear, clone);
    if (yearEl) {
      yearEl.textContent = releaseYear ?? "";
      yearEl.style.display = releaseYear != null ? "" : "none";
    }

    const ratingEl = qs(SELECTOR.cardRating, clone);
    const ratingScoreEl = qs(SELECTOR.cardRatingScore, clone);
    const reviewScoreEl = qs(SELECTOR.cardReviewScore, clone);
    const signalsEl = qs(SELECTOR.cardDiscoverySignals, clone);

    const hasReviewScore = reviewScore != null;
    const hasDiscoverySignals =
      Array.isArray(discoverySignals) && discoverySignals.length > 0;

    if (ratingEl) {
      ratingEl.style.display =
        hasReviewScore || hasDiscoverySignals ? "" : "none";
    }
    if (ratingScoreEl) {
      ratingScoreEl.style.display = hasReviewScore ? "" : "none";
    }
    if (reviewScoreEl) {
      reviewScoreEl.textContent = hasReviewScore ? String(reviewScore) : "";
    }
    if (signalsEl) {
      if (hasDiscoverySignals) {
        signalsEl.style.display = "";
        signalsEl.innerHTML = "";
        discoverySignals.forEach((sig) => {
          const span = document.createElement("span");
          span.className = "discovery-signal-badge";
          const modifier = toSignalBadgeModifier(sig);
          if (modifier) span.classList.add(modifier);
          const raw = String(sig ?? "").trim();
          span.textContent =
            raw.length > 0
              ? raw.charAt(0).toUpperCase() + raw.slice(1).toLowerCase()
              : raw;
          signalsEl.appendChild(span);
        });
      } else {
        signalsEl.style.display = "none";
      }
    }

    const platformsEl = qs(SELECTOR.cardPlatforms, clone);
    const tagMore = platformsEl ? qs(SELECTOR.tagMore, platformsEl) : null;

    const platformNames = Array.isArray(platforms)
      ? platforms
          .map((p) => p?.displayName ?? p?.name)
          .filter((n) => n != null && String(n).trim() !== "")
      : [];

    if (platformsEl) {
      platformsEl.setAttribute(
        "data-platform-names",
        JSON.stringify(platformNames),
      );
    }

    platformNames.forEach((label) => {
      if (label && platformsEl && tagMore) {
        const span = document.createElement("span");
        span.className = "tag";
        span.textContent = label;
        platformsEl.insertBefore(span, tagMore);
      }
    });

    const inColBtn = qs(SELECTOR.cardInCollection, clone);
    const addForm = qs(SELECTOR.cardAddForm, clone);
    if (inCollection) {
      if (inColBtn) inColBtn.style.display = "";
      if (addForm) addForm.style.display = "none";
    } else if (addForm) {
      const values = {
        apiId: String(id),
        title: name,
        imageUrl: coverImageUrl,
        query: query ?? "",
      };
      Object.entries(values).forEach(([field, value]) => {
        const input = addForm.querySelector(`input[name="${field}"]`);
        if (input) input.value = value;
      });
    }

    return qs(SELECTOR.searchResultCard, clone);
  }

  if (resultsList && loadMoreContainer && loadMoreBtn && cardTemplate) {
    loadMoreBtn.addEventListener("click", () => {
      if (isLoadingMore) return;
      const nextPage = parseInt(loadMoreContainer.dataset.page || "0", 10) + 1;
      const query =
        loadMoreContainer.dataset.query ||
        qs(".results-head__query")?.textContent?.trim() ||
        "";
      const sort = loadMoreContainer.dataset.sort || "relevance";
      if (!query) {
        loadMoreContainer.style.display = "none";
        return;
      }

      isLoadingMore = true;
      loadMoreBtn.disabled = true;

      fetch(
        `${API.loadMore}?query=${encodeURIComponent(query)}&page=${nextPage}&sort=${encodeURIComponent(sort)}`,
        { headers: { Accept: "application/json" } },
      )
        .then((res) => (res.ok ? res.json() : null))
        .then((data) => {
          if (data?.igdbError) {
            const parent = resultsList.parentNode;
            if (parent && !parent.querySelector(".search-igdb-error")) {
              const msg = document.createElement("div");
              msg.className = "search-igdb-error";
              msg.setAttribute("role", "alert");
              const p = document.createElement("p");
              p.className = "search-igdb-error-text";
              p.textContent = data.igdbError;
              msg.appendChild(p);
              parent.insertBefore(msg, resultsList);
            }
            loadMoreContainer.style.display = "none";
            return;
          }
          if (!data?.results?.length) {
            loadMoreContainer.style.display = "none";
            return;
          }

          data.results.forEach((g) => {
            const card = buildCard(g, query);
            if (card) resultsList.appendChild(card);
          });

          loadMoreContainer.dataset.page = String(nextPage);
          runPlatformTagUpdate();
          if (!data.hasMore || data.results.length < LIMITS.loadMorePageSize)
            loadMoreContainer.style.display = "none";
        })
        .catch(() => {
          loadMoreContainer.style.display = "";
        })
        .finally(() => {
          isLoadingMore = false;
          loadMoreBtn.disabled = false;
        });
    });
  }
})();
