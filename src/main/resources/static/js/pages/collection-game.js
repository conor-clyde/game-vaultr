(function () {
  "use strict";

  (function wireHeroLogPlayIdleChoice() {
    var wrap = document.getElementById("colGameHeroLogIdleWrap");
    var reveal = document.getElementById("colGameLogPlayRevealBtn");
    var panel = document.getElementById("colGameHeroLogPlayPanel");
    if (!wrap || !reveal || !panel) {
      return;
    }
    function isPanelOpen() {
      return !panel.hidden;
    }
    function setPanelOpen(open) {
      panel.hidden = !open;
      reveal.setAttribute("aria-expanded", open ? "true" : "false");
      wrap.classList.toggle("cg-hero-log-idle-wrap--open", open);
    }
    reveal.addEventListener("click", function (e) {
      e.stopPropagation();
      setPanelOpen(!isPanelOpen());
    });
    document.addEventListener("click", function (e) {
      if (!isPanelOpen()) {
        return;
      }
      var t = e.target;
      if (wrap.contains(t)) {
        return;
      }
      setPanelOpen(false);
    });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && isPanelOpen()) {
        setPanelOpen(false);
      }
    });
    wrap.addEventListener("click", function (e) {
      var openLog = e.target.closest && e.target.closest(".js-log-play-open");
      if (openLog && wrap.contains(openLog)) {
        setPanelOpen(false);
      }
    });
    document
      .querySelectorAll(".js-cg-hero-start-playing")
      .forEach(function (btn) {
        btn.addEventListener("click", function (e) {
          e.preventDefault();
          e.stopPropagation();
          if (btn.disabled) {
            return;
          }
          var id = btn.getAttribute("data-log-api-id");
          if (
            !id ||
            typeof window.cgSubmitStartPlayingNowFromHero !== "function"
          ) {
            return;
          }
          btn.disabled = true;
          btn.setAttribute("aria-busy", "true");
          window.cgSubmitStartPlayingNowFromHero(String(id).trim());
        });
      });
  })();

  var CG_EVT_CLOSE_ALL_GAME_EDITS = "cg-game-page-close-all-edits";

  function sessionBlocksColGameEdits() {
    return (
      document.body &&
      document.body.getAttribute("data-session-blocks-edits") === "true"
    );
  }

  function dispatchCloseAllGamePageEdits(opts) {
    var detail = {};
    if (opts && opts.keepClientDraftPlaythroughs) {
      detail.keepClientDraftPlaythroughs = true;
    }
    document.dispatchEvent(
      new CustomEvent(CG_EVT_CLOSE_ALL_GAME_EDITS, {
        bubbles: true,
        detail: detail,
      }),
    );
  }

  function getOrCreateColGameEditingLabel(editBtn) {
    if (!editBtn || !editBtn.id || !editBtn.parentElement) {
      return null;
    }
    var labelId = editBtn.getAttribute("data-editing-label-id");
    if (labelId) {
      var byId = document.getElementById(labelId);
      if (byId) {
        return byId;
      }
    }
    var parent = editBtn.parentElement;
    var sel =
      '.cg-section-editing-label[data-for-edit-btn="' + editBtn.id + '"]';
    var existing = parent.querySelector(sel);
    if (existing) {
      return existing;
    }
    var el = document.createElement("span");
    el.className = "cg-section-editing-label";
    el.setAttribute("data-for-edit-btn", editBtn.id);
    el.setAttribute("aria-live", "polite");
    el.textContent = "Editing";
    el.hidden = true;
    editBtn.insertAdjacentElement("afterend", el);
    return el;
  }

  function wireEditSection(
    sectionSelector,
    editBtnId,
    viewId,
    formId,
    cancelBtnId,
    onLeaveEdit,
    headActionsId,
  ) {
    var section = document.querySelector(sectionSelector);
    var editBtn = document.getElementById(editBtnId);
    var cancelBtn = document.getElementById(cancelBtnId);
    var viewEl = document.getElementById(viewId);
    var formEl = document.getElementById(formId);
    var headActionsEl = headActionsId
      ? document.getElementById(headActionsId)
      : null;
    if (!section || !editBtn || !viewEl || !formEl) {
      return;
    }

    function setEditButtonState(isEditing) {
      var idleLabel = editBtn.getAttribute("data-edit-label") || "Edit";
      var idleSpanText = editBtn.getAttribute("data-idle-label") || idleLabel;
      var editingLabel = getOrCreateColGameEditingLabel(editBtn);
      editBtn.setAttribute("aria-expanded", isEditing ? "true" : "false");
      editBtn.hidden = !!isEditing;
      if (editingLabel) {
        editingLabel.hidden = !isEditing;
      }
      if (editBtn.classList.contains("btn-icon")) {
        editBtn.setAttribute("aria-label", idleLabel);
        editBtn.setAttribute("title", idleLabel);
        return;
      }
      var labelEl = editBtn.querySelector("span");
      if (labelEl) {
        labelEl.textContent = idleSpanText;
      } else {
        editBtn.textContent = idleSpanText;
      }
      editBtn.setAttribute("aria-label", idleLabel);
      editBtn.setAttribute("title", idleLabel);
    }

    function enterEdit() {
      dispatchCloseAllGamePageEdits();
      viewEl.hidden = true;
      formEl.hidden = false;
      if (headActionsEl) {
        headActionsEl.hidden = false;
      }
      setEditButtonState(true);
    }

    function leaveEdit() {
      formEl.hidden = true;
      viewEl.hidden = false;
      if (headActionsEl) {
        headActionsEl.hidden = true;
      }
      setEditButtonState(false);
      if (typeof onLeaveEdit === "function") {
        onLeaveEdit();
      }
    }

    document.addEventListener(CG_EVT_CLOSE_ALL_GAME_EDITS, function () {
      leaveEdit();
    });

    editBtn.addEventListener("click", function () {
      if (formEl.hidden) {
        enterEdit();
      } else {
        leaveEdit();
      }
    });
    if (cancelBtn) {
      cancelBtn.addEventListener("click", leaveEdit);
    }

    if (section.getAttribute("data-reopen-edit") === "true") {
      enterEdit();
    }
  }

  [
    [
      ".cg-notes-card",
      "colGameNotesEditBtn",
      "colGameNotesView",
      "colGameNotesForm",
      "colGameNotesCancelBtn",
    ],
  ].forEach(function (r) {
    wireEditSection(r[0], r[1], r[2], r[3], r[4], r[5], r[6]);
  });

  (function wireColGamePersonalSplitEdit() {
    var shell = document.getElementById("colGamePersonalShell");
    var recordRead = document.getElementById("cgPlayRecordRead");
    var recordEdit = document.getElementById("cgPlayRecordEdit");
    var historyRead = document.getElementById("cgPlayHistoryRead");
    var editRecordBtn = document.getElementById("colGamePersonalEditBtn");
    var editHistoryBtn = document.getElementById("colGamePlayHistoryEditBtn");
    var cancelRecord = document.getElementById("colGamePersonalCancelBtn");
    var recordActions = document.getElementById("cgPlayRecordEditActions");
    if (!shell || !recordRead || !recordEdit || !historyRead) {
      return;
    }

    function setRecordEditing(on) {
      recordRead.hidden = !!on;
      recordEdit.hidden = !on;
      if (editRecordBtn) {
        editRecordBtn.hidden = !!on;
        editRecordBtn.setAttribute("aria-expanded", on ? "true" : "false");
      }
      if (recordActions) {
        recordActions.hidden = !on;
      }
      var recLabel = editRecordBtn
        ? getOrCreateColGameEditingLabel(editRecordBtn)
        : null;
      if (recLabel) {
        recLabel.hidden = !on;
      }
    }

    function setHistoryEditing(on) {
      /* Inline edit: keep the playthrough list and accordion chrome visible. */
      if (editHistoryBtn) {
        editHistoryBtn.setAttribute("aria-expanded", on ? "true" : "false");
      }
      var hLabel = editHistoryBtn
        ? getOrCreateColGameEditingLabel(editHistoryBtn)
        : null;
      if (hLabel) {
        hLabel.hidden = !on;
      }
    }

    function leaveAllEdit(keepClientDraftPlaythroughs) {
      setRecordEditing(false);
      setHistoryEditing(false);
      document.dispatchEvent(
        new CustomEvent("cg-inline-pt-close-all", {
          bubbles: true,
          detail: {
            keepClientDraftPlaythroughs: !!keepClientDraftPlaythroughs,
          },
        }),
      );
    }

    function enterRecordEdit() {
      dispatchCloseAllGamePageEdits();
      setRecordEditing(true);
    }

    function enterHistoryEdit() {
      dispatchCloseAllGamePageEdits();
      setHistoryEditing(true);
      document.dispatchEvent(new CustomEvent("cg-inline-pt-open", { bubbles: true }));
    }

    document.addEventListener(CG_EVT_CLOSE_ALL_GAME_EDITS, function (ev) {
      var d = (ev && ev.detail) || {};
      leaveAllEdit(!!d.keepClientDraftPlaythroughs);
    });

    if (editRecordBtn) {
      editRecordBtn.addEventListener("click", function () {
        if (sessionBlocksColGameEdits()) {
          return;
        }
        if (recordEdit.hidden) {
          enterRecordEdit();
        } else {
          leaveAllEdit();
        }
      });
    }
    if (editHistoryBtn) {
      editHistoryBtn.addEventListener("click", function () {
        if (sessionBlocksColGameEdits()) {
          return;
        }
        var expanded = editHistoryBtn.getAttribute("aria-expanded") === "true";
        if (!expanded) {
          enterHistoryEdit();
        } else {
          leaveAllEdit();
        }
      });
    }
    if (cancelRecord) {
      cancelRecord.addEventListener("click", leaveAllEdit);
    }
    shell.addEventListener("click", function (ev) {
      var goPt =
        ev.target && ev.target.closest
          ? ev.target.closest("a.js-cg-ps-goto-playthroughs")
          : null;
      if (!goPt || !shell.contains(goPt)) {
        return;
      }
      ev.preventDefault();
      leaveAllEdit();
      var href = goPt.getAttribute("href");
      if (href && href.charAt(0) === "#") {
        window.requestAnimationFrame(function () {
          var el = document.querySelector(href);
          if (!el) {
            return;
          }
          try {
            el.scrollIntoView({
              behavior: "smooth",
              block: "start",
              inline: "nearest",
            });
          } catch (e1) {
            try {
              el.scrollIntoView(true);
            } catch (e2) {
              /* no-op */
            }
          }
        });
      }
    });
    document.addEventListener("click", function (ev) {
      var c =
        ev.target && ev.target.closest
          ? ev.target.closest(".js-cg-history-cancel")
          : null;
      if (!c || !shell.contains(c)) {
        return;
      }
      ev.preventDefault();
      leaveAllEdit();
    });

    document.addEventListener("cg-open-history-with-focus", function (ev) {
      if (sessionBlocksColGameEdits()) {
        return;
      }
      var d = ev.detail || {};
      var anchor = d.anchorCard;
      if (anchor && anchor.nodeType === 1) {
        var isClientDraft =
          anchor.getAttribute &&
          anchor.getAttribute("data-cg-client-draft-row") === "true";
        dispatchCloseAllGamePageEdits(
          isClientDraft ? { keepClientDraftPlaythroughs: true } : undefined,
        );
        setHistoryEditing(true);
        document.dispatchEvent(
          new CustomEvent("cg-inline-pt-open", {
            bubbles: true,
            detail: { anchorCard: anchor },
          }),
        );
        return;
      }
      var pid =
        d.playthroughId != null && String(d.playthroughId).trim() !== ""
          ? String(d.playthroughId).trim()
          : null;
      dispatchCloseAllGamePageEdits();
      setHistoryEditing(true);
      document.dispatchEvent(
        new CustomEvent("cg-inline-pt-open", {
          bubbles: true,
          detail: { playthroughId: pid },
        }),
      );
    });

    function attrTruthy(el, name) {
      var v = el && el.getAttribute ? el.getAttribute(name) : null;
      if (v == null || v === "") {
        return false;
      }
      var s = String(v).toLowerCase().trim();
      return s === "true" || s === "1" || s === "yes";
    }

    if (attrTruthy(shell, "data-reopen-edit")) {
      enterRecordEdit();
    } else if (attrTruthy(shell, "data-reopen-history")) {
      window.setTimeout(function () {
        enterHistoryEdit();
      }, 0);
    } else {
      leaveAllEdit();
    }
  })();

  (function wireColGamePersonalSubmitScopes() {
    var form = document.getElementById("colGamePersonalForm");
    if (!form) {
      return;
    }
    form.addEventListener(
      "submit",
      function (e) {
        var jsonField = document.getElementById("colGamePlaythroughsJson");
        var sc = document.getElementById("colGameHiddenSyncCore");
        var sp = document.getElementById("colGameHiddenSyncPlaythroughs");
        if (!sc || !sp) {
          return;
        }
        var sub = e.submitter;
        if (sub && sub.id === "colGamePersonalSaveCoreBtn") {
          sc.value = "true";
          sp.value = "false";
          if (jsonField) {
            jsonField.disabled = true;
          }
        } else if (
          sub &&
          (sub.id === "colGamePersonalSaveHistoryBtn" ||
            sub.classList.contains("js-cg-history-save"))
        ) {
          sc.value = "false";
          sp.value = "true";
          if (jsonField) {
            jsonField.disabled = false;
          }
        }
      },
      true,
    );
  })();
  document.addEventListener("DOMContentLoaded", function () {
    var pageFlashes = document.querySelector(".cg-page-flashes");
    if (pageFlashes && pageFlashes.children.length > 0) {
      // After play-log save/delete we skip scrolling so the page stays at the top; the
      // hero log controls remain in a natural position (see body data-cg-skip-flash-scroll).
      var skipFlashScroll =
        document.body &&
        document.body.getAttribute("data-cg-skip-flash-scroll") === "true";
      if (!skipFlashScroll) {
        var reduceMotion =
          window.matchMedia &&
          window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        try {
          pageFlashes.scrollIntoView({
            behavior: reduceMotion ? "auto" : "smooth",
            block: "nearest",
          });
        } catch (err) {
          pageFlashes.scrollIntoView(true);
        }
      }
    }

    function wireClampedTextToggle(bodyEl, toggleEl) {
      if (!bodyEl || !toggleEl) {
        return;
      }

      function setExpanded(expanded) {
        if (expanded) {
          bodyEl.classList.remove("cg-notes-body--clamped");
          toggleEl.textContent = "Show less";
          toggleEl.setAttribute("aria-expanded", "true");
        } else {
          bodyEl.classList.add("cg-notes-body--clamped");
          toggleEl.textContent = "Show more";
          toggleEl.setAttribute("aria-expanded", "false");
        }
      }

      function refreshToggleVisibility() {
        setExpanded(false);
        var hasOverflow = bodyEl.scrollHeight > bodyEl.clientHeight + 2;
        if (hasOverflow) {
          toggleEl.classList.add("is-visible");
          toggleEl.removeAttribute("aria-hidden");
        } else {
          bodyEl.classList.remove("cg-notes-body--clamped");
          toggleEl.classList.remove("is-visible");
          toggleEl.setAttribute("aria-hidden", "true");
        }
      }

      refreshToggleVisibility();

      toggleEl.addEventListener("click", function () {
        var isClamped = bodyEl.classList.contains("cg-notes-body--clamped");
        setExpanded(isClamped);
      });

      window.addEventListener("resize", function () {
        var wasExpanded = toggleEl.getAttribute("aria-expanded") === "true";
        refreshToggleVisibility();
        if (wasExpanded && toggleEl.classList.contains("is-visible")) {
          setExpanded(true);
        }
      });
    }

    [
      ["colGameReviewReadBody", "colGameReviewReadToggle"],
    ].forEach(function (ids) {
      wireClampedTextToggle(
        document.getElementById(ids[0]),
        document.getElementById(ids[1]),
      );
    });

    (function wireInfoGenresTwoLineOverflow() {
      var HID = "cg-genre-chip--overflow-hidden";

      function apply(wrap) {
        var prevMore = wrap.querySelector(".cg-genre-chips-overflow");
        if (prevMore) {
          prevMore.remove();
        }
        var chips = [].slice.call(wrap.querySelectorAll(".cg-genre-chip"));
        chips.forEach(function (c) {
          c.classList.remove(HID);
        });
        if (chips.length === 0) {
          return;
        }
        var tops = [];
        var j;
        for (j = 0; j < chips.length; j++) {
          var t = chips[j].offsetTop;
          if (tops.indexOf(t) === -1) {
            tops.push(t);
          }
        }
        tops.sort(function (a, b) {
          return a - b;
        });
        if (tops.length <= 2) {
          return;
        }
        var secondLineTop = tops[1];
        var nHidden = 0;
        for (j = 0; j < chips.length; j++) {
          if (chips[j].offsetTop > secondLineTop) {
            chips[j].classList.add(HID);
            nHidden += 1;
          }
        }
        if (nHidden > 0) {
          var more = document.createElement("span");
          more.className = "cg-genre-chips-overflow";
          more.textContent = "+" + nHidden;
          more.setAttribute(
            "title",
            nHidden + " more genre" + (nHidden === 1 ? "" : "s"),
          );
          more.setAttribute("aria-label", nHidden + " more genres");
          wrap.appendChild(more);
        }
      }

      function runAll() {
        document.querySelectorAll(".js-info-genres-chips").forEach(apply);
      }

      runAll();
      if (document.fonts && document.fonts.ready) {
        document.fonts.ready.then(runAll);
      }
      if (typeof window.ResizeObserver === "function") {
        document.querySelectorAll(".js-info-genres-chips").forEach(function (w) {
          var ro = new ResizeObserver(function () {
            apply(w);
          });
          ro.observe(w);
        });
      } else {
        window.addEventListener("resize", function () {
          runAll();
        });
      }
    })();

    var settingsBtn = document.getElementById("colGameSettingsBtn");
    var settingsPanel = document.getElementById("colGameSettingsPanel");
    function setSettingsMenuOpen(open) {
      if (!settingsBtn || !settingsPanel) {
        return;
      }
      settingsBtn.setAttribute("aria-expanded", open ? "true" : "false");
      settingsPanel.hidden = !open;
      settingsPanel.setAttribute("aria-hidden", open ? "false" : "true");
    }
    if (settingsBtn && settingsPanel) {
      settingsBtn.addEventListener("click", function () {
        var isOpen = settingsBtn.getAttribute("aria-expanded") === "true";
        setSettingsMenuOpen(!isOpen);
      });
      document.addEventListener("click", function (event) {
        if (
          !settingsPanel.hidden &&
          !settingsPanel.contains(event.target) &&
          !settingsBtn.contains(event.target)
        ) {
          setSettingsMenuOpen(false);
        }
      });
      document.addEventListener("keydown", function (event) {
        if (event.key === "Escape") {
          setSettingsMenuOpen(false);
        }
      });
    }

    var ratingWrap = document.getElementById("colGamePersonalRatingWrap");
    var ratingHidden = document.getElementById("colGamePersonalRatingHidden");
    var ratingClearBtn = document.getElementById("colGameRatingClearBtn");
    var reviewTextarea = document.getElementById("colGamePersonalReview");
    var reviewCounter = document.getElementById("colGamePersonalReviewCounter");
    var reviewHeadlineInput = document.getElementById(
      "colGamePersonalReviewHeadline",
    );
    var reviewHeadlineHidden = document.getElementById(
      "colGamePersonalReviewHeadlineHidden",
    );

    function syncReviewHeadlineToHidden() {
      if (!reviewHeadlineHidden) {
        return;
      }
      if (!reviewHeadlineInput) {
        reviewHeadlineHidden.value = "";
        return;
      }
      reviewHeadlineHidden.value = reviewHeadlineInput.value;
    }

    function refreshStarEditFaces(value) {
      if (!ratingWrap) {
        return;
      }
      var slots = ratingWrap.querySelectorAll(".cg-star-slot");
      var r =
        value === null || value === undefined || value === ""
          ? NaN
          : parseInt(value, 10);
      var unrated = isNaN(r) || r < 1 || r > 10;
      slots.forEach(function (slot, idx) {
        var s = idx + 1;
        var icon = slot.querySelector(".cg-star-slot__icon");
        if (!icon) {
          return;
        }
        icon.className = "cg-star-slot__icon";
        slot.classList.toggle(
          "cg-star-slot--filled",
          !unrated && r >= 2 * s - 1,
        );
        if (unrated) {
          icon.classList.add("fa-regular", "fa-star");
          return;
        }
        if (r >= 2 * s) {
          icon.classList.add("fa-solid", "fa-star");
        } else if (r === 2 * s - 1) {
          icon.classList.add("fa-solid", "fa-star-half-stroke");
        } else {
          icon.classList.add("fa-regular", "fa-star");
        }
      });
    }

    function hasRatingSelected() {
      if (!ratingHidden) {
        return false;
      }
      var raw = String(ratingHidden.value).trim();
      if (raw === "") {
        return false;
      }
      var v = parseInt(raw, 10);
      return !isNaN(v) && v >= 1 && v <= 10;
    }

    function setRatingValue(n) {
      if (!ratingHidden) {
        return;
      }
      var v = parseInt(n, 10);
      if (isNaN(v) || v < 1 || v > 10) {
        return;
      }
      ratingHidden.value = String(v);
      refreshStarEditFaces(v);
    }

    function clearRatingValue() {
      if (!ratingHidden) {
        return;
      }
      ratingHidden.value = "";
      refreshStarEditFaces(null);
    }

    function hideRatingFormClientError() {
      var err = document.getElementById("colGameRatingFormError");
      if (err) {
        err.hidden = true;
        err.textContent = "";
      }
    }

    function initRatingFromDom() {
      if (!ratingWrap || !ratingHidden) {
        return;
      }
      var raw = ratingWrap.getAttribute("data-initial-rating");
      if (raw !== null && raw !== "") {
        var n = parseInt(raw, 10);
        if (!isNaN(n) && n >= 1 && n <= 10) {
          ratingHidden.value = String(n);
          refreshStarEditFaces(n);
          return;
        }
      }
      clearRatingValue();
    }

    function refreshMaxlengthCounter(ta, cnt, defaultMax) {
      if (!ta || !cnt) {
        return;
      }
      var max = parseInt(ta.getAttribute("maxlength"), 10);
      if (isNaN(max) || max < 1) {
        max = defaultMax;
      }
      cnt.textContent = String(ta.value.length) + " / " + String(max);
    }

    function refreshReviewCounter() {
      refreshMaxlengthCounter(reviewTextarea, reviewCounter, 3000);
    }

    initRatingFromDom();

    if (ratingWrap) {
      ratingWrap.querySelectorAll(".cg-star-slot__hit").forEach(function (btn) {
        btn.addEventListener("click", function () {
          var raw = btn.getAttribute("data-rating");
          var v = parseInt(raw, 10);
          if (!isNaN(v) && v >= 1 && v <= 10) {
            setRatingValue(v);
            hideRatingFormClientError();
          }
        });
      });
    }
    if (ratingClearBtn) {
      ratingClearBtn.addEventListener("click", function () {
        clearRatingValue();
        hideRatingFormClientError();
      });
    }

    var MAX_PERSONAL_WHY = 3;
    function colGameWhyChipsWithValue() {
      return Array.prototype.filter.call(
        document.querySelectorAll(".js-why-playing-chip"),
        function (c) {
          var d = c.getAttribute("data-why-playing");
          return d !== null && String(d) !== "";
        },
      );
    }
    function colGameWhyActiveCount() {
      var n = 0;
      colGameWhyChipsWithValue().forEach(function (c) {
        if (c.classList.contains("is-active")) {
          n += 1;
        }
      });
      return n;
    }
    function colGameSyncWhyHidden() {
      var hidden = document.getElementById("colGamePersonalWhyCsv");
      if (!hidden) {
        return;
      }
      var parts = [];
      colGameWhyChipsWithValue().forEach(function (c) {
        if (c.classList.contains("is-active")) {
          parts.push(String(c.getAttribute("data-why-playing")));
        }
      });
      hidden.value = parts.join(",");
    }
    function colGameRefreshWhyEditDescription() {
      var panel = document.getElementById("colGamePersonalWhyDescriptionPanel");
      var listWrap = document.getElementById("colGamePersonalWhyDescriptionList");
      if (!panel || !listWrap) {
        return;
      }
      var selected = [];
      colGameWhyChipsWithValue().forEach(function (c) {
        if (!c.classList.contains("is-active")) {
          return;
        }
        selected.push({
          title: String(c.getAttribute("data-why") || "").trim(),
          description: String(c.getAttribute("data-why-description") || "").trim(),
        });
      });
      if (!selected.length) {
        listWrap.innerHTML =
          '<p class="cg-subtle-field-hint cg-why-description">Pick up to 3 intents to describe why you play.</p>';
        panel.classList.remove("is-selected");
        return;
      }
      listWrap.innerHTML = "";
      var ul = document.createElement("ul");
      ul.className = "cg-why-description-list";
      selected.forEach(function (item) {
        var li = document.createElement("li");
        li.className = "cg-why-description-list__item";
        var lineP = document.createElement("p");
        lineP.className =
          "cg-subtle-field-hint cg-why-description cg-why-description--inline";
        var nameSpan = document.createElement("span");
        nameSpan.className = "cg-why-description-list__name";
        nameSpan.textContent = item.title + ":";
        var textSpan = document.createElement("span");
        textSpan.className = "cg-why-description-list__text";
        textSpan.textContent = " " + item.description;
        lineP.appendChild(nameSpan);
        lineP.appendChild(textSpan);
        li.appendChild(lineP);
        ul.appendChild(li);
      });
      listWrap.appendChild(ul);
      panel.classList.add("is-selected");
    }
    function colGameSetReadWhyDescription(title, text) {
      var panel = document.getElementById("cgPsWhyReadDescriptionPanel");
      var headingEl = document.getElementById("cgPsWhyReadSelection");
      var descEl = document.getElementById("cgPsWhyReadDescription");
      if (!panel || !headingEl || !descEl) {
        return;
      }
      var selectedTitle = String(title || "").trim();
      var description = String(text || "").trim();
      var hasContent = selectedTitle !== "" && description !== "";
      panel.hidden = !hasContent;
      panel.classList.toggle("is-selected", hasContent);
      if (!hasContent) {
        headingEl.textContent = "";
        descEl.textContent = "";
        return;
      }
      headingEl.textContent = selectedTitle;
      descEl.textContent = description;
    }
    function colGameSetActiveReadWhyChip(chip) {
      var chips = document.querySelectorAll(".js-why-read-chip");
      var targetChip = chip;
      if (!targetChip && chips.length > 0) {
        targetChip = chips[0];
      }
      chips.forEach(function (c) {
        var active = targetChip ? c === targetChip : false;
        c.classList.toggle("is-active", active);
        c.setAttribute("aria-pressed", active ? "true" : "false");
      });
      if (!targetChip) {
        colGameSetReadWhyDescription("", "");
        return;
      }
      colGameSetReadWhyDescription(
        targetChip.getAttribute("data-why"),
        targetChip.getAttribute("data-why-description"),
      );
    }
    (function wirePersonalPlayHoursField() {
      var el = document.getElementById("colGamePersonalPlayHours");
      if (!el) {
        return;
      }
      el.addEventListener("input", function () {
        var s = String(el.value);
        var cleaned = s.replace(/[^\d.]/g, "");
        var parts = cleaned.split(".");
        if (parts.length > 2) {
          cleaned = parts[0] + "." + parts.slice(1).join("");
        }
        if (cleaned.length > 14) {
          cleaned = cleaned.slice(0, 14);
        }
        if (el.value !== cleaned) {
          el.value = cleaned;
        }
      });
    })();

    (function wirePlaythroughEditor() {
      var form = document.getElementById("colGamePersonalForm");
      var hidden = document.getElementById("colGamePlaythroughsJson");
      var list = document.getElementById("cgPlaythroughRows");
      var editor = document.getElementById("cgPlaythroughEditorShell");
      var editHistoryBtn = document.getElementById("colGamePlayHistoryEditBtn");
      if (!form || !hidden) {
        return;
      }
      /* `cgPlaythroughRows` is absent when there are no playthroughs. `cgPlaythroughEditorShell` is optional;
               inline edit must not require it (listeners were previously skipped when the shell was missing). */
      if (!list) {
        return;
      }

      var addBtn = document.getElementById("cgPlaythroughAddBtn");
      var addBtnEmpty = document.getElementById("cgPlaythroughAddBtnEmpty");
      var newRowTpl = document.getElementById("cgPlaythroughNewRowTemplate");
      function maxPlaythroughsAllowed() {
        var n = editor && editor.getAttribute("data-max");
        if (n != null && String(n).trim() !== "") {
          var x = parseInt(String(n).trim(), 10);
          if (Number.isFinite(x) && x > 0) {
            return x;
          }
        }
        var maxSrc = addBtn || addBtnEmpty;
        if (maxSrc) {
          var m = maxSrc.getAttribute("data-pt-max");
          if (m != null && String(m).trim() !== "") {
            var y = parseInt(String(m).trim(), 10);
            if (Number.isFinite(y) && y > 0) {
              return y;
            }
          }
        }
        return 10;
      }

      function closeAllInlineEdits() {
        list.querySelectorAll("li.cgpd-card").forEach(function (card) {
          var editBtn = card.querySelector(
            ".cgpd-card__edit-btn--header[data-cg-edit-playthrough]",
          );
          if (!editBtn) {
            return;
          }
          var label = getOrCreateColGameEditingLabel(editBtn);
          editBtn.hidden = false;
          if (label) {
            label.hidden = true;
          }
        });
        list
          .querySelectorAll(".cgpd-card__inline-actions")
          .forEach(function (el) {
            el.hidden = true;
          });
        list
          .querySelectorAll(".cgpd-card--inline-editing")
          .forEach(function (card) {
            card.classList.remove("cgpd-card--inline-editing");
            var read = card.querySelector(".cgpd-card__body-read");
            var edit = card.querySelector(".cgpd-card__body-edit");
            if (read) {
              read.hidden = false;
            }
            if (edit) {
              edit.hidden = true;
            }
          });
        if (editor) {
          editor.removeAttribute("data-edit-focus-id");
        }
        syncPlaythroughAddButtonState();
      }

      function removeUnsavedClientDraftPlaythroughRows() {
        var n = 0;
        list
          .querySelectorAll('li.cgpd-card[data-cg-client-draft-row="true"]')
          .forEach(function (li) {
            li.remove();
            n++;
          });
        return n;
      }

      /** After removing a client draft row, the previously expanded active playthrough stays collapsed; re-open it. */
      function expandAccordionForActivePlaythrough() {
        var rows = list.querySelectorAll("[data-cg-row]");
        var targetCard = null;
        for (var i = 0; i < rows.length; i++) {
          var sel = rows[i].querySelector("[data-cg-progress-status]");
          if (
            sel &&
            String(sel.value != null ? sel.value : "")
              .trim()
              .toUpperCase() === "ACTIVE"
          ) {
            targetCard = rows[i].closest("li.cgpd-card");
            break;
          }
        }
        if (!targetCard) {
          return;
        }
        var det = targetCard.querySelector("details.cg-pt-accordion");
        if (det) {
          det.open = true;
        }
      }

      function openInlinePlaythroughEditForCard(target) {
        if (!target || !list.contains(target)) {
          return;
        }
        closeAllInlineEdits();
        var tid = target.getAttribute("data-playthrough-id");
        if (editor) {
          if (tid != null && String(tid).trim() !== "") {
            editor.setAttribute("data-edit-focus-id", String(tid).trim());
          } else {
            editor.removeAttribute("data-edit-focus-id");
          }
        }
        var details = target.querySelector("details.cg-pt-accordion");
        if (details) {
          details.open = true;
        }
        target.classList.add("cgpd-card--inline-editing");
        var editBtn = target.querySelector(
          ".cgpd-card__edit-btn--header[data-cg-edit-playthrough]",
        );
        if (editBtn) {
          var editingLabel = getOrCreateColGameEditingLabel(editBtn);
          editBtn.hidden = true;
          if (editingLabel) {
            editingLabel.hidden = false;
          }
        }
        var read = target.querySelector(".cgpd-card__body-read");
        var edit = target.querySelector(".cgpd-card__body-edit");
        if (read) {
          read.hidden = true;
        }
        if (edit) {
          edit.hidden = false;
        }
        var act = target.querySelector(".cgpd-card__inline-actions");
        if (act) {
          act.hidden = false;
        }
        var row = target.querySelector("[data-cg-row]");
        if (row) {
          syncDifficultyUi(row);
          syncInlinePctBar(row);
        }
        tryPushPlaythroughsToHidden();
        syncPlaythroughAddButtonState();
        window.setTimeout(function () {
          try {
            target.scrollIntoView({ block: "nearest", behavior: "smooth" });
          } catch (e1) {
            target.scrollIntoView(true);
          }
          var nameInp = target.querySelector(".cgpt-short-name");
          if (nameInp) {
            try {
              nameInp.focus({ preventScroll: true });
            } catch (e2) {
              nameInp.focus();
            }
          }
        }, 80);
      }

      function openInlinePlaythroughEdit(rawId) {
        var tid =
          rawId != null && String(rawId).trim() !== ""
            ? String(rawId).trim()
            : null;
        var cards = list.querySelectorAll("li.cgpd-card");
        var target = null;
        if (tid) {
          for (var i = 0; i < cards.length; i++) {
            var rid = cards[i].getAttribute("data-playthrough-id");
            if (rid != null && String(rid) === tid) {
              target = cards[i];
              break;
            }
          }
        }
        if (!target && cards.length) {
          target = cards[0];
        }
        if (!target) {
          return;
        }
        openInlinePlaythroughEditForCard(target);
      }

      function applyPlaythroughEditFocus(rawId) {
        openInlinePlaythroughEdit(rawId);
      }
      window.cgApplyPlaythroughEditFocus = applyPlaythroughEditFocus;

      document.addEventListener("cg-inline-pt-open", function (ev) {
        var d = (ev && ev.detail) || {};
        var anchor = d.anchorCard;
        if (anchor && anchor.nodeType === 1 && list.contains(anchor)) {
          openInlinePlaythroughEditForCard(anchor);
          return;
        }
        var pid =
          d.playthroughId != null && String(d.playthroughId).trim() !== ""
            ? String(d.playthroughId).trim()
            : null;
        openInlinePlaythroughEdit(pid);
      });
      document.addEventListener("cg-inline-pt-close-all", function (ev) {
        var d = (ev && ev.detail) || {};
        closeAllInlineEdits();
        var removedDrafts = 0;
        if (!d.keepClientDraftPlaythroughs) {
          removedDrafts = removeUnsavedClientDraftPlaythroughRows();
        }
        ensurePlaythroughStatusCoherent();
        syncPlaythroughEmptyState();
        tryPushPlaythroughsToHidden();
        if (removedDrafts > 0) {
          expandAccordionForActivePlaythrough();
        }
      });

      function rowCount() {
        return list.querySelectorAll("[data-cg-row]").length;
      }

      function syncCgDropdownFieldFromHidden(hidden) {
        if (
          !hidden ||
          hidden.nodeName !== "INPUT" ||
          hidden.type !== "hidden"
        ) {
          return;
        }
        var field = hidden.closest(".cg-dropdown-field");
        if (!field) {
          return;
        }
        var root = field.querySelector(".cg-form-dropdown");
        var labelEl = root && root.querySelector(".dropdown-label");
        var panel = root && root.querySelector(".dropdown-panel");
        if (!labelEl || !panel) {
          return;
        }
        var val = hidden.value != null ? String(hidden.value) : "";
        var matched = null;
        panel.querySelectorAll(".dropdown-option").forEach(function (opt) {
          var raw = opt.getAttribute("data-value");
          var ov = raw === null ? "" : String(raw);
          var isMatch = ov === val;
          opt.classList.toggle("is-selected", isMatch);
          if (isMatch) {
            matched = opt;
          }
        });
        if (matched) {
          labelEl.textContent = matched.textContent.trim();
        }
      }

      function syncDifficultyUi(row) {
        if (!row) {
          return;
        }
        var preset = row.querySelector("[data-cg-diff-preset]");
        var custom = row.querySelector("[data-cg-diff-custom]");
        if (!preset || !custom) {
          return;
        }
        function isStandardDifficultyToken(v) {
          if (v == null || v === "" || v === "__CUSTOM__") {
            return false;
          }
          var t = String(v).trim();
          return (
            t === "Easy" || t === "Normal" || t === "Hard" || t === "Very Hard"
          );
        }
        /* If the hidden field ever holds freeform text (legacy / bad state), move it to __CUSTOM__ + text box */
        var pv0 = String(preset.value != null ? preset.value : "").trim();
        if (
          pv0 !== "" &&
          pv0 !== "__CUSTOM__" &&
          !isStandardDifficultyToken(pv0)
        ) {
          if (!String(custom.value || "").trim()) {
            custom.value = pv0;
          }
          preset.value = "__CUSTOM__";
        }
        syncCgDropdownFieldFromHidden(preset);
        var show =
          String(preset.value != null ? preset.value : "").trim() ===
          "__CUSTOM__";
        custom.hidden = !show;
        custom.setAttribute("aria-hidden", show ? "false" : "true");
      }

      function syncPlaythroughAddButtonState() {
        var atCap = rowCount() >= maxPlaythroughsAllowed();
        var editing = list.querySelector(".cgpd-card--inline-editing") !== null;
        var blocked = atCap || editing;
        var title = "";
        if (editing && !atCap) {
          title =
            "Save or cancel the playthrough you are editing before adding another.";
        } else if (atCap) {
          title = "Maximum playthroughs reached for this game.";
        }
        [addBtn, addBtnEmpty].forEach(function (b) {
          if (!b) {
            return;
          }
          b.disabled = blocked;
          if (title) {
            b.setAttribute("title", title);
          } else {
            b.removeAttribute("title");
          }
        });
      }

      function syncPlaythroughEmptyState() {
        var hasCards = list.querySelectorAll("li.cgpd-card").length > 0;
        var emptyEl = document.getElementById("cgPlaythroughEmptyState");
        if (emptyEl) {
          emptyEl.hidden = !!hasCards;
        }
        var ptFooter = document.getElementById("cgPlaythroughSectionFooter");
        if (ptFooter) {
          ptFooter.hidden = !hasCards;
        }
        list.classList.toggle("cgpd-list--empty", !hasCards);
        syncPlaythroughAddButtonState();
      }

      /**
       * Keeps Play Summary "Active playthrough" in sync with the playthrough list
       * (e.g. after deleting the active playthrough without a full page reload).
       */
      function syncPlaySummaryActivePlaythroughFromList() {
        var section = document.getElementById("cgPsCurrentPtSection");
        var emptyWrap = document.getElementById("cgPsActivePtEmptyWrap");
        var emptyNoPts = document.getElementById("cgPsActivePtEmptyNoPts");
        var emptyNoActive = document.getElementById("cgPsActivePtEmptyNoActive");
        var read = document.getElementById("cgPsActivePtRead");
        var jumpLink = document.getElementById("cgPsAptJumpLink");
        if (!section || !emptyWrap) {
          return;
        }
        var cards = list.querySelectorAll("li.cgpd-card");
        var n = cards.length;
        var activeCard = null;
        for (var i = 0; i < cards.length; i++) {
          var row = cards[i].querySelector("[data-cg-row]");
          var sel = row && row.querySelector("[data-cg-progress-status]");
          if (
            sel &&
            String(sel.value != null ? sel.value : "").trim().toUpperCase() ===
              "ACTIVE"
          ) {
            activeCard = cards[i];
            break;
          }
        }
        if (!activeCard) {
          section.classList.add("cg-ps-current-pt-section--empty");
          emptyWrap.hidden = false;
          if (emptyNoPts && emptyNoActive) {
            emptyNoPts.hidden = n > 0;
            emptyNoActive.hidden = n === 0;
          }
          if (read) {
            read.hidden = true;
          }
          if (jumpLink) {
            jumpLink.hidden = true;
            jumpLink.setAttribute("href", "#");
          }
        } else {
          section.classList.remove("cg-ps-current-pt-section--empty");
          emptyWrap.hidden = true;
          if (read) {
            read.hidden = false;
          }
          var pid = activeCard.getAttribute("data-playthrough-id");
          if (jumpLink && pid != null && String(pid).trim() !== "") {
            jumpLink.hidden = false;
            jumpLink.setAttribute("href", "#cg-pt-card-" + String(pid).trim());
          }
        }
      }

      function rowDifficultyValue(row) {
        var preset = row.querySelector("[data-cg-diff-preset]");
        var custom = row.querySelector("[data-cg-diff-custom]");
        if (!preset) {
          return "Normal";
        }
        if (preset.value === "__CUSTOM__") {
          var c = custom ? String(custom.value || "").trim() : "";
          return c === "" ? "Normal" : c;
        }
        var pv = String(preset.value || "").trim();
        return pv === "" ? "Normal" : pv;
      }

      function parseRowId(row) {
        var raw = row.getAttribute("data-playthrough-id");
        if (raw === null || raw === "") {
          return null;
        }
        var n = parseInt(String(raw), 10);
        return Number.isFinite(n) ? n : null;
      }

      var MAX_PT_MANUAL_MINUTES = 50000 * 60;

      function rowManualPlayMinutes(row) {
        var hInp = row.querySelector("[data-cg-hours]");
        var mInp = row.querySelector("[data-cg-mins]");
        if (!hInp || !mInp) {
          return null;
        }
        var hRaw = String(hInp.value != null ? hInp.value : "").trim();
        var mRaw = String(mInp.value != null ? mInp.value : "").trim();
        if (hRaw === "" && mRaw === "") {
          return null;
        }
        if (hRaw !== "" && !/^\d{1,3}$/.test(hRaw)) {
          return -1;
        }
        if (mRaw !== "" && !/^\d{1,3}$/.test(mRaw)) {
          return -1;
        }
        var h = hRaw === "" ? 0 : parseInt(hRaw, 10);
        var mi = mRaw === "" ? 0 : parseInt(mRaw, 10);
        if (isNaN(h) || h < 0 || h > 999) {
          return -1;
        }
        if (isNaN(mi) || mi < 0 || mi > 59) {
          return -1;
        }
        var total = h * 60 + mi;
        if (total <= 0) {
          return null;
        }
        if (total > MAX_PT_MANUAL_MINUTES) {
          return -1;
        }
        return total;
      }

      function rowShortName(row) {
        var inp = row.querySelector(".cgpt-short-name");
        if (!inp) {
          return null;
        }
        var v = String(inp.value != null ? inp.value : "").trim();
        return v === "" ? null : v;
      }

      function rowProgressNote(row) {
        var inp = row.querySelector("[data-cg-progress-note]");
        if (!inp) {
          return null;
        }
        var v = String(inp.value != null ? inp.value : "").trim();
        return v === "" ? null : v;
      }

      function rowCompletionPercent(row) {
        var inp = row.querySelector("[data-cg-completion-percent]");
        if (!inp) {
          return null;
        }
        var raw = String(inp.value != null ? inp.value : "").trim();
        if (raw === "") {
          return null;
        }
        var n = parseInt(raw, 10);
        if (!Number.isFinite(n)) {
          return null;
        }
        if (n < 0) {
          return 0;
        }
        if (n > 100) {
          return 100;
        }
        return n;
      }

      function rowEndDate(row) {
        var inp = row.querySelector("[data-cg-end-date]");
        if (!inp) {
          return null;
        }
        var v = String(inp.value != null ? inp.value : "").trim();
        return v === "" ? null : v;
      }

      function rowPlaythroughState(row) {
        var sel = row.querySelector("[data-cg-progress-status]");
        var v = sel
          ? String(sel.value != null ? sel.value : "")
              .trim()
              .toUpperCase()
          : "STOPPED";
        if (v === "ACTIVE") {
          return { current: true, progressStatus: "PLAYING" };
        }
        if (v === "FINISHED") {
          return { current: false, progressStatus: "COMPLETED" };
        }
        return { current: false, progressStatus: "STOPPED" };
      }

      function rowProgressStatus(row) {
        return rowPlaythroughState(row).progressStatus;
      }

      function syncInlinePctBar(row) {
        if (!row) {
          return;
        }
        var inp = row.querySelector("[data-cg-completion-percent]");
        var fill = row.querySelector("[data-cg-pct-bar-fill]");
        var wrap = row.querySelector("[data-cg-pct-bar-wrap]");
        if (!inp || !fill || !wrap) {
          return;
        }
        var raw = String(inp.value != null ? inp.value : "").trim();
        var n = raw === "" ? NaN : parseInt(raw, 10);
        if (!Number.isFinite(n)) {
          fill.style.width = "0%";
          wrap.hidden = true;
          return;
        }
        var pct = Math.max(0, Math.min(100, n));
        fill.style.width = pct + "%";
        wrap.hidden = false;
      }

      list.querySelectorAll("[data-cg-row]").forEach(function (row) {
        syncDifficultyUi(row);
        syncInlinePctBar(row);
      });

      function buildPlaythroughPayload(includeDrafts) {
        if (includeDrafts === undefined) {
          includeDrafts = true;
        }
        var items = [];
        var badTime = false;
        var badShortName = false;
        var hasDraftRows =
          includeDrafts &&
          list.querySelector('[data-cg-row][data-cg-draft="true"]') !== null;
        list.querySelectorAll("[data-cg-row]").forEach(function (row) {
          if (!includeDrafts && row.getAttribute("data-cg-draft") === "true") {
            return;
          }
          var st = rowPlaythroughState(row);
          var isDraft = row.getAttribute("data-cg-draft") === "true";
          if (hasDraftRows && includeDrafts) {
            if (isDraft) {
              st = { current: true, progressStatus: "PLAYING" };
            } else if (st.current) {
              st = { current: false, progressStatus: "STOPPED" };
            }
          }
          var mm = rowManualPlayMinutes(row);
          if (mm === -1) {
            badTime = true;
            return;
          }
          var sn = rowShortName(row);
          if (sn == null) {
            badShortName = true;
          }
          items.push({
            id: parseRowId(row),
            shortName: sn,
            difficulty: rowDifficultyValue(row),
            current: st.current,
            manualPlayMinutes: mm,
            completionPercent: rowCompletionPercent(row),
            progressNote: rowProgressNote(row),
            progressStatus: st.progressStatus,
            endDate: rowEndDate(row),
          });
        });
        return {
          items: items,
          badTime: badTime,
          badShortName: badShortName,
        };
      }

      function tryPushPlaythroughsToHidden() {
        var p = buildPlaythroughPayload(false);
        if (p.badTime || p.badShortName) {
          return false;
        }
        /* Core save sets this field disabled; re-enable so deletes/edits still serialize on History Save. */
        if (hidden.disabled) {
          hidden.disabled = false;
        }
        hidden.value = JSON.stringify({ items: p.items });
        return true;
      }

      function ensurePlaythroughStatusCoherent() {
        var rows = Array.prototype.slice.call(
          list.querySelectorAll("[data-cg-row]"),
        );
        if (rows.length === 0) {
          return;
        }
        var activeRows = rows.filter(function (row) {
          var sel = row.querySelector("[data-cg-progress-status]");
          return sel && String(sel.value).toUpperCase() === "ACTIVE";
        });
        if (activeRows.length > 1) {
          activeRows.slice(1).forEach(function (row) {
            var s = row.querySelector("[data-cg-progress-status]");
            if (s) {
              s.value = "STOPPED";
              syncCgDropdownFieldFromHidden(s);
            }
          });
        }
      }

      list.addEventListener("change", function (e) {
        var t = e.target;
        if (t && t.matches && t.matches("[data-cg-diff-preset]")) {
          syncDifficultyUi(t.closest("[data-cg-row]"));
          tryPushPlaythroughsToHidden();
        } else if (t && t.matches && t.matches("[data-cg-end-date]")) {
          tryPushPlaythroughsToHidden();
        } else if (t && t.matches && t.matches("[data-cg-completion-percent]")) {
          syncInlinePctBar(t.closest("[data-cg-row]"));
          tryPushPlaythroughsToHidden();
        } else if (t && t.matches && t.matches("[data-cg-progress-status]")) {
          var statusRow = t.closest("[data-cg-row]");
          if (String(t.value).toUpperCase() === "ACTIVE") {
            list
              .querySelectorAll("[data-cg-progress-status]")
              .forEach(function (sel) {
                if (sel !== t) {
                  sel.value = "STOPPED";
                  syncCgDropdownFieldFromHidden(sel);
                }
              });
          }
          ensurePlaythroughStatusCoherent();
          tryPushPlaythroughsToHidden();
        }
      });

      list.addEventListener("input", function (e) {
        var t = e.target;
        if (!t || !t.matches) {
          return;
        }
        if (t.matches("[data-cg-hours]")) {
          var vh = String(t.value != null ? t.value : "")
            .replace(/\D/g, "")
            .slice(0, 3);
          if (t.value !== vh) {
            t.value = vh;
          }
          tryPushPlaythroughsToHidden();
          return;
        }
        if (t.matches("[data-cg-mins]")) {
          var vm = String(t.value != null ? t.value : "")
            .replace(/\D/g, "")
            .slice(0, 3);
          if (vm !== "" && parseInt(vm, 10) > 59) {
            vm = "59";
          }
          if (t.value !== vm) {
            t.value = vm;
          }
          tryPushPlaythroughsToHidden();
          return;
        }
        if (
          t.matches(".cgpt-short-name") ||
          t.matches("[data-cg-diff-custom]")
        ) {
          tryPushPlaythroughsToHidden();
          return;
        }
        if (t.matches("[data-cg-progress-note]")) {
          tryPushPlaythroughsToHidden();
          return;
        }
        if (t.matches("[data-cg-completion-percent]")) {
          var pv = String(t.value != null ? t.value : "")
            .replace(/[^\d]/g, "")
            .slice(0, 3);
          if (pv !== "" && parseInt(pv, 10) > 100) {
            pv = "100";
          }
          if (t.value !== pv) {
            t.value = pv;
          }
          syncInlinePctBar(t.closest("[data-cg-row]"));
          tryPushPlaythroughsToHidden();
          return;
        }
        if (t.matches("[data-cg-end-date]")) {
          tryPushPlaythroughsToHidden();
        }
      });

      /* Capture phase; persisted rows POST to server, then remove from DOM. Draft rows are client-only. */
      function handlePlaythroughDeleteClick(e) {
        var btn =
          e.target && e.target.closest
            ? e.target.closest("[data-cg-remove], .cg-playthrough-panel-delete")
            : null;
        if (!btn || btn.disabled) {
          return;
        }
        if (!list.contains(btn)) {
          return;
        }
        var card = btn.closest("li.cgpd-card");
        if (!card || !list.contains(card)) {
          return;
        }
        var row = card.querySelector("[data-cg-row]");
        var n = row
          ? parseInt(
              String(row.getAttribute("data-session-count") || "0"),
              10,
            ) || 0
          : 0;
        var isDraft = card.getAttribute("data-cg-client-draft-row") === "true";
        var rawPtId = card.getAttribute("data-playthrough-id");
        var ptIdNum =
          rawPtId != null && String(rawPtId).trim() !== ""
            ? parseInt(String(rawPtId).trim(), 10)
            : NaN;
        var serverDelete = !isDraft && Number.isFinite(ptIdNum) && ptIdNum > 0;
        var deleteUrl = list.getAttribute("data-cg-pt-delete-url");
        var msg =
          n > 0
            ? "Delete this playthrough and all " +
              n +
              " play log(s) tied to it? This cannot be undone."
            : "Delete this playthrough? (No play logs were linked.)";
        if (!window.confirm(msg)) {
          return;
        }
        e.preventDefault();
        e.stopPropagation();

        function afterRemoveFromDom() {
          card.remove();
          closeAllInlineEdits();
          ensurePlaythroughStatusCoherent();
          syncPlaythroughEmptyState();
          syncPlaySummaryActivePlaythroughFromList();
          tryPushPlaythroughsToHidden();
        }

        if (!serverDelete) {
          afterRemoveFromDom();
          return;
        }
        if (!deleteUrl || String(deleteUrl).trim() === "") {
          window.alert("Could not delete playthrough (configuration error).");
          return;
        }
        btn.disabled = true;
        var body = new URLSearchParams();
        body.set("playthroughId", String(ptIdNum));
        fetch(String(deleteUrl).trim(), {
          method: "POST",
          credentials: "same-origin",
          headers: {
            "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
            Accept: "application/json",
          },
          body: body.toString(),
        })
          .then(function (res) {
            return res
              .json()
              .catch(function () {
                return { ok: false, error: "Unexpected response from server." };
              })
              .then(function (data) {
                return { res: res, data: data };
              });
          })
          .then(function (x) {
            btn.disabled = false;
            if (!x.res.ok) {
              window.alert(
                (x.data && x.data.error) ||
                  "Could not delete playthrough (" + x.res.status + ").",
              );
              return;
            }
            if (!x.data || x.data.ok !== true) {
              window.alert(
                (x.data && x.data.error) || "Could not delete playthrough.",
              );
              return;
            }
            afterRemoveFromDom();
          })
          .catch(function () {
            btn.disabled = false;
            window.alert("Could not delete playthrough (network error).");
          });
      }
      document.addEventListener("click", handlePlaythroughDeleteClick, true);

      function collectPlaythroughsJson(ev) {
        if (hidden.disabled) {
          return;
        }
        ensurePlaythroughStatusCoherent();
        var p = buildPlaythroughPayload(true);
        if (p.badTime) {
          if (ev) {
            ev.preventDefault();
          }
          window.alert(
            "Enter valid hours (0–999) and minutes (0–59) for playthrough time, or leave both blank.",
          );
          return;
        }
        if (p.badShortName) {
          if (ev) {
            ev.preventDefault();
          }
          window.alert("Enter a name for each playthrough.");
          return;
        }
        hidden.value = JSON.stringify({ items: p.items });
        /* Must sync playthroughs whenever this field is posted; submitter can be missing (Enter key) so wireColGamePersonalSubmitScopes may leave syncPlaythroughs false. */
        var spEl = document.getElementById("colGameHiddenSyncPlaythroughs");
        if (spEl) {
          spEl.value = "true";
        }
      }

      ensurePlaythroughStatusCoherent();
      tryPushPlaythroughsToHidden();
      form.addEventListener("submit", collectPlaythroughsJson, true);
      syncPlaythroughEmptyState();
      syncPlaySummaryActivePlaythroughFromList();

      document.addEventListener(
        "click",
        function (ev) {
          var editT =
            ev.target && ev.target.closest
              ? ev.target.closest("[data-cg-edit-playthrough]")
              : null;
          if (editT) {
            if (sessionBlocksColGameEdits()) {
              return;
            }
            ev.preventDefault();
            ev.stopPropagation();
            var rawId = editT.getAttribute("data-playthrough-id");
            if (rawId == null || String(rawId).trim() === "") {
              var draftCard = editT.closest("li.cgpd-card");
              if (
                draftCard &&
                draftCard.getAttribute("data-cg-client-draft-row") === "true"
              ) {
                document.dispatchEvent(
                  new CustomEvent("cg-open-history-with-focus", {
                    bubbles: true,
                    detail: { anchorCard: draftCard },
                  }),
                );
              }
              return;
            }
            document.dispatchEvent(
              new CustomEvent("cg-open-history-with-focus", {
                bubbles: true,
                detail: { playthroughId: String(rawId).trim() },
              }),
            );
            return;
          }
          var t =
            ev.target && ev.target.closest
              ? ev.target.closest("[data-cg-activate-playthrough]")
              : null;
          if (!t) {
            return;
          }
          if (sessionBlocksColGameEdits()) {
            return;
          }
          ev.preventDefault();
          var raw = t.getAttribute("data-cg-activate-playthrough");
          if (raw == null || String(raw).trim() === "") {
            return;
          }
          document.dispatchEvent(
            new CustomEvent("cg-open-history-with-focus", {
              bubbles: true,
              detail: { playthroughId: String(raw).trim() },
            }),
          );
          window.setTimeout(function () {
            var targetCard = list.querySelector(
              '[data-playthrough-id="' + String(raw).trim() + '"]',
            );
            if (!targetCard) {
              return;
            }
            list.querySelectorAll("[data-cg-row]").forEach(function (r) {
              var sel = r.querySelector("[data-cg-progress-status]");
              if (!sel) {
                return;
              }
              var card = r.closest("li.cgpd-card");
              sel.value = card === targetCard ? "ACTIVE" : "STOPPED";
              syncCgDropdownFieldFromHidden(sel);
            });
            ensurePlaythroughStatusCoherent();
            tryPushPlaythroughsToHidden();
            try {
              targetCard.scrollIntoView({
                block: "nearest",
                behavior: "smooth",
              });
            } catch (e2) {
              targetCard.scrollIntoView(true);
            }
          }, 120);
        },
        true,
      );

      function addClientPlaythroughRow() {
        if (!newRowTpl) {
          return;
        }
        var cap = maxPlaythroughsAllowed();
        if (rowCount() >= cap) {
          window.alert(
            "You can add at most " + cap + " playthroughs for a game.",
          );
          return;
        }
        var htmlRaw = newRowTpl.innerHTML;
        if (!htmlRaw || !String(htmlRaw).trim()) {
          return;
        }
        var suffix =
          "d" +
          String(Date.now()) +
          "_" +
          String(Math.floor(Math.random() * 1e6));
        var ord = String(list.querySelectorAll("li.cgpd-card").length + 1);
        var html = String(htmlRaw)
          .split("__DRAFT__")
          .join(suffix)
          .split("__ORD__")
          .join(ord);
        var wrap = document.createElement("div");
        wrap.innerHTML = html.trim();
        var li = wrap.firstElementChild;
        if (!li) {
          return;
        }
        list.appendChild(li);
        if (typeof window.cgWireCgDropdownField === "function") {
          li.querySelectorAll(".cg-dropdown-field").forEach(function (f) {
            window.cgWireCgDropdownField(f);
          });
        }
        if (typeof window.cgAttachPlaythroughAccordion === "function") {
          var det = li.querySelector(
            "details.cg-pt-accordion[data-cg-pt-accordion]",
          );
          if (det) {
            window.cgAttachPlaythroughAccordion(det);
          }
        }
        syncPlaythroughEmptyState();
        ensurePlaythroughStatusCoherent();
        tryPushPlaythroughsToHidden();
        document.dispatchEvent(
          new CustomEvent("cg-open-history-with-focus", {
            bubbles: true,
            detail: { anchorCard: li },
          }),
        );
      }
      function bindPlaythroughAddClick(btn) {
        if (!btn || !newRowTpl) {
          return;
        }
        btn.addEventListener("click", function (ev) {
          ev.preventDefault();
          if (btn.disabled) {
            return;
          }
          addClientPlaythroughRow();
        });
      }
      bindPlaythroughAddClick(addBtn);
      bindPlaythroughAddClick(addBtnEmpty);
    })();

    document.querySelectorAll(".js-why-playing-chip").forEach(function (chip) {
      chip.addEventListener("click", function () {
        var v = chip.getAttribute("data-why-playing");
        v = v === null ? "" : String(v);
        var hidden = document.getElementById("colGamePersonalWhyCsv");
        if (!hidden || v === "") {
          return;
        }
        var turningOn = !chip.classList.contains("is-active");
        if (turningOn) {
          if (colGameWhyActiveCount() >= MAX_PERSONAL_WHY) {
            return;
          }
          chip.classList.add("is-active");
        } else {
          chip.classList.remove("is-active");
        }
        colGameSyncWhyHidden();
        colGameRefreshWhyEditDescription();
      });
    });
    colGameRefreshWhyEditDescription();

    document.querySelectorAll(".js-why-read-chip").forEach(function (chip) {
      chip.addEventListener("click", function () {
        if (chip.classList.contains("is-active")) {
          return;
        }
        colGameSetActiveReadWhyChip(chip);
      });
    });
    colGameSetActiveReadWhyChip(null);

    var notesTa = document.getElementById("colGamePersonalNotes");
    var notesCnt = document.getElementById("colGamePersonalNotesCounter");
    function refreshNotesCounter() {
      refreshMaxlengthCounter(notesTa, notesCnt, 4000);
    }
    if (notesTa && notesCnt) {
      notesTa.addEventListener("input", refreshNotesCounter);
      refreshNotesCounter();
    }
    if (reviewTextarea && reviewCounter) {
      reviewTextarea.addEventListener("input", function () {
        hideRatingFormClientError();
        refreshReviewCounter();
      });
      refreshReviewCounter();
    }
    var resetReflectionFormFromSeed = function () {};

    (function wireReflectionTagsEditor() {
      var MAX_TAGS = 6;
      var MAX_LEN = 30;
      var section = document.querySelector(".cg-reflection-rating-stack");
      var hidden = document.getElementById("colGameReflectionTagsJson");
      var chips = document.getElementById("colGameReflectionTagsChips");
      var input = document.getElementById("colGameReflectionTagInput");
      var countEl = document.getElementById("colGameReflectionTagCount");
      if (!section || !hidden || !chips || !input) {
        return;
      }

      var tags = [];

      function normalizeClientTag(raw) {
        var s = String(raw || "")
          .trim()
          .toLowerCase();
        if (!s) {
          return "";
        }
        s = s.replace(/[\s_]+/g, "-");
        s = s.replace(/[^a-z0-9-]/g, "");
        s = s.replace(/-+/g, "-").replace(/^-+|-+$/g, "");
        if (s.length > MAX_LEN) {
          s = s.substring(0, MAX_LEN).replace(/-+$/g, "");
        }
        return s;
      }

      function formatTagForDisplay(slug) {
        if (!slug) {
          return "";
        }
        var s = String(slug).trim().toLowerCase();
        if (!s) {
          return "";
        }
        s = s
          .replace(/[\s_]+/g, "-")
          .replace(/-+/g, "-")
          .replace(/^-+|-+$/g, "");
        return s;
      }

      function hasSlug(slug) {
        return tags.indexOf(slug) >= 0;
      }

      function syncHidden() {
        try {
          hidden.value = JSON.stringify(tags);
        } catch (err) {
          hidden.value = "[]";
        }
      }

      function updateCount() {
        if (countEl) {
          countEl.textContent = String(tags.length) + " / " + String(MAX_TAGS);
        }
      }

      function syncSuggestionButtons() {
        section
          .querySelectorAll(".js-reflection-suggest-chip")
          .forEach(function (btn) {
            var slug = btn.getAttribute("data-reflection-tag");
            if (!slug) {
              return;
            }
            var on = hasSlug(slug);
            btn.setAttribute("aria-pressed", on ? "true" : "false");
            btn.disabled = !on && tags.length >= MAX_TAGS;
          });
      }

      function closeReflectionTagCategoryPanels() {
        section
          .querySelectorAll(".cg-reflection-tag-panel")
          .forEach(function (p) {
            p.hidden = true;
          });
        section
          .querySelectorAll(".js-reflection-tag-cat")
          .forEach(function (b) {
            b.setAttribute("aria-expanded", "false");
          });
      }

      function renderChips() {
        chips.innerHTML = "";
        tags.forEach(function (tag) {
          var chip = document.createElement("span");
          chip.className = "cg-reflection-tag-chip";
          var lab = document.createElement("span");
          lab.className = "cg-reflection-tag-chip-text";
          lab.textContent = formatTagForDisplay(tag);
          chip.appendChild(lab);
          var rm = document.createElement("button");
          rm.type = "button";
          rm.className = "cg-reflection-tag-chip-remove";
          rm.setAttribute(
            "aria-label",
            "Remove tag " + formatTagForDisplay(tag),
          );
          rm.innerHTML = '<i class="fa-solid fa-xmark" aria-hidden="true"></i>';
          (function (tagVal) {
            rm.addEventListener("click", function () {
              tags = tags.filter(function (x) {
                return x !== tagVal;
              });
              renderChips();
            });
          })(tag);
          chip.appendChild(rm);
          chips.appendChild(chip);
        });
        syncHidden();
        updateCount();
        syncSuggestionButtons();
      }

      function parseInitialFromHidden() {
        tags = [];
        try {
          var raw = String(hidden.value || "").trim();
          if (!raw) {
            raw = "[]";
          }
          var arr = JSON.parse(raw);
          if (Array.isArray(arr)) {
            arr.forEach(function (s) {
              if (typeof s !== "string") {
                return;
              }
              var n = normalizeClientTag(s);
              if (!n || tags.length >= MAX_TAGS) {
                return;
              }
              if (!hasSlug(n)) {
                tags.push(n);
              }
            });
          }
        } catch (err) {
          tags = [];
        }
        renderChips();
      }

      function addTagFromInput() {
        var n = normalizeClientTag(input.value);
        if (!n) {
          input.value = "";
          return;
        }
        if (tags.length >= MAX_TAGS) {
          return;
        }
        if (hasSlug(n)) {
          input.value = "";
          return;
        }
        tags.push(n);
        input.value = "";
        renderChips();
      }

      input.addEventListener("keydown", function (e) {
        if (e.key === "Enter") {
          e.preventDefault();
          addTagFromInput();
        }
      });

      section.addEventListener("click", function (e) {
        var catBtn =
          e.target && e.target.closest
            ? e.target.closest(".js-reflection-tag-cat")
            : null;
        if (catBtn && section.contains(catBtn)) {
          e.preventDefault();
          var panelId = catBtn.getAttribute("aria-controls");
          var panel = panelId ? document.getElementById(panelId) : null;
          if (!panel) {
            return;
          }
          if (catBtn.getAttribute("aria-expanded") === "true") {
            panel.hidden = true;
            catBtn.setAttribute("aria-expanded", "false");
            return;
          }
          section
            .querySelectorAll(".cg-reflection-tag-panel")
            .forEach(function (p) {
              p.hidden = p.id !== panel.id;
            });
          section
            .querySelectorAll(".js-reflection-tag-cat")
            .forEach(function (b) {
              var pid = b.getAttribute("aria-controls");
              b.setAttribute(
                "aria-expanded",
                pid === panel.id ? "true" : "false",
              );
            });
          return;
        }

        var btn =
          e.target && e.target.closest
            ? e.target.closest(".js-reflection-suggest-chip")
            : null;
        if (!btn || !section.contains(btn) || btn.disabled) {
          return;
        }
        e.preventDefault();
        var slug = btn.getAttribute("data-reflection-tag");
        if (!slug) {
          return;
        }
        var idx = tags.indexOf(slug);
        if (idx >= 0) {
          tags.splice(idx, 1);
        } else {
          if (tags.length >= MAX_TAGS) {
            return;
          }
          tags.push(slug);
        }
        renderChips();
      });

      resetReflectionFormFromSeed = function () {
        var j = section.getAttribute("data-seed-reflection-tags-json");
        if (j == null || j === "") {
          j = "[]";
        }
        hidden.value = j;
        closeReflectionTagCategoryPanels();
        parseInitialFromHidden();
      };

      parseInitialFromHidden();
    })();

    function resetReviewFormFromSeed() {
      var stack = document.querySelector(".cg-reflection-rating-stack");
      var rt = document.getElementById("colGamePersonalReview");
      if (stack && rt) {
        var body = stack.getAttribute("data-seed-review-text");
        rt.value = body != null ? body : "";
      }
      syncReviewHeadlineToHidden();
      if (ratingWrap && stack) {
        var rn = stack.getAttribute("data-seed-rating-num");
        ratingWrap.setAttribute(
          "data-initial-rating",
          rn != null && String(rn).trim() !== "" ? String(rn).trim() : "",
        );
      }
      initRatingFromDom();
      refreshReviewCounter();
      hideRatingFormClientError();
    }

    (function wireReflectionUnifiedEdit() {
      var stack = document.querySelector(".cg-reflection-rating-stack");
      var formEl = document.getElementById("colGameRatingForm");
      var reflectionRead = document.getElementById("colGameReflectionRead");
      var reflectionEdit = document.getElementById(
        "colGameReflectionEditSection",
      );
      var reflectionBtn = document.getElementById("colGameReflectionEditBtn");
      var reflectionCancel = document.getElementById(
        "colGameReflectionCancelBtn",
      );

      if (!stack || !formEl || !reflectionRead || !reflectionEdit) {
        return;
      }

      var editingReflection = false;

      function setReflectionEditingUi(on) {
        if (reflectionBtn) {
          reflectionBtn.hidden = !!on;
          reflectionBtn.setAttribute("aria-expanded", on ? "true" : "false");
        }
        var lab = reflectionBtn
          ? getOrCreateColGameEditingLabel(reflectionBtn)
          : null;
        if (lab) {
          lab.hidden = !on;
        }
      }

      function syncLayout() {
        formEl.hidden = !editingReflection;
        reflectionRead.hidden = editingReflection;
        reflectionEdit.hidden = !editingReflection;
        setReflectionEditingUi(editingReflection);
      }

      function enterReflectionEdit() {
        dispatchCloseAllGamePageEdits();
        editingReflection = true;
        resetReflectionFormFromSeed();
        resetReviewFormFromSeed();
        syncLayout();
      }

      function leaveReflectionEdit() {
        editingReflection = false;
        resetReflectionFormFromSeed();
        resetReviewFormFromSeed();
        syncLayout();
      }

      document.addEventListener(CG_EVT_CLOSE_ALL_GAME_EDITS, function () {
        leaveReflectionEdit();
      });

      if (reflectionBtn) {
        reflectionBtn.addEventListener("click", function () {
          if (sessionBlocksColGameEdits()) {
            return;
          }
          enterReflectionEdit();
        });
      }
      if (reflectionCancel) {
        reflectionCancel.addEventListener("click", leaveReflectionEdit);
      }

      if (stack.getAttribute("data-reopen-reflection-edit") === "true") {
        enterReflectionEdit();
      } else {
        syncLayout();
      }
    })();

    var colGameRatingForm = document.getElementById("colGameRatingForm");
    if (colGameRatingForm) {
      colGameRatingForm.addEventListener("submit", function (e) {
        syncReviewHeadlineToHidden();
        var hasBody = reviewTextarea && reviewTextarea.value.trim() !== "";
        var errEl = document.getElementById("colGameRatingFormError");
        if (hasBody && !hasRatingSelected()) {
          e.preventDefault();
          if (errEl) {
            errEl.textContent =
              "Choose a star rating (1–10) to save your review. You can save a rating on its own without review text.";
            errEl.hidden = false;
          }
          var firstHit =
            ratingWrap && ratingWrap.querySelector(".cg-star-slot__hit");
          if (firstHit) {
            firstHit.focus();
          }
        }
      });
    }

    (function wireColGameFormDropdowns() {
      function setPanelHidden(panel, hidden) {
        if (!panel) {
          return;
        }
        panel.setAttribute("aria-hidden", hidden ? "true" : "false");
      }

      function closeAllColGameDropdownsExcept(exceptRoot) {
        document.querySelectorAll(".cg-form-dropdown").forEach(function (root) {
          if (root === exceptRoot) {
            return;
          }
          var t = root.querySelector(".dropdown-trigger");
          var p = root.querySelector(".dropdown-panel");
          if (!t || !p || p.getAttribute("aria-hidden") === "true") {
            return;
          }
          setPanelHidden(p, true);
          t.setAttribute("aria-expanded", "false");
        });
      }

      function wireOneCgDropdownField(field) {
        /* Log play modal wires its own playthrough dropdown (dynamic options); a second handler here toggles open/closed twice. */
        if (!field || field.getAttribute("data-cg-dd-wired") === "1") {
          return;
        }
        if (field.closest && field.closest("#logPlayModal")) {
          return;
        }
        var hidden = field.querySelector('input[type="hidden"]');
        var root = field.querySelector(".cg-form-dropdown");
        if (!hidden || !root) {
          return;
        }
        var trigger = root.querySelector(".dropdown-trigger");
        var panel = root.querySelector(".dropdown-panel");
        var labelEl = root.querySelector(".dropdown-label");
        if (!trigger || !panel || !labelEl) {
          return;
        }
        field.setAttribute("data-cg-dd-wired", "1");

        setPanelHidden(panel, true);
        trigger.setAttribute("aria-expanded", "false");

        trigger.addEventListener("click", function (e) {
          e.stopPropagation();
          var expanded = trigger.getAttribute("aria-expanded") === "true";
          if (expanded) {
            setPanelHidden(panel, true);
            trigger.setAttribute("aria-expanded", "false");
          } else {
            closeAllColGameDropdownsExcept(root);
            setPanelHidden(panel, false);
            trigger.setAttribute("aria-expanded", "true");
          }
        });

        panel.querySelectorAll(".dropdown-option").forEach(function (opt) {
          opt.addEventListener("click", function (e) {
            e.stopPropagation();
            var v = opt.getAttribute("data-value");
            if (v === null) {
              return;
            }
            hidden.value = v;
            labelEl.textContent = opt.textContent.trim();
            panel.querySelectorAll(".dropdown-option").forEach(function (o) {
              o.classList.toggle("is-selected", o === opt);
            });
            setPanelHidden(panel, true);
            trigger.setAttribute("aria-expanded", "false");
            try {
              hidden.dispatchEvent(new Event("input", { bubbles: true }));
              hidden.dispatchEvent(new Event("change", { bubbles: true }));
            } catch (err) {
              /* no-op */
            }
          });
        });
      }

      document
        .querySelectorAll(".cg-dropdown-field")
        .forEach(wireOneCgDropdownField);
      window.cgWireCgDropdownField = wireOneCgDropdownField;

      document.addEventListener("click", function (e) {
        document.querySelectorAll(".cg-form-dropdown").forEach(function (root) {
          var t = root.querySelector(".dropdown-trigger");
          var p = root.querySelector(".dropdown-panel");
          if (!t || !p || p.getAttribute("aria-hidden") === "true") {
            return;
          }
          if (t.contains(e.target) || p.contains(e.target)) {
            return;
          }
          setPanelHidden(p, true);
          t.setAttribute("aria-expanded", "false");
        });
      });

      document.addEventListener("keydown", function (e) {
        if (e.key !== "Escape") {
          return;
        }
        document.querySelectorAll(".cg-form-dropdown").forEach(function (root) {
          var t = root.querySelector(".dropdown-trigger");
          var p = root.querySelector(".dropdown-panel");
          if (!t || !p || p.getAttribute("aria-hidden") === "true") {
            return;
          }
          setPanelHidden(p, true);
          t.setAttribute("aria-expanded", "false");
        });
      });
    })();

    (function wireFinishDatePromptOnTerminalStatus() {
      var statusHidden = document.getElementById("colGamePersonalStatus");
      var finishedInput = document.getElementById("colGamePersonalFinished");
      var promptEl = document.getElementById("colGameFinishedDatePrompt");
      var promptLead = document.getElementById(
        "colGameFinishedDatePromptTitle",
      );
      var promptYes = document.getElementById("colGameFinishedDatePromptYes");
      var promptNo = document.getElementById("colGameFinishedDatePromptNo");
      var statusTrigger = document.getElementById(
        "colGamePersonalStatusTrigger",
      );
      if (
        !statusHidden ||
        !finishedInput ||
        !promptEl ||
        !promptLead ||
        !promptYes ||
        !promptNo
      ) {
        return;
      }
      var statusWrap = document.getElementById("colGameFinishedDatePromptAnchor");

      function localIsoDate() {
        var d = new Date();
        function p2(n) {
          return n < 10 ? "0" + n : String(n);
        }
        return (
          d.getFullYear() + "-" + p2(d.getMonth() + 1) + "-" + p2(d.getDate())
        );
      }
      function finishedDateIsEmpty() {
        var v = finishedInput.value;
        return v === null || String(v).trim() === "";
      }
      function promptIsOpen() {
        return promptEl.getAttribute("aria-hidden") !== "true";
      }
      function showPrompt() {
        promptLead.textContent = finishedDateIsEmpty()
          ? "Set the finished date to today?"
          : "Update the finished date to today?";
        promptEl.setAttribute("aria-hidden", "false");
        try {
          promptYes.focus();
        } catch (err) {
          /* no-op */
        }
      }
      function hidePrompt() {
        if (!promptIsOpen()) {
          return;
        }
        promptEl.setAttribute("aria-hidden", "true");
        if (statusTrigger && typeof statusTrigger.focus === "function") {
          try {
            statusTrigger.focus();
          } catch (errFocus) {
            /* no-op */
          }
        } else if (statusHidden && typeof statusHidden.focus === "function") {
          try {
            statusHidden.focus();
          } catch (errFocus2) {
            /* no-op */
          }
        }
      }

      statusHidden.setAttribute("data-prev-status", statusHidden.value || "");
      statusHidden.addEventListener("change", function () {
        var prev = statusHidden.getAttribute("data-prev-status");
        var next = statusHidden.value || "";
        var terminal = next === "FINISHED" || next === "DROPPED";
        if (terminal && next !== prev) {
          showPrompt();
        }
        statusHidden.setAttribute("data-prev-status", next);
      });

      promptYes.addEventListener("click", function (e) {
        e.stopPropagation();
        finishedInput.value = localIsoDate();
        hidePrompt();
      });
      promptNo.addEventListener("click", function (e) {
        e.stopPropagation();
        hidePrompt();
      });

      document.addEventListener("keydown", function (e) {
        if (e.key !== "Escape") {
          return;
        }
        if (!promptIsOpen()) {
          return;
        }
        hidePrompt();
      });
      document.addEventListener("click", function (e) {
        if (!promptIsOpen()) {
          return;
        }
        if (promptEl.contains(e.target)) {
          return;
        }
        if (statusWrap && statusWrap.contains(e.target)) {
          return;
        }
        hidePrompt();
      });
    })();

    (function wireCollectionPlaythroughPlogs() {
      var PLOG_PAGE_SIZE = 10;
      var bootstrapMap = {};
      var bootEl = document.getElementById("cgPlaythroughLogsBootstrapJson");
      if (bootEl) {
        var bt = (bootEl.textContent || "").trim();
        if (bt) {
          try {
            bootstrapMap = JSON.parse(bt);
          } catch (e0) {
            bootstrapMap = {};
          }
        }
      }

      function setPlayLogRowExpanded(btn, expanded) {
        if (!btn) {
          return;
        }
        var li = btn.closest(".cglog-li");
        if (!li) {
          return;
        }
        btn.setAttribute("aria-expanded", expanded ? "true" : "false");
        var det = li.querySelector(".cglog-detail");
        if (det) {
          det.hidden = !expanded;
        }
        li.classList.toggle("is-expanded", expanded);
      }

      function appendMetaSection(aside, labelText, valueNode) {
        var sec = document.createElement("div");
        sec.className = "cglog-detail-meta-section";
        if (labelText) {
          var lab = document.createElement("div");
          lab.className = "cglog-detail-meta-label";
          lab.textContent = labelText;
          sec.appendChild(lab);
        } else {
          sec.classList.add("cglog-detail-meta-section--value-only");
        }
        var val = document.createElement("div");
        val.className = "cglog-detail-meta-value";
        val.appendChild(valueNode);
        sec.appendChild(val);
        aside.appendChild(sec);
      }

      function nonEmptyTrim(s) {
        if (s == null) {
          return "";
        }
        var t = String(s).trim();
        return t || "";
      }

      function appendNoteProseBody(container, rawText) {
        var normalized = String(rawText)
          .trim()
          .replace(/\r\n/g, "\n")
          .replace(/\r/g, "\n");
        if (!normalized) {
          return;
        }
        var body = document.createElement("div");
        body.className = "cglog-note-body";
        /* Single block: CSS pre-wrap keeps newlines without flex/margin between <p>s. */
        var p = document.createElement("p");
        p.className = "cglog-note-p";
        p.textContent = normalized;
        body.appendChild(p);
        container.appendChild(body);
      }

      /** Session notes in the row (main history + playthrough header preview / full when expanded). */
      function appendPlayLogThoughtsBlock(host, noteRaw, noteContainsSpoilers) {
        var noteTrim = String(noteRaw != null ? noteRaw : "").trim();
        var section = document.createElement("section");
        section.className = "cglog-note-section";
        section.setAttribute("aria-label", "Session notes");

        if (noteTrim && noteContainsSpoilers) {
          var spoil = document.createElement("div");
          spoil.className = "cg-playlog-spoiler cg-playlog-spoiler--in-notes";
          var innerS = document.createElement("div");
          innerS.className = "cg-playlog-spoiler-inner";
          var contentWrap = document.createElement("div");
          contentWrap.className = "cg-playlog-spoiler-content-wrap";
          appendNoteProseBody(contentWrap, noteTrim);
          var overlay = document.createElement("div");
          overlay.className = "cg-playlog-spoiler-overlay";
          overlay.setAttribute("aria-hidden", "false");
          var reveal = document.createElement("button");
          reveal.type = "button";
          reveal.className =
            "cg-playlog-spoiler-toggle js-playlog-spoiler-reveal";
          reveal.setAttribute("aria-expanded", "false");
          reveal.textContent = "Show spoiler content";
          overlay.appendChild(reveal);
          innerS.appendChild(contentWrap);
          innerS.appendChild(overlay);
          spoil.appendChild(innerS);
          var hideBtn = document.createElement("button");
          hideBtn.type = "button";
          hideBtn.className = "cg-playlog-spoiler-hide js-playlog-spoiler-hide";
          hideBtn.setAttribute("aria-hidden", "true");
          hideBtn.hidden = true;
          hideBtn.textContent = "Hide spoiler";
          spoil.appendChild(hideBtn);
          section.appendChild(spoil);
          host.appendChild(section);
          return;
        }
        if (noteTrim) {
          appendNoteProseBody(section, noteTrim);
          host.appendChild(section);
          return;
        }
        var emptyN = document.createElement("p");
        emptyN.className = "cglog-note-empty";
        emptyN.textContent = "No session notes";
        section.appendChild(emptyN);
        host.appendChild(section);
      }

      function sessionExperienceParts(row) {
        var label = nonEmptyTrim(row.sessionExperienceLabel);
        if (!label) {
          return null;
        }
        var code = nonEmptyTrim(row.sessionExperienceCode) || "OKAY";
        return {
          label: label,
          code: code,
        };
      }

      /** Title-case each word for "Feeling: Fine", "Feeling: Burnt out" → Burnt Out */
      function formatFeelingLabel(raw) {
        var s = String(raw != null ? raw : "").trim();
        if (!s) {
          return "";
        }
        return s
          .split(/\s+/)
          .filter(Boolean)
          .map(function (w) {
            return w.charAt(0).toUpperCase() + w.slice(1).toLowerCase();
          })
          .join(" ");
      }

      function setPlogNoteToggleContent(btn, expanded) {
        btn.textContent = expanded ? "Show less" : "Show more";
      }

      function createPlayLogEditControls(row, gb) {
        var editBtn = document.createElement("button");
        editBtn.type = "button";
        editBtn.className = "btn btn-sm btn-ghost";
        editBtn.title = "Edit play session";
        editBtn.innerHTML =
          '<i class="fa-solid fa-pen-to-square" aria-hidden="true"></i><span>Edit</span>';
        editBtn.addEventListener("click", function (ev) {
          ev.stopPropagation();
          var roots = editBtn.closest("[data-api-id]");
          var aid = roots && roots.getAttribute("data-api-id");
          var tEl = document.querySelector(".cg-title");
          var gTitle = tEl ? tEl.textContent.trim() : "";
          if (typeof window.openLogPlayModalForEdit === "function" && aid) {
            window.openLogPlayModalForEdit({
              apiId: aid,
              title: gTitle,
              row: row,
            });
          }
        });

        var delForm = document.createElement("form");
        delForm.method = "post";
        delForm.action = gb + "/play-log/" + String(row.id) + "/delete";
        delForm.className = "cglog-delete-form";
        delForm.setAttribute("aria-label", "Remove this play log");
        var delBtn = document.createElement("button");
        delBtn.type = "submit";
        delBtn.className = "btn btn-sm btn-ghost cg-btn-danger";
        delBtn.setAttribute("aria-label", "Delete play log");
        delBtn.title = "Delete play log";
        delBtn.innerHTML =
          '<i class="fa-solid fa-trash-can" aria-hidden="true"></i><span>Delete</span>';
        delForm.appendChild(delBtn);
        delForm.addEventListener("submit", function (ev) {
          if (!window.confirm("Remove this play log? This cannot be undone.")) {
            ev.preventDefault();
          }
          ev.stopPropagation();
        });
        delForm.addEventListener("click", function (ev) {
          ev.stopPropagation();
        });
        return { editBtn: editBtn, delForm: delForm };
      }

      function buildPlogKebabMenu(editBtn, delForm) {
        var det = document.createElement("details");
        det.className = "cg-plog-kebab";
        var sum = document.createElement("summary");
        sum.className = "cg-plog-kebab-sum";
        sum.innerHTML =
          '<i class="fa-solid fa-ellipsis" aria-hidden="true"></i>';
        sum.setAttribute("aria-label", "Play log actions");
        sum.addEventListener("click", function (e) {
          e.stopPropagation();
        });
        det.addEventListener("toggle", function () {
          if (!det.open) {
            return;
          }
          var ul = det.closest("ul");
          if (!ul) {
            return;
          }
          ul.querySelectorAll("details.cg-plog-kebab").forEach(function (o) {
            if (o !== det && o.open) {
              o.open = false;
            }
          });
        });
        det.addEventListener("click", function (e) {
          e.stopPropagation();
        });
        var panel = document.createElement("div");
        panel.className = "cg-plog-kebab-panel";
        editBtn.className =
          "btn btn-ghost cg-plog-kebab-action cg-plog-kebab-action--edit";
        delForm.className =
          "cglog-delete-form cg-plog-kebab-delete-form";
        editBtn.innerHTML =
          '<i class="fa-solid fa-pen-to-square" aria-hidden="true"></i>';
        editBtn.setAttribute("aria-label", "Edit play session");
        var delBtnKebab = delForm.querySelector('button[type="submit"]');
        if (delBtnKebab) {
          delBtnKebab.className =
            "btn btn-ghost cg-plog-kebab-action cg-plog-kebab-action--delete";
          delBtnKebab.innerHTML =
            '<i class="fa-solid fa-trash-can" aria-hidden="true"></i>';
          delBtnKebab.setAttribute("aria-label", "Delete play log");
        }
        panel.appendChild(editBtn);
        panel.appendChild(delForm);
        det.appendChild(sum);
        det.appendChild(panel);
        return det;
      }

      /** Collapsed-row copy for playthrough play logs on the game page. */
      function appendPlogSessionRowNote(
        container,
        noteRaw,
        noteContainsSpoilers,
        hasMood,
      ) {
        var noteTrim = String(noteRaw != null ? noteRaw : "").trim();
        if (!noteTrim) {
          var empty = document.createElement("p");
          empty.className =
            "cg-play-log-note cg-plog-note-preview cg-plog-note-preview--empty";
          empty.textContent = hasMood
            ? "No thoughts added"
            : "No feeling or thoughts recorded";
          container.appendChild(empty);
          return;
        }
        if (noteContainsSpoilers) {
          appendPlayLogThoughtsBlock(container, noteRaw, true);
          return;
        }

        /* Two-line preview + Show more when note is long enough to usually exceed 2 lines */
        var PLOG_NOTE_PREVIEW_MAX = 96;
        if (noteTrim.length > PLOG_NOTE_PREVIEW_MAX) {
          var wrap = document.createElement("div");
          wrap.className = "cg-plog-note-expandable";
          /* One prose tree: clamp via CSS when collapsed so expand is the same text (no ellipsis remnant). */
          var prose = document.createElement("div");
          prose.className =
            "cg-plog-note-prose cg-plog-note-prose--collapsed";
          appendNoteProseBody(prose, noteTrim);

          var toggleBtn = document.createElement("button");
          toggleBtn.type = "button";
          toggleBtn.className = "cg-plog-note-toggle cg-plog-note-toggle--more";
          toggleBtn.setAttribute("aria-expanded", "false");
          setPlogNoteToggleContent(toggleBtn, false);

          toggleBtn.addEventListener("click", function (e) {
            e.stopPropagation();
            var open = wrap.classList.toggle("is-expanded");
            prose.classList.toggle("cg-plog-note-prose--collapsed", !open);
            setPlogNoteToggleContent(toggleBtn, open);
            toggleBtn.classList.toggle("cg-plog-note-toggle--less", open);
            toggleBtn.classList.toggle("cg-plog-note-toggle--more", !open);
            toggleBtn.setAttribute("aria-expanded", open ? "true" : "false");
          });

          wrap.appendChild(prose);
          wrap.appendChild(toggleBtn);
          container.appendChild(wrap);
          return;
        }

        var fullOnly = document.createElement("div");
        fullOnly.className = "cg-plog-note-full";
        appendNoteProseBody(fullOnly, noteTrim);
        container.appendChild(fullOnly);
      }

      function buildLogRow(row, _accordionUl, gb, sessionRowPt) {
        var isPtSession = sessionRowPt === true;
        var li = document.createElement("li");
        li.className = "cglog-li";

        var shell = null;
        var rowHost;
        var detailId = "cg-plog-detail-" + String(row.id);

        if (isPtSession) {
          shell = document.createElement("div");
          shell.className = "cg-plog-row-shell";
          rowHost = document.createElement("div");
          rowHost.className = "cglog-row cglog-row--pt cglog-row--plog-home";
        } else {
          rowHost = document.createElement("button");
          rowHost.type = "button";
          rowHost.className = "cglog-row";
          rowHost.setAttribute("aria-expanded", "false");
          rowHost.setAttribute("aria-controls", detailId);
          rowHost.setAttribute(
            "aria-label",
            "Play session " +
              (row.fullPlayedAtLabel || "") +
              ", show full details",
          );
        }

        var whenWrap = document.createElement("div");
        whenWrap.className = "cglog-when cg-play-log-date-wrap";
        var timeEl = document.createElement("time");
        timeEl.className = "cg-play-log-date";
        timeEl.setAttribute("datetime", row.playedAtIso || "");
        var daySpan = document.createElement("span");
        daySpan.className = "cg-play-log-day";
        daySpan.textContent =
          row.dayOfMonth != null ? String(row.dayOfMonth) : "—";
        var monthSpan = document.createElement("span");
        monthSpan.className = "cg-play-log-month";
        var ms = row.monthShort != null ? String(row.monthShort).trim() : "";
        monthSpan.textContent = ms ? ms.slice(0, 3).toUpperCase() : "";
        timeEl.appendChild(daySpan);
        timeEl.appendChild(monthSpan);
        whenWrap.appendChild(timeEl);

        var noteRaw = row.note != null ? String(row.note) : "";

        function progressChipOnlyStartedOrFinished(label) {
          if (!label) {
            return null;
          }
          var n = String(label).trim().toLowerCase();
          if (n === "continuing") {
            return null;
          }
          if (n === "started" || n === "finished") {
            return String(label).trim();
          }
          return null;
        }

        if (isPtSession) {
          whenWrap.classList.add("cg-plog-row-when");
          var mood = sessionExperienceParts(row);
          var hasMood = !!mood;
          var durLblPt =
            row.durationLabel != null ? String(row.durationLabel).trim() : "";
          var hasDurPt =
            row.durationMinutes != null &&
            row.durationMinutes > 0 &&
            durLblPt;

          var mainCol = document.createElement("div");
          mainCol.className = "cg-plog-row-main";

          var ctrlsKebab = createPlayLogEditControls(row, gb);
          var kebab = buildPlogKebabMenu(ctrlsKebab.editBtn, ctrlsKebab.delForm);
          var kebabWrap = document.createElement("div");
          kebabWrap.className = "cg-plog-kebab-wrap";
          kebabWrap.appendChild(kebab);

          var headRow = document.createElement("div");
          headRow.className = "cg-plog-row-head";

          var headStart = document.createElement("div");
          headStart.className = "cg-plog-row-head-start";

          if (row.sessionAwaitingEnd) {
            var pend = document.createElement("span");
            pend.className = "cg-plog-pending-pill";
            pend.textContent = "End time pending";
            headStart.appendChild(pend);
          } else if (mood) {
            var feelingBadge = document.createElement("span");
            var moodCode = String(mood.code || "OKAY")
              .trim()
              .toLowerCase()
              .replace(/_/g, "-");
            feelingBadge.className =
              "cg-plog-feeling-badge cg-plog-feeling-badge--" + moodCode;
            feelingBadge.textContent = formatFeelingLabel(mood.label);
            headStart.appendChild(feelingBadge);
          }

          if (hasDurPt) {
            var durHead = document.createElement("span");
            durHead.className = "cg-plog-head-stat cg-plog-head-stat--duration";
            durHead.setAttribute("title", durLblPt);
            durHead.setAttribute(
              "aria-label",
              "Time played " + durLblPt,
            );
            var clockIc = document.createElement("i");
            clockIc.className = "fa-regular fa-clock";
            clockIc.setAttribute("aria-hidden", "true");
            durHead.appendChild(clockIc);
            durHead.appendChild(document.createTextNode(" " + durLblPt));
            headStart.appendChild(durHead);
          }

          var progCodeRaw =
            row.sessionProgressCode != null
              ? String(row.sessionProgressCode).trim().toUpperCase()
              : "";
          var progDisp = row.sessionProgressLabel != null
            ? String(row.sessionProgressLabel).trim()
            : "";
          var progIsContinuing =
            progCodeRaw === "CONTINUING" ||
            progDisp.toLowerCase() === "continuing";
          if (progDisp && !progIsContinuing) {
            var progHead = document.createElement("span");
            progHead.className = "cg-plog-head-stat cg-plog-head-stat--progress";
            var progIc = document.createElement("i");
            progIc.className = "fa-solid fa-arrow-trend-up";
            progIc.setAttribute("aria-hidden", "true");
            progHead.appendChild(progIc);
            progHead.appendChild(document.createTextNode(" " + progDisp));
            headStart.appendChild(progHead);
          }

          headRow.appendChild(headStart);
          headRow.appendChild(kebabWrap);
          mainCol.appendChild(headRow);

          var noteBody = document.createElement("div");
          noteBody.className = "cg-plog-row-body";
          appendPlogSessionRowNote(
            noteBody,
            noteRaw,
            row.noteContainsSpoilers,
            hasMood,
          );

          var thoughtsWrap = document.createElement("div");
          thoughtsWrap.className = "cg-plog-row-thoughts";
          thoughtsWrap.appendChild(noteBody);
          mainCol.appendChild(thoughtsWrap);

          var asideCol = document.createElement("div");
          asideCol.className = "cg-plog-row-aside";
          var dateCard = document.createElement("div");
          dateCard.className = "cg-plog-date-card";
          dateCard.appendChild(whenWrap);
          asideCol.appendChild(dateCard);

          var gridWrap = document.createElement("div");
          gridWrap.className = "cg-plog-row-grid";
          gridWrap.appendChild(asideCol);
          gridWrap.appendChild(mainCol);

          rowHost.appendChild(gridWrap);
          shell.appendChild(rowHost);
        } else {
          var mainStack = document.createElement("div");
          mainStack.className = "cglog-main-stack";
          var prog = progressChipOnlyStartedOrFinished(row.sessionProgressLabel);
          var thoughtsUnified = document.createElement("div");
          thoughtsUnified.className = "cglog-thoughts-unified";
          appendPlayLogThoughtsBlock(
            thoughtsUnified,
            noteRaw,
            row.noteContainsSpoilers,
          );
          var rowHead = document.createElement("div");
          rowHead.className = "cglog-row-head";
          var body = document.createElement("div");
          body.className = "cglog-body cglog-body--chips-only";
          var chips = document.createElement("div");
          chips.className = "cglog-chips";
          var hasChip = false;
          if (row.sessionAwaitingEnd) {
            var cOpen = document.createElement("span");
            cOpen.className = "cglog-chip cglog-chip--open";
            cOpen.textContent = "End time pending";
            chips.appendChild(cOpen);
            hasChip = true;
          }
          if (
            row.durationMinutes != null &&
            row.durationMinutes > 0 &&
            row.durationLabel
          ) {
            var c4 = document.createElement("span");
            c4.className = "cglog-chip cglog-chip--duration";
            c4.textContent = row.durationLabel;
            chips.appendChild(c4);
            hasChip = true;
          }
          if (hasChip) {
            body.appendChild(chips);
          }
          var chev = document.createElement("span");
          chev.className = "cglog-chevron";
          chev.setAttribute("aria-hidden", "true");
          chev.innerHTML = '<i class="fa-solid fa-chevron-right"></i>';
          var tail = document.createElement("div");
          tail.className = "cglog-tail cg-play-log-meta";
          if (prog) {
            var cProg = document.createElement("span");
            var pLow = prog.toLowerCase();
            cProg.className =
              "cglog-chip cglog-chip--progress cglog-chip--row-end " +
              (pLow === "started"
                ? "cglog-chip--started"
                : "cglog-chip--finished");
            cProg.textContent = prog;
            tail.appendChild(cProg);
          }
          tail.appendChild(chev);
          rowHead.appendChild(body);
          rowHead.appendChild(tail);
          mainStack.appendChild(rowHead);
          mainStack.appendChild(thoughtsUnified);
          rowHost.appendChild(whenWrap);
          rowHost.appendChild(mainStack);
        }

        if (!isPtSession) {
          var detail = document.createElement("div");
          detail.className = "cglog-detail";
          detail.id = detailId;
          detail.hidden = true;
          detail.setAttribute("role", "region");
          detail.setAttribute("aria-label", "Full play log");

          var bodyGrid = document.createElement("div");
          bodyGrid.className = "cglog-detail-body";

          var aside = document.createElement("aside");
          aside.className = "cglog-detail-aside";
          aside.setAttribute("aria-label", "Session details");
          var metaCount = 0;

          var moodLabel = nonEmptyTrim(row.sessionExperienceLabel);
          if (moodLabel) {
            var moodSpan = document.createElement("span");
            moodSpan.textContent = moodLabel;
            appendMetaSection(aside, "", moodSpan);
            metaCount += 1;
          }

          var durationOnRow =
            row.durationMinutes != null &&
            row.durationMinutes > 0 &&
            row.durationLabel;
          if (durationOnRow) {
            var durInline = document.createElement("span");
            durInline.className = "cglog-detail-duration-inline";
            durInline.setAttribute(
              "aria-label",
              "Time played " + (row.durationLabel || ""),
            );
            var clockIcD = document.createElement("i");
            clockIcD.className = "fa-regular fa-clock";
            clockIcD.setAttribute("aria-hidden", "true");
            var timeSpan = document.createElement("span");
            timeSpan.textContent = row.durationLabel || "";
            durInline.appendChild(clockIcD);
            durInline.appendChild(document.createTextNode(" "));
            durInline.appendChild(timeSpan);
            appendMetaSection(aside, "", durInline);
            metaCount += 1;
          }

          if (metaCount > 0) {
            bodyGrid.appendChild(aside);
          }

          var footer = document.createElement("footer");
          footer.className = "cglog-detail-footer";

          var actionCtrls = createPlayLogEditControls(row, gb);
          var editBtn = actionCtrls.editBtn;
          var delForm = actionCtrls.delForm;

          var actions = document.createElement("div");
          actions.className = "cglog-detail-actions";
          actions.appendChild(editBtn);
          actions.appendChild(delForm);

          footer.appendChild(actions);

          if (bodyGrid.childNodes.length) {
            detail.appendChild(bodyGrid);
          } else {
            detail.classList.add("cglog-detail--solo-footer");
          }
          detail.appendChild(footer);

          rowHost.addEventListener("click", function () {
            var expanded = rowHost.getAttribute("aria-expanded") === "true";
            document
              .querySelectorAll(".cg-pt-plog-list .cglog-li.is-expanded")
              .forEach(function (openLi) {
                if (openLi !== li) {
                  setPlayLogRowExpanded(
                    openLi.querySelector(".cglog-row"),
                    false,
                  );
                }
              });
            setPlayLogRowExpanded(rowHost, !expanded);
          });

          li.appendChild(rowHost);
          li.appendChild(detail);
          return li;
        }

        if (isPtSession && shell) {
          li.appendChild(shell);
        } else {
          li.appendChild(rowHost);
        }
        return li;
      }

      function wireOneRoot(root) {
        if (!root || root.getAttribute("data-cg-pt-plog-wired") === "1") {
          return;
        }
        var playthroughId = root.getAttribute("data-playthrough-id");
        var playLogsUrl = root.getAttribute("data-play-logs-url");
        var gameBase = root.getAttribute("data-collection-game-base");
        if (!playthroughId || !playLogsUrl || !gameBase) {
          return;
        }
        var loadingEl = root.querySelector(".cg-pt-plog-loading");
        var errEl = root.querySelector(".cg-pt-plog-err");
        var listEl = root.querySelector(".cg-pt-plog-list");
        var emptyEl = root.querySelector(".cg-pt-plog-empty");
        var moreWrap = root.querySelector(".cg-pt-plog-more");
        var moreBtn = root.querySelector(".cg-pt-plog-more-btn");
        if (!listEl) {
          return;
        }

        var page = 0;
        var loading = false;

        function setStatus(kind, msg) {
          if (loadingEl) {
            loadingEl.hidden = kind !== "loading";
          }
          if (errEl) {
            if (kind === "error") {
              errEl.hidden = false;
              errEl.textContent = msg || "Could not load play logs.";
            } else {
              errEl.hidden = true;
              errEl.textContent = "";
            }
          }
          if (emptyEl) {
            emptyEl.hidden = kind !== "empty";
          }
          listEl.hidden = kind !== "list";
          if (moreWrap && kind === "loading") {
            moreWrap.hidden = true;
          }
          root.setAttribute("aria-busy", kind === "loading" ? "true" : "false");
        }

        function showLoadMoreError(message) {
          if (errEl) {
            errEl.hidden = false;
            errEl.textContent = message || "Could not load more play logs.";
          }
        }

        function renderRows(rows, append) {
          if (!append) {
            listEl.innerHTML = "";
          }
          rows.forEach(function (row) {
            listEl.appendChild(buildLogRow(row, listEl, gameBase, true));
          });
        }

        function finishList(totalFetched, append, hasMore) {
          if (totalFetched === 0 && !append) {
            setStatus("empty");
            if (moreWrap) {
              moreWrap.hidden = true;
            }
            return;
          }
          if (totalFetched === 0 && append) {
            setStatus("list");
            if (moreWrap) {
              moreWrap.hidden = true;
            }
            if (moreBtn) {
              moreBtn.disabled = false;
            }
            return;
          }
          setStatus("list");
          if (moreWrap && moreBtn) {
            moreWrap.hidden = !hasMore;
            moreBtn.disabled = false;
          }
        }

        function loadPage(nextPage, append) {
          if (loading) {
            return;
          }
          loading = true;
          if (!append) {
            setStatus("loading");
          } else if (moreBtn) {
            moreBtn.disabled = true;
          }
          var url =
            playLogsUrl +
            "?page=" +
            String(nextPage) +
            "&size=" +
            String(PLOG_PAGE_SIZE) +
            "&playthroughId=" +
            encodeURIComponent(playthroughId);
          fetch(url, {
            credentials: "same-origin",
            headers: { Accept: "application/json" },
          })
            .then(function (res) {
              if (!res.ok) {
                throw new Error("Bad response");
              }
              return res.json();
            })
            .then(function (data) {
              loading = false;
              var rows = data && Array.isArray(data.items) ? data.items : [];
              var hasMore = !!(data && data.hasMore);
              page = nextPage;
              renderRows(rows, append);
              finishList(rows.length, append, hasMore);
            })
            .catch(function () {
              loading = false;
              if (append) {
                if (moreBtn) {
                  moreBtn.disabled = false;
                }
                showLoadMoreError("Could not load more play logs.");
              } else {
                setStatus("error", "Could not load play logs.");
              }
            });
        }

        var boot =
          bootstrapMap[playthroughId] || bootstrapMap[String(playthroughId)];
        if (boot && boot.items != null) {
          if (loadingEl) {
            loadingEl.hidden = true;
          }
          if (boot.items.length === 0) {
            finishList(0, false, false);
          } else {
            renderRows(boot.items, false);
            finishList(boot.items.length, false, !!boot.hasMore);
          }
          page = 0;
        } else {
          loadPage(0, false);
        }

        if (moreBtn) {
          moreBtn.addEventListener("click", function () {
            loadPage(page + 1, true);
          });
        }

        root.setAttribute("data-cg-pt-plog-wired", "1");
      }

      function wirePlogInsidePlaythroughAccordion(detailsEl) {
        var root = detailsEl.querySelector(".js-cg-pt-plog-root");
        if (!root) {
          return;
        }
        wireOneRoot(root);
      }

      function attachPlaythroughAccordionBehavior(det) {
        if (!det || det.getAttribute("data-cg-pt-accordion-behavior") === "1") {
          return;
        }
        det.setAttribute("data-cg-pt-accordion-behavior", "1");
        var sum = det.querySelector(".cg-pt-accordion__summary");
        if (sum) {
          /* Don’t toggle closed from the header while this run is already expanded — switch runs via another row. */
          sum.addEventListener("click", function (e) {
            if (det.open) {
              e.preventDefault();
            }
          });
          sum.addEventListener("keydown", function (e) {
            if (det.open && (e.key === "Enter" || e.key === " ")) {
              e.preventDefault();
            }
          });
        }
        det.addEventListener("toggle", function () {
          if (!det.open) {
            var hasOtherOpen = false;
            document
              .querySelectorAll("details.cg-pt-accordion[data-cg-pt-accordion]")
              .forEach(function (o) {
                if (o !== det && o.open) {
                  hasOtherOpen = true;
                }
              });
            if (!hasOtherOpen) {
              det.open = true;
            }
            return;
          }
          wirePlogInsidePlaythroughAccordion(det);
          document
            .querySelectorAll("details.cg-pt-accordion[data-cg-pt-accordion]")
            .forEach(function (other) {
              if (other !== det) {
                other.open = false;
              }
            });
          var summaryEl = det.querySelector(".cg-pt-accordion__summary");
          if (summaryEl) {
            window.requestAnimationFrame(function () {
              window.requestAnimationFrame(function () {
                try {
                  summaryEl.scrollIntoView({
                    block: "start",
                    behavior: "smooth",
                    inline: "nearest",
                  });
                } catch (e1) {
                  try {
                    summaryEl.scrollIntoView(true);
                  } catch (e2) {
                    /* no-op */
                  }
                }
              });
            });
          }
        });
      }

      document
        .querySelectorAll("details.cg-pt-accordion[data-cg-pt-accordion]")
        .forEach(attachPlaythroughAccordionBehavior);
      window.cgAttachPlaythroughAccordion = attachPlaythroughAccordionBehavior;

      document
        .querySelectorAll("details.cg-pt-accordion[open]")
        .forEach(function (det) {
          wirePlogInsidePlaythroughAccordion(det);
        });

      document
        .querySelectorAll(".js-cg-pt-accordion-stop")
        .forEach(function (el) {
          el.addEventListener("click", function (ev) {
            ev.stopPropagation();
          });
        });

      document.addEventListener("keydown", function (e) {
        if (e.key !== "Escape") {
          return;
        }
        document
          .querySelectorAll('.cglog-row[aria-expanded="true"]')
          .forEach(function (btn) {
            setPlayLogRowExpanded(btn, false);
          });
      });
    })();
  });
})();
