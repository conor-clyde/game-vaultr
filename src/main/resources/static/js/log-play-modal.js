(function () {
  "use strict";

  var MAX_DURATION_MINUTES = 60 * 24 * 7;

  var state = {
    mode: "game",
    gamesUrl: "",
    editLogId: null,
    logAwaitingEnd: false,
    editHasStoredSessionStart: false,
    baselineDisplayMinutes: null,
    modalGameTitle: "",
    editSavedDurationMinutes: null,
    editSavedPlaythroughId: null,
    lastPlaythroughItems: null,
    modalVariant: "create",
    endSessionStartEpochMs: null,
    endSessionReferenceMinutes: null,
    endSessionStartUnlocked: false,
  };

  var endSessionTicker = null;

  function $(id) {
    return document.getElementById(id);
  }

  function closeLogPlayModal() {
    var m = $("logPlayModal");
    if (!m) return;
    state.modalGameTitle = "";
    var sub = $("logPlayModalSubtitle");
    if (sub) {
      sub.textContent = "";
    }
    m.classList.remove("is-open");
    m.classList.remove("log-play-modal--edit");
    m.classList.remove("log-play-modal--end");
    m.setAttribute("aria-hidden", "true");
    if (endSessionTicker) {
      clearInterval(endSessionTicker);
      endSessionTicker = null;
    }
    document.body.style.overflow = "";
    var search = $("logPlayGameSearch");
    if (search) search.value = "";
    document
      .querySelectorAll("#logPlayModal .js-log-play-submit")
      .forEach(function (btn) {
        btn.disabled = false;
        btn.removeAttribute("aria-busy");
      });
    if (
      lastActiveBeforeModal &&
      typeof lastActiveBeforeModal.focus === "function"
    ) {
      try {
        lastActiveBeforeModal.focus();
      } catch (err) {
        /* no-op */
      }
    }
    lastActiveBeforeModal = null;
    clearEditMode();
    applyLogPlayDetailsUi();
  }

  function openLogPlayModal() {
    var m = $("logPlayModal");
    if (!m) return;
    m.classList.add("is-open");
    m.setAttribute("aria-hidden", "false");
    document.body.style.overflow = "hidden";
  }

  function collectionBaseFromModal() {
    var modal = $("logPlayModal");
    if (!modal) return "";
    var gamesUrl = modal.getAttribute("data-log-games-url") || "";
    return gamesUrl.replace(/\/log-play\/games\/?$/, "");
  }

  function appendCsrfHiddenInput(form) {
    if (!form) {
      return;
    }
    var tokenMeta = document.querySelector('meta[name="_csrf"]');
    var paramMeta = document.querySelector('meta[name="_csrf_parameter"]');
    var token = tokenMeta ? tokenMeta.getAttribute("content") : "";
    var param = paramMeta ? paramMeta.getAttribute("content") : "";
    if (!token || !param) {
      // Fallback for pages where CSRF meta tags are missing:
      // reuse the token Spring already injected into any regular POST form.
      var fallback = Array.from(
        document.querySelectorAll('input[type="hidden"][name]'),
      ).find(function (input) {
        var name = String(input.name || "");
        var value = String(input.value || "");
        return /csrf/i.test(name) && value !== "";
      });
      if (fallback) {
        token = String(fallback.value || "");
        param = String(fallback.name || "");
      }
    }
    if (!token || !param) {
      return;
    }
    var csrfInput = document.createElement("input");
    csrfInput.type = "hidden";
    csrfInput.name = param;
    csrfInput.value = token;
    form.appendChild(csrfInput);
  }

  function readPlaythroughCountHintFromButton(btn) {
    if (!btn || !btn.getAttribute) {
      return null;
    }
    var raw = String(
      btn.getAttribute("data-log-playthrough-count") || "",
    ).trim();
    if (raw === "") {
      return null;
    }
    var n = parseInt(raw, 10);
    if (isNaN(n) || n < 0) {
      return null;
    }
    return n;
  }

  function coalescePlaythroughCountHintFromButton(btn, fromBtn) {
    var h = fromBtn;
    if (h != null && h > 0) {
      return h;
    }
    if (!btn || !btn.getAttribute) {
      return fromBtn;
    }
    var pref = String(
      btn.getAttribute("data-log-preferred-playthrough-id") || "",
    ).trim();
    if (pref !== "" && !isNaN(Number(pref))) {
      return 1;
    }
    return fromBtn;
  }

  function setPlaythroughDropdownPanelHidden(hidden) {
    var panel = $("logPlayPlaythroughPanel");
    var trigger = $("logPlayPlaythroughTrigger");
    if (panel) {
      panel.setAttribute("aria-hidden", hidden ? "true" : "false");
    }
    if (trigger) {
      trigger.setAttribute("aria-expanded", hidden ? "false" : "true");
    }
  }

  function applyPlaythroughOptionSelection(optBtn) {
    var panel = $("logPlayPlaythroughPanel");
    var hidden = $("logPlayPlaythroughValue");
    var labelEl = $("logPlayPlaythroughLabel");
    if (!optBtn || !panel || !hidden || !labelEl) {
      return;
    }
    var v = optBtn.getAttribute("data-value");
    if (v === null) {
      v = "";
    }
    hidden.value = v;
    var labelText = optBtn.textContent.trim();
    labelEl.textContent = labelText;
    panel.querySelectorAll(".dropdown-option").forEach(function (o) {
      o.classList.toggle("is-selected", o === optBtn);
    });
    setPlaythroughDropdownPanelHidden(true);
    var triggerPt = $("logPlayPlaythroughTrigger");
    if (triggerPt) {
      triggerPt.setAttribute(
        "aria-label",
        "Playthrough: " + labelText + ". Open to change.",
      );
    }
    try {
      hidden.dispatchEvent(new Event("change", { bubbles: true }));
    } catch (err) {
      /* no-op */
    }
  }

  function wireLogPlayPlaythroughDropdown() {
    var root = $("logPlayPlaythroughDropdown");
    if (!root || root.getAttribute("data-log-play-wired") === "1") {
      return;
    }
    root.setAttribute("data-log-play-wired", "1");
    var trigger = $("logPlayPlaythroughTrigger");
    var panel = $("logPlayPlaythroughPanel");
    if (!trigger || !panel) {
      return;
    }

    trigger.addEventListener("click", function (e) {
      e.stopPropagation();
      if (trigger.disabled) {
        return;
      }
      var expanded = trigger.getAttribute("aria-expanded") === "true";
      setPlaythroughDropdownPanelHidden(expanded);
    });

    panel.addEventListener("click", function (e) {
      var opt =
        e.target && e.target.closest
          ? e.target.closest(".dropdown-option")
          : null;
      if (!opt || !panel.contains(opt)) {
        return;
      }
      e.stopPropagation();
      applyPlaythroughOptionSelection(opt);
    });

    document.addEventListener("click", function logPlayPtDropdownOutside(e) {
      if (!root.isConnected) {
        return;
      }
      var modal = $("logPlayModal");
      if (!modal || !modal.classList.contains("is-open")) {
        return;
      }
      if (panel.getAttribute("aria-hidden") === "true") {
        return;
      }
      if (trigger.contains(e.target) || panel.contains(e.target)) {
        return;
      }
      setPlaythroughDropdownPanelHidden(true);
    });

    document.addEventListener("keydown", function logPlayPtDropdownEsc(e) {
      if (e.key !== "Escape") {
        return;
      }
      var modal = $("logPlayModal");
      if (!modal || !modal.classList.contains("is-open")) {
        return;
      }
      if (panel.getAttribute("aria-hidden") === "true") {
        return;
      }
      setPlaythroughDropdownPanelHidden(true);
    });
  }

  function syncPlaythroughDropdownChevron(multipleChoices) {
    var chevron = $("logPlayPlaythroughChevron");
    if (chevron) {
      chevron.hidden = !multipleChoices;
    }
  }

  function clearPlaythroughPicker() {
    var wrap = $("logPlayPlaythroughPicker");
    var hidden = $("logPlayPlaythroughValue");
    var trigger = $("logPlayPlaythroughTrigger");
    var labelEl = $("logPlayPlaythroughLabel");
    var panel = $("logPlayPlaythroughPanel");
    var dropWrap = $("logPlayPlaythroughDropdownWrap");
    if (wrap) {
      wrap.hidden = true;
    }
    if (dropWrap) {
      dropWrap.hidden = false;
    }
    if (panel) {
      panel.innerHTML = "";
    }
    if (hidden) {
      hidden.value = "";
      hidden.removeAttribute("name");
    }
    if (labelEl) {
      labelEl.textContent = "No playthrough";
    }
    syncPlaythroughDropdownChevron(false);
    if (trigger) {
      trigger.disabled = true;
      trigger.classList.remove("is-log-playthrough-singleton");
      trigger.removeAttribute("aria-disabled");
      trigger.setAttribute("aria-expanded", "false");
      trigger.setAttribute("aria-label", "Playthrough");
    }
    if (panel) {
      panel.setAttribute("aria-hidden", "true");
    }
    state.lastPlaythroughItems = null;
  }

  function showNoPlaythroughSingletonPending() {
    var wrap = $("logPlayPlaythroughPicker");
    var hidden = $("logPlayPlaythroughValue");
    var trigger = $("logPlayPlaythroughTrigger");
    var labelEl = $("logPlayPlaythroughLabel");
    var panel = $("logPlayPlaythroughPanel");
    var dropWrap = $("logPlayPlaythroughDropdownWrap");
    if (!wrap || !hidden) {
      return;
    }
    state.lastPlaythroughItems = null;
    wrap.hidden = false;
    if (dropWrap) {
      dropWrap.hidden = false;
    }
    hidden.value = "";
    hidden.removeAttribute("name");
    if (panel) {
      panel.innerHTML = "";
      panel.setAttribute("aria-hidden", "true");
    }
    if (labelEl) {
      labelEl.textContent = "New playthrough";
    }
    syncPlaythroughDropdownChevron(false);
    if (trigger) {
      trigger.disabled = true;
      trigger.classList.add("is-log-playthrough-singleton");
      trigger.setAttribute("aria-expanded", "false");
      trigger.setAttribute("aria-label", "Playthrough: new playthrough");
    }
    try {
      hidden.dispatchEvent(new Event("change", { bubbles: true }));
    } catch (err) {
      /* no-op */
    }
  }

  function pickPreferredPlaythroughIndex(items, preferredPlaythroughId) {
    if (!items || items.length === 0) {
      return 0;
    }
    if (preferredPlaythroughId != null && preferredPlaythroughId !== "") {
      var target = Number(preferredPlaythroughId);
      if (!isNaN(target)) {
        for (var i = 0; i < items.length; i++) {
          if (Number(items[i].id) === target) {
            return i;
          }
        }
      }
    }
    for (var j = 0; j < items.length; j++) {
      if (items[j].active === true) {
        return j;
      }
    }
    return 0;
  }

  function showPlaythroughPickerWithPreferred(
    items,
    preferredPlaythroughId,
    fetchOk,
    playthroughCountHint,
  ) {
    var wrap = $("logPlayPlaythroughPicker");
    var hidden = $("logPlayPlaythroughValue");
    var trigger = $("logPlayPlaythroughTrigger");
    var labelEl = $("logPlayPlaythroughLabel");
    var panel = $("logPlayPlaythroughPanel");
    var dropWrap = $("logPlayPlaythroughDropdownWrap");
    if (!wrap || !hidden || !trigger || !panel) {
      return;
    }
    if (fetchOk === undefined) {
      fetchOk = true;
    }
    var hint = playthroughCountHint;
    if (
      hint === undefined ||
      hint === null ||
      (typeof hint === "number" && isNaN(hint))
    ) {
      hint = null;
    } else {
      hint = Math.max(0, Math.floor(Number(hint)));
    }
    if (!items || items.length === 0) {
      state.lastPlaythroughItems = null;
      if (state.editLogId != null) {
        clearPlaythroughPicker();
        return;
      }
      if (fetchOk) {
        if (hint != null && hint > 0) {
          clearPlaythroughPicker();
        } else {
          showNoPlaythroughSingletonPending();
        }
      } else {
        clearPlaythroughPicker();
      }
      return;
    }
    state.lastPlaythroughItems = items;
    if (dropWrap) {
      dropWrap.hidden = false;
    }
    wrap.hidden = false;
    hidden.setAttribute("name", "playthroughId");

    function appendOption(value, label) {
      var btn = document.createElement("button");
      btn.type = "button";
      btn.className = "dropdown-option";
      btn.setAttribute("data-value", value);
      btn.textContent = label;
      panel.appendChild(btn);
    }

    if (items.length === 1) {
      var lone = items[0];
      var loneLabel = lone.label != null ? String(lone.label) : "Playthrough";
      panel.innerHTML = "";
      hidden.value = String(lone.id);
      if (labelEl) {
        labelEl.textContent = loneLabel;
      }
      trigger.disabled = true;
      trigger.classList.add("is-log-playthrough-singleton");
      trigger.setAttribute("aria-expanded", "false");
      trigger.setAttribute("aria-label", "Playthrough: " + loneLabel + ".");
      syncPlaythroughDropdownChevron(false);
      setPlaythroughDropdownPanelHidden(true);
      if (panel) {
        panel.setAttribute("aria-hidden", "true");
      }
      try {
        hidden.dispatchEvent(new Event("change", { bubbles: true }));
      } catch (err) {
        /* no-op */
      }
      return;
    }

    trigger.disabled = false;
    trigger.classList.remove("is-log-playthrough-singleton");
    syncPlaythroughDropdownChevron(true);
    panel.innerHTML = "";
    items.forEach(function (it) {
      var label = it.label != null ? String(it.label) : "Playthrough";
      appendOption(String(it.id), label);
    });

    var idx = pickPreferredPlaythroughIndex(items, preferredPlaythroughId);
    var opts = panel.querySelectorAll(".dropdown-option");
    var chosen = opts[Math.min(Math.max(0, idx), opts.length - 1)];
    if (chosen) {
      applyPlaythroughOptionSelection(chosen);
    }
    setPlaythroughDropdownPanelHidden(true);
  }

  function normalizePlaythroughsForLogJson(data) {
    if (data == null) {
      return [];
    }
    if (Array.isArray(data)) {
      return data;
    }
    if (typeof data === "object") {
      if (Array.isArray(data.items)) {
        return data.items;
      }
      if (Array.isArray(data.playthroughs)) {
        return data.playthroughs;
      }
    }
    return [];
  }

  function fetchPlaythroughsForGame(apiId, bustCache) {
    var base = collectionBaseFromModal();
    var aid = apiId != null ? String(apiId).trim() : "";
    if (!base || !aid) {
      return Promise.resolve({ ok: false, items: [] });
    }
    var url = base + "/" + encodeURIComponent(aid) + "/playthroughs-for-log";
    if (bustCache) {
      url += (url.indexOf("?") === -1 ? "?" : "&") + "_=" + Date.now();
    }
    return fetch(url, { credentials: "same-origin" })
      .then(function (r) {
        if (!r.ok) {
          return { ok: false, items: [] };
        }
        return r.json().then(function (data) {
          return {
            ok: true,
            items: normalizePlaythroughsForLogJson(data),
          };
        });
      })
      .catch(function () {
        return { ok: false, items: [] };
      });
  }

  function loadAndApplyPlaythroughs(
    apiId,
    preferredPlaythroughId,
    playthroughCountHint,
  ) {
    var aid = apiId != null ? String(apiId).trim() : "";
    if (!aid) {
      clearPlaythroughPicker();
      return;
    }
    fetchPlaythroughsForGame(aid, false)
      .then(function (result) {
        var items = result.items || [];
        var fetchOk = result.ok === true;
        if (fetchOk && items.length === 0 && state.editLogId == null) {
          return fetchPlaythroughsForGame(aid, true).then(function (r2) {
            if (r2.ok === true && (r2.items || []).length > 0) {
              return r2;
            }
            return result;
          });
        }
        return result;
      })
      .then(function (result) {
        var items = result.items || [];
        var fetchOk = result.ok === true;
        showPlaythroughPickerWithPreferred(
          items,
          preferredPlaythroughId,
          fetchOk,
          playthroughCountHint,
        );
      });
  }

  function setFormAction(apiId) {
    var form = $("logPlayModalForm");
    if (!form || !apiId) return;
    var base = collectionBaseFromModal();
    form.action = base + "/" + encodeURIComponent(apiId) + "/play-log";
  }

  function setFormActionEdit(apiId, logId) {
    var form = $("logPlayModalForm");
    if (!form || !apiId || logId == null) return;
    var base = collectionBaseFromModal();
    form.action =
      base +
      "/" +
      encodeURIComponent(apiId) +
      "/play-log/" +
      encodeURIComponent(String(logId));
  }

  function syncLogPlaySubmitButtonLabels() {
    var main = $("logPlayModalSubmit");
    var editSubmit = $("logPlayModalSubmitEdit");
    if (state.modalVariant === "end-session") {
      if (main) main.textContent = "End session";
      if (editSubmit) editSubmit.textContent = "Save changes";
      return;
    }
    if (state.editLogId != null) {
      if (main) main.textContent = "Save changes";
      if (editSubmit) editSubmit.textContent = "Save changes";
    } else {
      if (main) {
        main.textContent = "Log session";
      }
      if (editSubmit) editSubmit.textContent = "Save changes";
    }
  }

  function syncSessionFlowButtonsUi() {
    var hidden = $("logPlaySessionFlowMode");
    if (hidden) {
      hidden.value = "just-played";
    }
  }

  function clearEditMode() {
    state.editLogId = null;
    state.logAwaitingEnd = false;
    state.editHasStoredSessionStart = false;
    state.modalVariant = "create";
    state.endSessionStartEpochMs = null;
    state.endSessionReferenceMinutes = null;
    state.endSessionStartUnlocked = false;
    state.editSavedDurationMinutes = null;
    state.editSavedPlaythroughId = null;
    var t = $("logPlayModalTitle");
    if (t) {
      t.textContent = "Log play";
    }
    syncLogPlaySubmitButtonLabels();
  }

  function isEditVariant() {
    return state.modalVariant === "edit-session";
  }

  function isEndSessionVariant() {
    return state.modalVariant === "end-session";
  }

  function syncEndSessionRecordedHint() {
    var readonly = $("logPlayTimeReadonly");
    if (!isEndSessionVariant() || state.endSessionStartEpochMs == null) {
      if (readonly) {
        readonly.hidden = true;
        readonly.textContent = "";
      }
      return;
    }
    var ref =
      typeof state.endSessionReferenceMinutes === "number" &&
      !isNaN(state.endSessionReferenceMinutes)
        ? Math.max(0, Math.floor(state.endSessionReferenceMinutes))
        : 0;
    var msg =
      ref < 1
        ? "End session unlocks after 1:00 on the timer below."
        : formatMinutesRecordedHint(ref) + " recorded";
    if (readonly) {
      readonly.textContent = msg;
      readonly.hidden = false;
    }
  }

  function applyLogPlayDetailsUi() {
    var modal = $("logPlayModal");
    var panel = $("logPlayDetailsPanel");
    if (!panel) {
      return;
    }
    var isEdit = isEditVariant();
    var isEnd = isEndSessionVariant();
    if (isEdit) {
      if (modal) {
        modal.classList.add("log-play-modal--edit");
        modal.classList.remove("log-play-modal--end");
        modal.classList.remove("log-play-modal--end-start-unlocked");
      }
      panel.hidden = false;
      document
        .querySelectorAll("#logPlayModal .log-play-end-optional-details")
        .forEach(function (el) {
          el.hidden = false;
        });
    } else {
      if (modal) {
        modal.classList.remove("log-play-modal--edit");
        modal.classList.toggle("log-play-modal--end", isEnd);
        modal.classList.toggle(
          "log-play-modal--end-start-unlocked",
          isEnd && state.endSessionStartUnlocked,
        );
      }
      panel.hidden = false;
      document
        .querySelectorAll("#logPlayModal .log-play-end-optional-details")
        .forEach(function (el) {
          el.hidden = false;
        });
    }
    var mainSubmit = $("logPlayModalSubmit");
    var editSubmit = $("logPlayModalSubmitEdit");
    if (mainSubmit && editSubmit) {
      if (isEdit) {
        mainSubmit.disabled = true;
        mainSubmit.setAttribute("tabindex", "-1");
        editSubmit.disabled = false;
        editSubmit.removeAttribute("tabindex");
      } else {
        mainSubmit.disabled = false;
        mainSubmit.removeAttribute("tabindex");
        editSubmit.disabled = true;
        editSubmit.setAttribute("tabindex", "-1");
      }
    }
    var expRow = $("logPlayExpRow");
    if (expRow) {
      expRow.hidden = isEnd;
    }
    var timeSimple = $("logPlayTimeSimpleBlock");
    if (timeSimple) {
      timeSimple.hidden = false;
    }
    var durationBlock = $("logPlayDurationModeBlock");
    if (durationBlock) {
      if (isEnd) {
        durationBlock.hidden = false;
      } else {
        var modeEl = $("logPlayTimeInputMode");
        var isRangeMode = modeEl && modeEl.value === "range";
        durationBlock.hidden = isRangeMode;
      }
    }
    /* Playthrough strip: same visibility rules as Log play (JS may show/hide picker by data). */
    setRangeStartFieldsReadonly(isEnd && !state.endSessionStartUnlocked);
    syncEndSessionRecordedHint();
    syncSessionFlowButtonsUi();
    refreshTimeFieldsUi();
  }

  function showPickStep(show) {
    var pick = $("logPlayPickStep");
    var formStep = $("logPlayFormStep");
    var sub = $("logPlayModalSubtitle");
    if (!pick || !formStep) return;
    if (show) {
      pick.hidden = false;
      formStep.hidden = true;
      var modalPick = $("logPlayModal");
      if (modalPick) {
        modalPick.classList.remove("log-play-modal--edit");
        modalPick.classList.remove("log-play-modal--end");
      }
      state.modalGameTitle = "";
      if (sub) {
        sub.textContent = "";
      }
    } else {
      pick.hidden = true;
      formStep.hidden = false;
    }
  }

  function syncLogPlayExpSelection(activeValue) {
    var hidden = $("logPlaySessionExperience");
    var v =
      activeValue === undefined || activeValue === null
        ? "OKAY"
        : String(activeValue);
    if (hidden) {
      hidden.value = v;
    }
    document
      .querySelectorAll("#logPlayModal .js-log-play-exp")
      .forEach(function (btn) {
        var ev = btn.getAttribute("data-exp");
        ev = ev === null ? "" : String(ev);
        btn.classList.toggle("is-active", ev === v);
      });
  }

  function pad2(n) {
    var s = String(n);
    return s.length === 1 ? "0" + s : s;
  }

  function localDateIsoFromEpochMs(ms) {
    var d = new Date(ms);
    return (
      d.getFullYear() + "-" + pad2(d.getMonth() + 1) + "-" + pad2(d.getDate())
    );
  }

  function localTimeHHMMEpochMs(ms) {
    var d = new Date(ms);
    return pad2(d.getHours()) + ":" + pad2(d.getMinutes());
  }

  function localTodayIso() {
    var d = new Date();
    return (
      d.getFullYear() + "-" + pad2(d.getMonth() + 1) + "-" + pad2(d.getDate())
    );
  }

  function yesterdayIso() {
    var d = new Date();
    d.setDate(d.getDate() - 1);
    return (
      d.getFullYear() + "-" + pad2(d.getMonth() + 1) + "-" + pad2(d.getDate())
    );
  }

  function dayOrdinal(n) {
    var v = Number(n);
    var mod100 = v % 100;
    if (mod100 >= 11 && mod100 <= 13) {
      return v + "th";
    }
    var mod10 = v % 10;
    if (mod10 === 1) return v + "st";
    if (mod10 === 2) return v + "nd";
    if (mod10 === 3) return v + "rd";
    return v + "th";
  }

  function formatMonthDayOrdinal(dateObj) {
    if (!(dateObj instanceof Date)) {
      return "";
    }
    var months = [
      "January",
      "February",
      "March",
      "April",
      "May",
      "June",
      "July",
      "August",
      "September",
      "October",
      "November",
      "December",
    ];
    return months[dateObj.getMonth()] + " " + dayOrdinal(dateObj.getDate());
  }

  function isStartedYesterdayChecked() {
    var cb = $("logPlayStartYesterday");
    return !!(cb && cb.checked);
  }

  function syncStartedYesterdayCheckboxFromDate() {
    var cb = $("logPlayStartYesterday");
    var sd = $("logPlaySessionStartDate");
    if (!cb || !sd) {
      return;
    }
    cb.checked = trimInput(sd) === yesterdayIso();
  }

  function parseIsoToDateAndTime(iso) {
    if (iso == null || String(iso).trim() === "") {
      return { date: "", time: "" };
    }
    var s = String(iso).trim();
    var m = s.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})/);
    if (!m) {
      return { date: "", time: "" };
    }
    return { date: m[1], time: m[2] };
  }

  function trimInput(el) {
    return el && el.value != null ? String(el.value).trim() : "";
  }

  function optimizeIgdbCoverUrl(url, sizePreset) {
    var raw = url != null ? String(url).trim() : "";
    if (!raw) {
      return "";
    }
    var preset = sizePreset || "t_cover_small";
    return raw.replace(/\/t_[^/]+\//, "/" + preset + "/");
  }

  function normalizeTimeForParse(t) {
    var s = String(t || "").trim();
    if (!s) {
      return "";
    }
    if (s.length === 5 && s.charAt(2) === ":") {
      return s + ":00";
    }
    return s;
  }

  function localDateTimeToMs(dateStr, timeStr) {
    var ds = String(dateStr || "").trim();
    var ns = normalizeTimeForParse(timeStr);
    if (!ds || !ns) {
      return NaN;
    }
    return Date.parse(ds + "T" + ns);
  }

  function timeStrToMinutesSinceMidnight(timeStr) {
    var ns = normalizeTimeForParse(timeStr);
    if (!ns) {
      return NaN;
    }
    var parts = ns.split(":");
    var h = parseInt(parts[0], 10);
    var mi = parseInt(parts[1] != null ? parts[1] : "0", 10);
    if (isNaN(h) || isNaN(mi)) {
      return NaN;
    }
    return h * 60 + mi;
  }

  /** ISO-like local time with seconds — use when posting session start so the server matches wall clock (HH:mm alone becomes :00 and reads as up to ~59s ago). */
  function localTimeHHMMSSForInput() {
    var d = new Date();
    return (
      pad2(d.getHours()) +
      ":" +
      pad2(d.getMinutes()) +
      ":" +
      pad2(d.getSeconds())
    );
  }

  function updateSessionDateBounds() {
    var minD = yesterdayIso();
    var maxD = localTodayIso();
    ["logPlaySessionStartDate", "logPlaySessionEndDate"].forEach(function (id) {
      var el = $(id);
      if (el && el.type !== "hidden") {
        el.min = minD;
        el.max = maxD;
      }
    });
  }

  function syncRangeHiddenDatesFromTimes() {
    var modeEl = $("logPlayTimeInputMode");
    if (!modeEl || modeEl.value !== "range") {
      return;
    }
    var sd = $("logPlaySessionStartDate");
    var st = $("logPlaySessionStartTime");
    var ed = $("logPlaySessionEndDate");
    var et = $("logPlaySessionEndTime");
    if (!sd || !st || !ed || !et) {
      return;
    }
    var stV = trimInput(st);
    var etV = trimInput(et);
    if (!stV) {
      if (!isStartedYesterdayChecked()) {
        sd.value = "";
      }
    } else if (!trimInput(sd)) {
      sd.value = isStartedYesterdayChecked() ? yesterdayIso() : localTodayIso();
    }
    if (!etV) {
      ed.value = "";
    } else if (!trimInput(ed)) {
      ed.value = localTodayIso();
    }
    syncStartedYesterdayCheckboxFromDate();
  }

  function setRangeStartFieldsReadonly(on) {
    var sd = $("logPlaySessionStartDate");
    var st = $("logPlaySessionStartTime");
    var cb = $("logPlayStartYesterday");
    if (sd) {
      sd.readOnly = !!on;
    }
    if (st) {
      st.readOnly = !!on;
    }
    if (cb) {
      cb.disabled = !!on;
    }
  }

  function setTimeInputMode(mode) {
    var isRange = mode === "range";
    var hidden = $("logPlayTimeInputMode");
    if (hidden) {
      hidden.value = isRange ? "range" : "hours";
    }
    var durBlock = $("logPlayDurationModeBlock");
    if (durBlock) {
      durBlock.hidden = isRange && !isEndSessionVariant();
    }
    /* Session start/end are hidden inputs only — never toggle #logPlayRangeBlock[hidden] or the browser omits them from POST. */
    var sd = $("logPlaySessionStartDate");
    var st = $("logPlaySessionStartTime");
    var ed = $("logPlaySessionEndDate");
    var et = $("logPlaySessionEndTime");
    var hEl = $("logPlayHoursPart");
    var mEl = $("logPlayMinutesPart");
    /* Hidden session range inputs must stay enabled so they POST in all modes. */
    if (sd) {
      sd.disabled = false;
    }
    if (st) {
      st.disabled = false;
    }
    if (ed) {
      ed.disabled = false;
    }
    if (et) {
      et.disabled = false;
    }
    if (hEl) {
      hEl.disabled = isRange && !isEndSessionVariant();
    }
    if (mEl) {
      mEl.disabled = isRange && !isEndSessionVariant();
    }
    updateSessionDateBounds();
    refreshTimeFieldsUi();
  }

  function ensureHoursTimeMode() {
    setTimeInputMode("hours");
  }

  function getModalGameTitle() {
    return state.modalGameTitle ? String(state.modalGameTitle).trim() : "";
  }

  function syncLogPlayModalHeaderSubtitle() {
    var el = $("logPlayModalSubtitle");
    if (!el) {
      return;
    }
    var t = getModalGameTitle();
    if (!t) {
      el.textContent = "";
      return;
    }
    el.textContent = t + " \u00b7 " + formatMonthDayOrdinal(new Date());
  }

  function applyLogPlaySessionSummary() {
    syncLogPlayModalHeaderSubtitle();
  }

  function setClientLocalTodayHidden() {
    var el = $("logPlayClientLocalToday");
    if (!el) return;
    el.value = localTodayIso();
  }

  function clearSessionRangeFields() {
    [
      "logPlaySessionStartDate",
      "logPlaySessionEndDate",
      "logPlaySessionStartTime",
      "logPlaySessionEndTime",
    ].forEach(function (rid) {
      var el = $(rid);
      if (el) {
        el.value = "";
      }
    });
  }

  function readNonNegativeInt(value, fallback) {
    var n = Number(value);
    if (!isFinite(n) || isNaN(n)) {
      return fallback;
    }
    return Math.max(0, Math.floor(n));
  }

  function readNonNegativeIntAttr(el, attrName, fallback) {
    if (!el || !el.getAttribute) {
      return fallback;
    }
    return readNonNegativeInt(el.getAttribute(attrName), fallback);
  }

  function parseDurationMinutesFromRangeFields() {
    var sd = trimInput($("logPlaySessionStartDate"));
    var st = trimInput($("logPlaySessionStartTime"));
    var ed = trimInput($("logPlaySessionEndDate"));
    var et = trimInput($("logPlaySessionEndTime"));
    var hasStart = sd !== "" && st !== "";
    var hasEnd = ed !== "" && et !== "";
    if (!hasStart || !hasEnd) {
      return { ok: true, empty: true, mins: 0 };
    }
    var t0 = localDateTimeToMs(sd, st);
    var t1 = localDateTimeToMs(ed, et);
    if (isNaN(t0) || isNaN(t1)) {
      return {
        ok: false,
        msg: "Could not read session start and end. Check the times.",
      };
    }
    if (t1 <= t0) {
      if (sd === ed && t1 < t0) {
        t1 += 86400000;
      } else {
        return { ok: false, msg: "Session end must be after start." };
      }
    }
    var mins = Math.round((t1 - t0) / 60000);
    if (mins > MAX_DURATION_MINUTES) {
      return { ok: false, msg: "Session length is too long (max one week)." };
    }
    if (mins === 0) {
      if (isEndSessionVariant()) {
        return { ok: false, msg: "Session must be at least 1 minute." };
      }
      return { ok: false, msg: "Session end must be after start." };
    }
    return { ok: true, empty: false, mins: mins };
  }

  function resolvePlayLogDuration() {
    var modeEl = $("logPlayTimeInputMode");
    if (modeEl && modeEl.value === "range") {
      return parseDurationMinutesFromRangeFields();
    }
    return parseDurationMinutesFromFields();
  }

  function formatMinutesParts(mins, mode, joinWith) {
    var m = Math.floor(Number(mins));
    if (!m || m < 0) {
      return mode === "title" ? "0 Mins" : "0 mins";
    }
    var h = Math.floor(m / 60);
    var mi = m % 60;
    var parts = [];
    if (h) {
      if (mode === "title") {
        parts.push(h === 1 ? "1 Hr" : h + " Hrs");
      } else {
        parts.push(h + (h === 1 ? " hr" : " hrs"));
      }
    }
    if (mi) {
      if (mode === "title") {
        parts.push(mi === 1 ? "1 Min" : mi + " Mins");
      } else {
        parts.push(mi + (mi === 1 ? " min" : " mins"));
      }
    }
    return parts.length ? parts.join(joinWith || ", ") : "0 mins";
  }

  function formatPlaytimeLabel(mins) {
    return formatMinutesParts(mins, "title", ", ");
  }

  function formatMinutesHumanParts(mins) {
    return formatMinutesParts(mins, "normal", ", ");
  }

  function formatMinutesRecordedHint(mins) {
    return formatMinutesParts(mins, "normal", " ");
  }

  function addQuickDurationMinutes(delta) {
    var d = Number(delta);
    if (!isFinite(d) || d <= 0) {
      return;
    }
    var modeEl = $("logPlayTimeInputMode");
    if (!isEndSessionVariant() && modeEl && modeEl.value !== "hours") {
      return;
    }
    var durH = $("logPlayHoursPart");
    var durM = $("logPlayMinutesPart");
    var h = parseInt(durH && durH.value ? String(durH.value).trim() : "0", 10);
    var m = parseInt(durM && durM.value ? String(durM.value).trim() : "0", 10);
    if (isNaN(h)) {
      h = 0;
    }
    if (isNaN(m)) {
      m = 0;
    }
    var total = h * 60 + m + Math.round(d);
    if (total < 0) {
      total = 0;
    }
    if (total > MAX_DURATION_MINUTES) {
      total = MAX_DURATION_MINUTES;
    }
    var nh = Math.floor(total / 60);
    var nm = total % 60;
    if (durH) {
      durH.value = nh > 0 ? String(nh) : "";
    }
    if (durM) {
      durM.value = nm > 0 ? String(nm) : nh > 0 ? "0" : "";
    }
    refreshTimeFieldsUi();
  }

  function refreshTimeFieldsUi() {
    syncRangeHiddenDatesFromTimes();
    updateLogPlayTotalPreview();
  }

  function resetForm() {
    clearEditMode();
    var form = $("logPlayModalForm");
    if (form) form.reset();
    var hiddenDur = $("logPlayDurationMinutesHidden");
    if (hiddenDur) hiddenDur.value = "";
    var cnt = $("logPlayNoteCount");
    if (cnt) cnt.textContent = "0";
    var errTime = $("logPlayTimeError");
    if (errTime) {
      errTime.hidden = true;
      errTime.textContent = "";
    }
    var durH = $("logPlayHoursPart");
    var durM = $("logPlayMinutesPart");
    if (durH) durH.value = "";
    if (durM) durM.value = "";
    var yCb = $("logPlayStartYesterday");
    if (yCb) {
      yCb.checked = false;
      yCb.disabled = false;
    }
    state.baselineDisplayMinutes = null;
    state.logAwaitingEnd = false;
    state.editHasStoredSessionStart = false;
    var dropWrap = $("logPlayPlaythroughDropdownWrap");
    if (dropWrap) dropWrap.hidden = false;
    syncLogPlayExpSelection("OKAY");
    ensureHoursTimeMode();
    clearSessionRangeFields();
    clearPlaythroughPicker();
    syncSessionFlowButtonsUi();
    syncLogPlaySubmitButtonLabels();
  }

  function applyLogRowToForm(row) {
    if (!row) return;
    var note = $("logPlayNote");
    if (note) {
      note.value = row.note != null ? String(row.note) : "";
    }
    var cnt = $("logPlayNoteCount");
    if (cnt) {
      cnt.textContent = String(note && note.value ? note.value.length : 0);
    }
    var sp = $("logPlaySpoiler");
    if (sp) {
      sp.checked = !!row.noteContainsSpoilers;
    }

    var durH = $("logPlayHoursPart");
    var durM = $("logPlayMinutesPart");
    if (durH) durH.value = "";
    if (durM) durM.value = "";
    clearSessionRangeFields();
    setRangeStartFieldsReadonly(false);

    var startIso =
      row.sessionStartedAtIso != null
        ? String(row.sessionStartedAtIso).trim()
        : "";
    state.editHasStoredSessionStart = startIso !== "";

    if (startIso) {
      var shouldUseRangeMode = isEndSessionVariant();
      setTimeInputMode(shouldUseRangeMode ? "range" : "hours");
      var startParts = parseIsoToDateAndTime(startIso);
      var sdEl = $("logPlaySessionStartDate");
      var stEl = $("logPlaySessionStartTime");
      if (sdEl) sdEl.value = startParts.date;
      if (stEl) stEl.value = startParts.time;
      var awaiting = row.sessionAwaitingEnd === true;
      var dm = row.durationMinutes;
      var edEl = $("logPlaySessionEndDate");
      var etEl = $("logPlaySessionEndTime");
      if (dm != null && dm > 0 && !awaiting) {
        var playedIso =
          row.playedAtIso != null ? String(row.playedAtIso).trim() : "";
        var endParts = parseIsoToDateAndTime(playedIso);
        if (edEl) edEl.value = endParts.date;
        if (etEl) etEl.value = endParts.time;
      } else {
        if (edEl) edEl.value = "";
        if (etEl) etEl.value = "";
      }
      if (!shouldUseRangeMode) {
        var hStart = Math.floor((dm || 0) / 60);
        var mStart = (dm || 0) % 60;
        if (dm != null && dm > 0) {
          if (durH) durH.value = hStart > 0 ? String(hStart) : "";
          if (durM)
            durM.value = mStart > 0 ? String(mStart) : hStart > 0 ? "0" : "";
        }
      }
      setRangeStartFieldsReadonly(shouldUseRangeMode && awaiting);
      updateSessionDateBounds();
      syncStartedYesterdayCheckboxFromDate();
    } else {
      ensureHoursTimeMode();
      var mins = row.durationMinutes;
      if (mins != null && mins > 0) {
        var h = Math.floor(mins / 60);
        var mi = mins % 60;
        if (durH) durH.value = h > 0 ? String(h) : "";
        if (durM) durM.value = mi > 0 ? String(mi) : h > 0 ? "0" : "";
      }
      var yCb = $("logPlayStartYesterday");
      if (yCb) {
        yCb.checked = false;
      }
    }

    var exp = (row.sessionExperienceCode || "OKAY").toString().toUpperCase();
    if (exp === "BAD") {
      exp = "BURNT_OUT";
    } else if (exp === "NEUTRAL") {
      exp = "OKAY";
    }
    syncLogPlayExpSelection(exp);

    if (row.id != null) {
      var dmRow = row.durationMinutes;
      state.editSavedDurationMinutes = dmRow != null && dmRow > 0 ? dmRow : 0;
      var pti = row.playthroughId;
      state.editSavedPlaythroughId =
        pti != null && pti !== undefined ? pti : null;
    } else {
      state.editSavedDurationMinutes = null;
      state.editSavedPlaythroughId = null;
    }
    refreshTimeFieldsUi();
  }

  function getBaselinePlaythroughMinutesForPreview() {
    if (!state.lastPlaythroughItems || !state.lastPlaythroughItems.length) {
      return null;
    }
    var hidden = $("logPlayPlaythroughValue");
    if (!hidden || hidden.getAttribute("name") !== "playthroughId") {
      return null;
    }
    var raw = String(hidden.value || "").trim();
    if (raw === "") {
      return null;
    }
    var id = Number(raw);
    if (isNaN(id)) {
      return null;
    }
    var it = null;
    for (var i = 0; i < state.lastPlaythroughItems.length; i++) {
      if (Number(state.lastPlaythroughItems[i].id) === id) {
        it = state.lastPlaythroughItems[i];
        break;
      }
    }
    if (!it) {
      return null;
    }
    var mins = it.manualPlayMinutes;
    var base = typeof mins === "number" && !isNaN(mins) ? mins : 0;
    if (
      state.editLogId != null &&
      state.editSavedPlaythroughId != null &&
      Number(state.editSavedPlaythroughId) === id
    ) {
      var sub =
        typeof state.editSavedDurationMinutes === "number" &&
        !isNaN(state.editSavedDurationMinutes)
          ? state.editSavedDurationMinutes
          : 0;
      base = Math.max(0, base - sub);
    }
    return base;
  }

  function updateLogPlayTotalPreview() {
    var side = $("logPlayTimeSide");
    var deltaEl = $("logPlayLibraryPlaytimeDelta");
    var deltaPtEl = $("logPlayPlaythroughPlaytimeDelta");
    var ptLine = $("logPlayPlaythroughPreviewLine");
    if (!side || !deltaEl) {
      return;
    }
    function clearPreview() {
      side.hidden = true;
      deltaEl.textContent = "";
      if (deltaPtEl) {
        deltaPtEl.textContent = "";
      }
      if (ptLine) {
        ptLine.hidden = true;
      }
    }
    var dur = resolvePlayLogDuration();
    var hasDur = dur.ok && !dur.empty && dur.mins > 0;
    var canPreviewRemoval =
      dur.ok &&
      dur.empty &&
      state.editLogId != null &&
      typeof state.editSavedDurationMinutes === "number" &&
      !isNaN(state.editSavedDurationMinutes) &&
      state.editSavedDurationMinutes > 0;
    if (!hasDur && !canPreviewRemoval) {
      clearPreview();
      return;
    }
    var nextDurationMins = hasDur ? dur.mins : 0;
    side.hidden = false;
    var base =
      typeof state.baselineDisplayMinutes === "number" &&
      !isNaN(state.baselineDisplayMinutes)
        ? state.baselineDisplayMinutes
        : 0;
    var totalMins = base + nextDurationMins;
    var toStr = formatMinutesHumanParts(totalMins);
    deltaEl.textContent = toStr;

    if (deltaPtEl) {
      var basePt = getBaselinePlaythroughMinutesForPreview();
      if (basePt === null) {
        deltaPtEl.textContent = "";
        if (ptLine) {
          ptLine.hidden = true;
        }
      } else {
        var totalPt = basePt + nextDurationMins;
        var toPt = formatMinutesHumanParts(totalPt);
        deltaPtEl.textContent = toPt;
        if (ptLine) {
          ptLine.hidden = false;
        }
      }
    }
  }

  function openLogPlayModalForEdit(opts) {
    var apiId = opts && opts.apiId ? String(opts.apiId).trim() : "";
    var title = opts && opts.title ? String(opts.title) : "";
    var row = opts && opts.row ? opts.row : null;
    var m = $("logPlayModal");
    if (!m || !apiId || !row || row.id == null) {
      return;
    }
    lastActiveBeforeModal = document.activeElement;
    state.gamesUrl = m.getAttribute("data-log-games-url") || "";
    state.mode = "game";
    var redirectEl = $("logPlayRedirectTo");
    if (redirectEl) {
      redirectEl.value = "detail";
    }
    setGameTitle(title);
    resetForm();
    state.modalVariant = "edit-session";
    state.endSessionStartUnlocked = false;
    setFormActionEdit(apiId, row.id);
    state.editLogId = row.id;
    applyLogRowToForm(row);
    setRangeStartFieldsReadonly(false);
    var mt = $("logPlayModalTitle");
    if (mt) {
      mt.textContent = "Edit play session";
    }
    state.logAwaitingEnd = row.sessionAwaitingEnd === true;
    state.baselineDisplayMinutes = null;
    syncLogPlaySubmitButtonLabels();
    showPickStep(false);
    applyLogPlayDetailsUi();
    var baseUrl = collectionBaseFromModal();
    var pollUrl =
      baseUrl +
      "/" +
      encodeURIComponent(apiId) +
      "/play-time-display?excludeLogId=" +
      encodeURIComponent(String(row.id));
    fetch(pollUrl, { credentials: "same-origin" })
      .then(function (r) {
        return r.ok ? r.json() : { displayMinutes: 0 };
      })
      .then(function (data) {
        state.baselineDisplayMinutes =
          typeof data.displayMinutes === "number" ? data.displayMinutes : 0;
        updateLogPlayTotalPreview();
      })
      .catch(function () {
        state.baselineDisplayMinutes = 0;
        updateLogPlayTotalPreview();
      });
    applyLogPlaySessionSummary();
    openLogPlayModal();
    var ptPrefEdit =
      row.playthroughId != null && row.playthroughId !== undefined
        ? row.playthroughId
        : null;
    loadAndApplyPlaythroughs(apiId, ptPrefEdit, null);
    focusFirstFormControl();
    updateLogPlayTotalPreview();
  }

  function openLogPlayModalForEndSession(opts) {
    var apiId = opts && opts.apiId ? String(opts.apiId).trim() : "";
    var title = opts && opts.title ? String(opts.title) : "";
    var row = opts && opts.row ? opts.row : null;
    var startEpochMs =
      opts && opts.startEpochMs != null ? Number(opts.startEpochMs) : null;
    var m = $("logPlayModal");
    if (!m || !apiId || !row || row.id == null) {
      return;
    }
    lastActiveBeforeModal = document.activeElement;
    state.gamesUrl = m.getAttribute("data-log-games-url") || "";
    state.mode = "game";
    state.modalVariant = "end-session";
    state.endSessionStartUnlocked = false;
    var redirectEl = $("logPlayRedirectTo");
    if (redirectEl) {
      redirectEl.value = "detail";
    }
    setGameTitle(title);
    resetForm();
    state.modalVariant = "end-session";
    setFormActionEdit(apiId, row.id);
    state.editLogId = row.id;
    applyLogRowToForm(row);
    var mt = $("logPlayModalTitle");
    if (mt) {
      mt.textContent = "End session";
    }
    /* End-session submits as range (start + chosen duration); elapsed at open is reference-only ("… recorded"). */
    setTimeInputMode("range");
    setRangeStartFieldsReadonly(true);
    syncRangeHiddenDatesFromTimes();
    refreshTimeFieldsUi();
    state.endSessionStartEpochMs = isFinite(startEpochMs) ? startEpochMs : null;
    if (state.endSessionStartEpochMs == null) {
      var parsed = parseIsoToDateAndTime(
        row.sessionStartedAtIso != null ? String(row.sessionStartedAtIso) : "",
      );
      if (parsed.date && parsed.time) {
        var guessed = localDateTimeToMs(parsed.date, parsed.time);
        if (!isNaN(guessed)) {
          state.endSessionStartEpochMs = guessed;
        }
      }
    }
    state.endSessionReferenceMinutes = 0;
    if (
      state.endSessionStartEpochMs != null &&
      isFinite(state.endSessionStartEpochMs)
    ) {
      var elapsedMin = Math.floor(
        (Date.now() - state.endSessionStartEpochMs) / 60000,
      );
      state.endSessionReferenceMinutes = elapsedMin > 0 ? elapsedMin : 0;
    }
    var durHPref = $("logPlayHoursPart");
    var durMPref = $("logPlayMinutesPart");
    var totalPref =
      typeof state.endSessionReferenceMinutes === "number" &&
      !isNaN(state.endSessionReferenceMinutes)
        ? state.endSessionReferenceMinutes
        : 0;
    var prefH = Math.floor(totalPref / 60);
    var prefMi = totalPref % 60;
    if (durHPref) {
      durHPref.value = prefH > 0 ? String(prefH) : "";
    }
    if (durMPref) {
      if (totalPref === 0) {
        durMPref.value = "0";
      } else {
        durMPref.value = prefMi > 0 ? String(prefMi) : prefH > 0 ? "0" : "";
      }
    }
    syncLogPlaySubmitButtonLabels();
    showPickStep(false);
    applyLogPlayDetailsUi();
    if (endSessionTicker) {
      clearInterval(endSessionTicker);
      endSessionTicker = null;
    }
    syncEndSessionRecordedHint();
    applyLogPlaySessionSummary();
    openLogPlayModal();
    var ptPref =
      row.playthroughId != null && row.playthroughId !== undefined
        ? row.playthroughId
        : null;
    loadAndApplyPlaythroughs(apiId, ptPref, null);
    var bm =
      opts &&
      typeof opts.baselineDisplayMinutes === "number" &&
      !isNaN(opts.baselineDisplayMinutes)
        ? opts.baselineDisplayMinutes
        : 0;
    state.baselineDisplayMinutes = bm;
    focusFirstFormControl();
    updateLogPlayTotalPreview();
  }

  function setGameTitle(title) {
    state.modalGameTitle = title != null ? String(title).trim() : "";
    syncLogPlayModalHeaderSubtitle();
  }

  function bindDurationDigitSanitize(el, opts) {
    opts = opts || {};
    var maxLen = opts.maxLen != null ? opts.maxLen : 3;
    var maxVal = opts.maxVal;
    if (!el) return;
    el.addEventListener("input", function () {
      var d = String(el.value).replace(/\D/g, "").slice(0, maxLen);
      if (maxVal != null && d.length > 0) {
        var n = parseInt(d, 10);
        if (!isNaN(n) && n > maxVal) {
          d = String(maxVal);
        }
      }
      if (el.value !== d) {
        el.value = d;
      }
    });
  }

  function parseDurationMinutesFromFields() {
    var hEl = $("logPlayHoursPart");
    var mEl = $("logPlayMinutesPart");
    var hRaw = hEl && hEl.value != null ? String(hEl.value).trim() : "";
    var mRaw = mEl && mEl.value != null ? String(mEl.value).trim() : "";
    if (hRaw === "" && mRaw === "") {
      return { ok: true, empty: true, mins: 0 };
    }
    var h = hRaw === "" ? 0 : parseInt(hRaw, 10);
    var mi = mRaw === "" ? 0 : parseInt(mRaw, 10);
    if (isNaN(h) || h < 0 || h > 168) {
      return { ok: false, msg: "Hours must be between 0 and 168." };
    }
    if (isNaN(mi) || mi < 0 || mi > 59) {
      return { ok: false, msg: "Minutes must be between 0 and 59." };
    }
    var total = h * 60 + mi;
    if (total > MAX_DURATION_MINUTES) {
      return { ok: false, msg: "Session length is too long (max one week)." };
    }
    if (total === 0) {
      return { ok: true, empty: true, mins: 0 };
    }
    return { ok: true, empty: false, mins: total };
  }

  function validateRangeModeForSubmit() {
    syncRangeHiddenDatesFromTimes();
    var sd = trimInput($("logPlaySessionStartDate"));
    var st = trimInput($("logPlaySessionStartTime"));
    var ed = trimInput($("logPlaySessionEndDate"));
    var et = trimInput($("logPlaySessionEndTime"));
    var hasStart = sd !== "" && st !== "";
    var hasEnd = ed !== "" && et !== "";
    var partialStart = (sd !== "" || st !== "") && !hasStart;
    var partialEnd = (ed !== "" || et !== "") && !hasEnd;
    if (partialStart || partialEnd) {
      return {
        ok: false,
        msg: "Finish entering start and end time, or leave end blank for start only.",
      };
    }
    var any = sd !== "" || st !== "" || ed !== "" || et !== "";
    if (!any) {
      return {
        ok: false,
        msg: "Enter session start and end, or start only to save and add the end later.",
      };
    }
    if (hasStart && hasEnd) {
      return parseDurationMinutesFromRangeFields();
    }
    if (hasStart && !hasEnd) {
      var t0 = localDateTimeToMs(sd, st);
      if (isNaN(t0)) {
        return {
          ok: false,
          msg: "Could not save that session start. Check the start time.",
        };
      }
      return { ok: true };
    }
    if (hasEnd && !hasStart) {
      if (!state.editLogId || !state.editHasStoredSessionStart) {
        return { ok: false, msg: "Enter session start time before the end." };
      }
      var t1 = localDateTimeToMs(ed, et);
      if (isNaN(t1)) {
        return {
          ok: false,
          msg: "Could not save that session time. Check the end time (after start).",
        };
      }
      return { ok: true };
    }
    return {
      ok: false,
      msg: "Enter session start and end, or start only to save and add the end later.",
    };
  }

  var gamesCache = null;
  var gamesFetchPromise = null;
  var lastActiveBeforeModal = null;

  function fetchGames(url) {
    if (gamesCache) {
      return Promise.resolve(gamesCache);
    }
    if (gamesFetchPromise) {
      return gamesFetchPromise;
    }
    gamesFetchPromise = fetch(url, { credentials: "same-origin" })
      .then(function (r) {
        if (!r.ok) throw new Error("games");
        return r.json();
      })
      .then(function (data) {
        gamesCache = Array.isArray(data) ? data : [];
        return gamesCache;
      })
      .catch(function () {
        gamesFetchPromise = null;
        return [];
      });
    return gamesFetchPromise;
  }

  function setPickLoading(show) {
    var loading = $("logPlayPickLoading");
    var list = $("logPlayGameList");
    var emptyCol = $("logPlayPickEmpty");
    if (loading) {
      loading.hidden = !show;
    }
    if (list) {
      if (show) {
        list.hidden = true;
        list.innerHTML = "";
      }
      list.setAttribute("aria-busy", show ? "true" : "false");
    }
    if (emptyCol && show) {
      emptyCol.hidden = true;
    }
  }

  function renderGameList(games, filter) {
    var list = $("logPlayGameList");
    var emptyMatch = $("logPlayPickNoMatch");
    var emptyCol = $("logPlayPickEmpty");
    if (!list) return;
    list.innerHTML = "";
    var q = (filter || "").trim().toLowerCase();
    var filtered = games.filter(function (g) {
      if (!q) return true;
      return (g.title || "").toLowerCase().indexOf(q) !== -1;
    });
    if (emptyMatch) {
      emptyMatch.hidden = !(q && games.length > 0 && filtered.length === 0);
    }
    if (games.length === 0) {
      list.hidden = true;
      if (emptyMatch) {
        emptyMatch.hidden = true;
      }
      if (emptyCol) {
        emptyCol.hidden = false;
      }
      return;
    }
    if (emptyCol) {
      emptyCol.hidden = true;
    }
    list.hidden = false;
    if (filtered.length === 0) {
      return;
    }
    filtered.forEach(function (g) {
      var li = document.createElement("li");
      var btn = document.createElement("button");
      btn.type = "button";
      btn.className = "hd-tile hd-playing-tile js-log-play-pick-option";
      btn.setAttribute("role", "option");
      btn.dataset.apiId = g.apiId;
      btn.dataset.title = g.title || "";
      var imgWrap = document.createElement("span");
      imgWrap.className = "hd-tile-cover";
      if (g.imageUrl) {
        var img = document.createElement("img");
        img.src = optimizeIgdbCoverUrl(g.imageUrl, "t_cover_small");
        img.alt = "";
        img.width = 48;
        img.height = 64;
        img.loading = "lazy";
        img.decoding = "async";
        imgWrap.appendChild(img);
      } else {
        var ph = document.createElement("span");
        ph.className = "hd-tile-fallback";
        ph.setAttribute("aria-hidden", "true");
        ph.innerHTML = '<i class="fa-solid fa-gamepad"></i>';
        imgWrap.appendChild(ph);
      }
      var textCol = document.createElement("span");
      textCol.className = "hd-tile-text";
      var titleLine = document.createElement("span");
      titleLine.className = "hd-playing-title-line";
      var nameEl = document.createElement("span");
      nameEl.className = "hd-playing-title-name";
      nameEl.textContent = g.title || g.apiId;
      titleLine.appendChild(nameEl);
      var dpm = readNonNegativeInt(g.displayPlayMinutes, 0);
      btn.setAttribute(
        "data-log-baseline-minutes",
        String(dpm),
      );
      var hasLogs = g.hasPlayLogs === true;
      btn.setAttribute("data-log-has-play-logs", hasLogs ? "true" : "false");
      var relPick =
        g.lastPlayedRelative != null && String(g.lastPlayedRelative).trim()
          ? String(g.lastPlayedRelative).trim()
          : "";
      if (relPick) {
        btn.setAttribute("data-log-last-played-relative", relPick);
      } else {
        btn.removeAttribute("data-log-last-played-relative");
      }
      var scount = readNonNegativeInt(g.playSessionCount, 0);
      btn.setAttribute("data-log-play-session-count", String(scount));
      var ptc = readNonNegativeInt(g.playthroughCount, 0);
      btn.setAttribute("data-log-playthrough-count", String(ptc));
      var calPick =
        g.lastPlayedCalendarLine != null &&
        String(g.lastPlayedCalendarLine).trim()
          ? String(g.lastPlayedCalendarLine).trim()
          : "";
      if (calPick) {
        btn.setAttribute("data-log-last-played-calendar", calPick);
      } else {
        btn.removeAttribute("data-log-last-played-calendar");
      }
      var totalPick =
        g.totalPlayTimeLabel != null && String(g.totalPlayTimeLabel).trim()
          ? String(g.totalPlayTimeLabel).trim()
          : "";
      if (totalPick) {
        btn.setAttribute("data-log-total-play-label", totalPick);
      } else {
        btn.removeAttribute("data-log-total-play-label");
      }
      var platRaw = g.platform != null ? String(g.platform).trim() : "";
      if (platRaw) {
        var platEl = document.createElement("span");
        platEl.className = "hd-playing-title-platform";
        platEl.textContent = platRaw;
        titleLine.appendChild(platEl);
      }
      textCol.appendChild(titleLine);
      btn.appendChild(imgWrap);
      btn.appendChild(textCol);
      li.appendChild(btn);
      list.appendChild(li);
    });
  }

  function focusFirstFormControl() {
    setTimeout(function () {
      try {
        var modal = $("logPlayModal");
        var primary = $("logPlayModalSubmit");
        var edit = $("logPlayModalSubmitEdit");
        var isEdit = modal && modal.classList.contains("log-play-modal--edit");
        var target =
          isEdit && edit && !edit.disabled
            ? edit
            : primary && !primary.disabled
              ? primary
              : edit || primary;
        if (target) {
          target.focus();
        }
      } catch (e) {
        /* no-op */
      }
    }, 30);
  }

  function enterFormFromPick(apiId, title, pickBtn) {
    setFormAction(apiId);
    setGameTitle(title);
    showPickStep(false);
    resetForm();
    applyLogPlaySessionSummary();
    state.baselineDisplayMinutes = readNonNegativeIntAttr(
      pickBtn,
      "data-log-baseline-minutes",
      0,
    );
    updateLogPlayTotalPreview();
    applyLogPlayDetailsUi();
    loadAndApplyPlaythroughs(
      apiId,
      null,
      readPlaythroughCountHintFromButton(pickBtn),
    );
    focusFirstFormControl();
  }

  function openActiveSessionForEnd(apiId, title, logId, openerBtn) {
    var base = collectionBaseFromModal();
    if (!base || logId == null || isNaN(Number(logId))) {
      openFromButtonFallback(apiId, title);
      return;
    }
    var rowUrl =
      base +
      "/" +
      encodeURIComponent(apiId) +
      "/play-log/" +
      encodeURIComponent(String(logId)) +
      "/json";
    fetch(rowUrl, { credentials: "same-origin" })
      .then(function (r) {
        if (!r.ok) throw new Error("play-log-json");
        return r.json();
      })
      .then(function (row) {
        if (!row || row.sessionAwaitingEnd !== true) {
          openFromButtonFallback(apiId, title);
          return;
        }
        var startEpochRaw =
          openerBtn && openerBtn.getAttribute
            ? String(
                openerBtn.getAttribute("data-open-session-start-epoch-ms") ||
                  "",
              ).trim()
            : "";
        var startEpoch =
          startEpochRaw !== "" && !isNaN(Number(startEpochRaw))
            ? Number(startEpochRaw)
            : null;
        if (
          startEpoch != null &&
          isFinite(startEpoch) &&
          Date.now() - startEpoch < 60000
        ) {
          return;
        }
        var baselineFromBtn = readNonNegativeIntAttr(
          openerBtn,
          "data-log-baseline-minutes",
          0,
        );
        openLogPlayModalForEndSession({
          apiId: apiId,
          title: title,
          row: row,
          startEpochMs: startEpoch,
          baselineDisplayMinutes: baselineFromBtn,
        });
      })
      .catch(function () {
        openFromButtonFallback(apiId, title);
      });
  }

  function openFromButtonFallback(apiId, title) {
    setFormAction(apiId);
    setGameTitle(title);
    showPickStep(false);
    resetForm();
    applyLogPlayDetailsUi();
    openLogPlayModal();
    loadAndApplyPlaythroughs(apiId, null, null);
    focusFirstFormControl();
  }

  function openFromButton(btn) {
    var modal = $("logPlayModal");
    if (!modal) return;
    if (btn && (btn.disabled || btn.getAttribute("aria-disabled") === "true")) {
      return;
    }
    lastActiveBeforeModal = document.activeElement;
    state.gamesUrl = modal.getAttribute("data-log-games-url") || "";
    var mode = (btn.getAttribute("data-log-mode") || "").trim().toLowerCase();
    var apiId = (btn.getAttribute("data-log-api-id") || "").trim();
    var title = (btn.getAttribute("data-log-title") || "").trim();
    var redirect = (btn.getAttribute("data-log-redirect") || "detail").trim();
    var openSessionIdRaw = String(
      btn.getAttribute("data-log-open-session-id") || "",
    ).trim();
    var openSessionId =
      openSessionIdRaw !== "" && !isNaN(Number(openSessionIdRaw))
        ? Number(openSessionIdRaw)
        : null;

    var redirectEl = $("logPlayRedirectTo");
    if (redirectEl) redirectEl.value = redirect || "detail";

    state.mode = mode === "pick" ? "pick" : "game";

    if (state.mode === "pick") {
      resetForm();
      state.baselineDisplayMinutes = null;
      showPickStep(true);
      if (redirectEl) redirectEl.value = redirect || "home";
      openLogPlayModal();
      var search = $("logPlayGameSearch");
      if (search) {
        search.value = "";
        setTimeout(function () {
          search.focus();
        }, 50);
      }
      if (gamesCache) {
        setPickLoading(false);
        renderGameList(gamesCache, "");
      } else {
        setPickLoading(true);
        fetchGames(state.gamesUrl)
          .then(function (games) {
            setPickLoading(false);
            renderGameList(games, "");
          })
          .catch(function () {
            setPickLoading(false);
            renderGameList([], "");
          });
      }
      return;
    }

    if (!apiId) return;
    if (openSessionId != null) {
      var startGateRaw = String(
        btn.getAttribute("data-open-session-start-epoch-ms") || "",
      ).trim();
      var startGateMs =
        startGateRaw !== "" && !isNaN(Number(startGateRaw))
          ? Number(startGateRaw)
          : null;
      if (
        startGateMs != null &&
        isFinite(startGateMs) &&
        Date.now() - startGateMs < 60000
      ) {
        return;
      }
      openActiveSessionForEnd(apiId, title, openSessionId, btn);
      return;
    }
    setFormAction(apiId);
    setGameTitle(title);
    showPickStep(false);
    resetForm();
    applyLogPlaySessionSummary();
    applyLogPlayDetailsUi();
    state.baselineDisplayMinutes = readNonNegativeIntAttr(
      btn,
      "data-log-baseline-minutes",
      0,
    );
    updateLogPlayTotalPreview();
    openLogPlayModal();
    var ptPrefRaw = String(
      btn.getAttribute("data-log-preferred-playthrough-id") || "",
    ).trim();
    var ptPref =
      ptPrefRaw !== "" && !isNaN(Number(ptPrefRaw)) ? ptPrefRaw : null;
    loadAndApplyPlaythroughs(
      apiId,
      ptPref,
      coalescePlaythroughCountHintFromButton(
        btn,
        readPlaythroughCountHintFromButton(btn),
      ),
    );
    focusFirstFormControl();
  }

  function initLogPlayModal() {
    var m = $("logPlayModal");
    if (!m) return;

    var form = $("logPlayModalForm");
    var closeX = $("logPlayModalClose");
    var backdrop = m.querySelector("[data-log-play-backdrop]");
    var search = $("logPlayGameSearch");
    var list = $("logPlayGameList");
    var note = $("logPlayNote");
    var noteCnt = $("logPlayNoteCount");
    var hiddenDur = $("logPlayDurationMinutesHidden");
    var durH = $("logPlayHoursPart");
    var durM = $("logPlayMinutesPart");
    bindDurationDigitSanitize(durH);
    bindDurationDigitSanitize(durM, { maxLen: 2, maxVal: 59 });
    if (durH) {
      durH.addEventListener("input", refreshTimeFieldsUi);
    }
    if (durM) {
      durM.addEventListener("input", refreshTimeFieldsUi);
    }
    document
      .querySelectorAll("#logPlayModal .js-log-play-duration-quick")
      .forEach(function (btn) {
        btn.addEventListener("click", function () {
          var raw = String(btn.getAttribute("data-quick-add-min") || "").trim();
          var n = parseInt(raw, 10);
          if (isNaN(n)) {
            return;
          }
          addQuickDurationMinutes(n);
        });
      });
    var ptVal = $("logPlayPlaythroughValue");
    if (ptVal) {
      ptVal.addEventListener("change", refreshTimeFieldsUi);
    }
    wireLogPlayPlaythroughDropdown();
    ensureHoursTimeMode();
    clearSessionRangeFields();
    syncSessionFlowButtonsUi();
    applyLogPlaySessionSummary();

    [
      "logPlaySessionStartDate",
      "logPlaySessionStartTime",
      "logPlaySessionEndDate",
      "logPlaySessionEndTime",
    ].forEach(function (rid) {
      var el = $(rid);
      if (el) {
        el.addEventListener("input", refreshTimeFieldsUi);
        el.addEventListener("change", refreshTimeFieldsUi);
      }
    });
    var startedYesterdayCb = $("logPlayStartYesterday");
    if (startedYesterdayCb) {
      startedYesterdayCb.addEventListener("change", function () {
        var sd = $("logPlaySessionStartDate");
        var st = $("logPlaySessionStartTime");
        if (!sd) {
          return;
        }
        if (startedYesterdayCb.checked) {
          sd.value = yesterdayIso();
        } else if (trimInput(sd) === yesterdayIso()) {
          sd.value = trimInput(st) ? localTodayIso() : "";
        }
        refreshTimeFieldsUi();
      });
    }

    if (closeX) {
      closeX.addEventListener("click", closeLogPlayModal);
    }
    var cancelBtn = $("logPlayModalCancel");
    if (cancelBtn) {
      cancelBtn.addEventListener("click", closeLogPlayModal);
    }
    if (backdrop) {
      backdrop.addEventListener("click", closeLogPlayModal);
    }

    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && m.classList.contains("is-open")) {
        closeLogPlayModal();
      }
    });

    if (search) {
      search.addEventListener("input", function () {
        fetchGames(state.gamesUrl).then(function (games) {
          renderGameList(games, search.value);
        });
      });
    }

    if (list) {
      list.addEventListener("click", function (e) {
        var row = e.target && e.target.closest(".js-log-play-pick-option");
        if (!row || !row.dataset.apiId) return;
        enterFormFromPick(row.dataset.apiId, row.dataset.title || "", row);
      });
    }

    if (note && noteCnt) {
      note.addEventListener("input", function () {
        noteCnt.textContent = String(note.value.length);
      });
    }

    document
      .querySelectorAll("#logPlayModal .js-log-play-exp")
      .forEach(function (btn) {
        btn.addEventListener("click", function () {
          var v = btn.getAttribute("data-exp");
          v = v === null ? "OKAY" : String(v);
          syncLogPlayExpSelection(v);
        });
      });

    if (form) {
      form.addEventListener("submit", function (e) {
        setClientLocalTodayHidden();
        var errTime = $("logPlayTimeError");
        if (errTime) {
          errTime.hidden = true;
          errTime.textContent = "";
        }
        var modeEl = $("logPlayTimeInputMode");
        var isRange = modeEl && modeEl.value === "range";
        if (isEndSessionVariant()) {
          setTimeInputMode("range");
          var durEnd = parseDurationMinutesFromFields();
          if (!durEnd.ok) {
            e.preventDefault();
            if (errTime) {
              errTime.textContent = durEnd.msg || "Check duration.";
              errTime.hidden = false;
            }
            return;
          }
          if (durEnd.empty || durEnd.mins < 1) {
            e.preventDefault();
            if (errTime) {
              errTime.textContent =
                "Play sessions must be at least 1 minute. Wait for the timer, or cancel the session.";
              errTime.hidden = false;
            }
            return;
          }
          var effMins = durEnd.mins;
          var startMsEnd = state.endSessionStartEpochMs;
          if (startMsEnd == null || !isFinite(startMsEnd)) {
            e.preventDefault();
            if (errTime) {
              errTime.textContent = "Missing session start.";
              errTime.hidden = false;
            }
            return;
          }
          var nowMs = Date.now();
          var rawEndMs = startMsEnd + effMins * 60000;
          var endMsComputed = Math.min(rawEndMs, nowMs);
          if (endMsComputed - startMsEnd < 60000) {
            e.preventDefault();
            if (errTime) {
              errTime.textContent =
                "Recorded length must be at least 1 minute. Increase duration or wait until enough time has passed.";
              errTime.hidden = false;
            }
            return;
          }
          var startDateEl = $("logPlaySessionStartDate");
          var startTimeEl = $("logPlaySessionStartTime");
          var endDateEl = $("logPlaySessionEndDate");
          var endTimeEl = $("logPlaySessionEndTime");
          if (endDateEl) {
            endDateEl.value = localDateIsoFromEpochMs(endMsComputed);
          }
          if (endTimeEl) {
            endTimeEl.value = localTimeHHMMEpochMs(endMsComputed);
          }
          if (startDateEl && !trimInput(startDateEl)) {
            startDateEl.value = localDateIsoFromEpochMs(startMsEnd);
          }
          if (startTimeEl && !trimInput(startTimeEl)) {
            startTimeEl.value = localTimeHHMMEpochMs(startMsEnd);
          }
          syncRangeHiddenDatesFromTimes();
          isRange = true;
        }
        if (isRange) {
          var rangeResult = validateRangeModeForSubmit();
          if (!rangeResult.ok) {
            e.preventDefault();
            if (errTime) {
              errTime.textContent = rangeResult.msg || "Check session times.";
              errTime.hidden = false;
            }
            return;
          }
          if (hiddenDur) {
            hiddenDur.value = "";
          }
        } else {
          var durResult = parseDurationMinutesFromFields();
          if (!durResult.ok) {
            e.preventDefault();
            if (errTime) {
              errTime.textContent = durResult.msg;
              errTime.hidden = false;
            }
            return;
          }
          if (hiddenDur) {
            hiddenDur.value = durResult.empty ? "" : String(durResult.mins);
          }
        }
        document
          .querySelectorAll("#logPlayModal .js-log-play-submit")
          .forEach(function (btn) {
            btn.disabled = true;
            btn.setAttribute("aria-busy", "true");
          });
      });
    }

    document.querySelectorAll(".js-log-play-open").forEach(function (btn) {
      btn.addEventListener("click", function (e) {
        if (btn.disabled || btn.getAttribute("aria-disabled") === "true") {
          return;
        }
        e.preventDefault();
        openFromButton(btn);
      });
    });
    updateHeroLogPlayButtonState();
    setInterval(updateHeroLogPlayButtonState, 1000);
  }

  function updateHeroLogPlayButtonState() {
    var btn = $("colGameLogPlayBtn");
    var timerEl = $("colGameLogPlayBtnTimer");
    if (!btn || !timerEl) return;
    var raw = String(
      btn.getAttribute("data-open-session-start-epoch-ms") || "",
    ).trim();
    var openId = String(
      btn.getAttribute("data-log-open-session-id") || "",
    ).trim();
    var gateEndPlay = openId !== "";
    if (raw === "" || isNaN(Number(raw))) {
      timerEl.textContent = "";
      if (gateEndPlay) {
        btn.disabled = true;
        btn.title = "Could not read session start time. Refresh the page.";
      }
      return;
    }
    var startMs = Number(raw);
    var elapsedMs = Date.now() - startMs;
    if (!isFinite(elapsedMs) || elapsedMs < 0) {
      timerEl.textContent = "";
      if (gateEndPlay) {
        btn.disabled = true;
        btn.title = "Invalid session timer. Refresh the page.";
      }
      return;
    }
    var totalSecs = Math.floor(elapsedMs / 1000);
    var pad2 = function (n) {
      return n < 10 ? "0" + String(n) : String(n);
    };
    if (totalSecs >= 3600) {
      var h = Math.floor(totalSecs / 3600);
      var m = Math.floor((totalSecs % 3600) / 60);
      var s = totalSecs % 60;
      timerEl.textContent = String(h) + "h " + pad2(m) + "m " + pad2(s) + "s";
    } else {
      var mins = Math.floor(totalSecs / 60);
      var secs = totalSecs % 60;
      timerEl.textContent = String(mins) + "m " + pad2(secs) + "s";
    }
    if (gateEndPlay) {
      var canEnd = elapsedMs >= 60000;
      btn.disabled = !canEnd;
      btn.title = canEnd
        ? "End active session"
        : "Available after 1:00 on the timer";
    }
  }

  window.openLogPlayModalForEdit = openLogPlayModalForEdit;

  window.cgSubmitStartPlayingNowFromHero = function (apiId) {
    var base = collectionBaseFromModal();
    if (!base || !apiId) {
      return;
    }
    var form = document.createElement("form");
    form.method = "POST";
    form.action =
      base + "/" + encodeURIComponent(String(apiId).trim()) + "/play-log";
    function add(name, value) {
      var inp = document.createElement("input");
      inp.type = "hidden";
      inp.name = name;
      inp.value = value;
      form.appendChild(inp);
    }
    var today = localTodayIso();
    var hms = localTimeHHMMSSForInput();
    add("redirectTo", "detail");
    add("timeInputMode", "range");
    add("durationMinutes", "");
    add("clientLocalToday", today);
    add("sessionStartDate", today);
    add("sessionStartTime", hms);
    add("updatePlaythroughPlaytime", "true");
    add("noteContainsSpoilers", "false");
    add("sessionExperience", "OKAY");
    appendCsrfHiddenInput(form);
    document.body.appendChild(form);
    form.submit();
  };

  window.playstateOpenEndSessionFromFloat = function () {
    var root = document.getElementById("navActivePlaySessionFloat");
    if (!root) {
      return;
    }
    var apiId = String(root.getAttribute("data-game-api-id") || "").trim();
    var logIdRaw = String(root.getAttribute("data-log-id") || "").trim();
    var title = String(
      root.getAttribute("data-session-game-title") || "",
    ).trim();
    var logId =
      logIdRaw !== "" && !isNaN(Number(logIdRaw)) ? Number(logIdRaw) : null;
    if (!apiId || logId == null || isNaN(logId)) {
      return;
    }
    var fakeBtn = {
      getAttribute: function (name) {
        if (name === "data-open-session-start-epoch-ms") {
          return String(root.getAttribute("data-start-ms") || "");
        }
        return "";
      },
    };
    openActiveSessionForEnd(apiId, title, logId, fakeBtn);
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initLogPlayModal);
  } else {
    initLogPlayModal();
  }
})();
