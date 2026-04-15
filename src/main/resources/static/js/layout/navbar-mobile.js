(function () {
  const hamburger = document.getElementById("navHamburger");
  const panel = document.getElementById("navbarMobilePanel");
  const userTrigger = document.getElementById("navUserMenuTrigger");
  const userDropdown = document.getElementById("navbarUserDropdown");
  const userMenuRoot = userTrigger && userTrigger.closest(".navbar-user-menu");

  const mq = window.matchMedia("(max-width: 720px)");

  function setMobileOpen(open) {
    if (!hamburger || !panel) {
      return;
    }
    hamburger.classList.toggle("open", open);
    hamburger.setAttribute("aria-expanded", String(open));
    hamburger.setAttribute("aria-label", open ? "Close menu" : "Open menu");
    panel.classList.toggle("is-open", open);
    panel.setAttribute("aria-hidden", String(!open));
  }

  function setUserMenuOpen(open) {
    if (!userTrigger || !userDropdown || !userMenuRoot) {
      return;
    }
    userMenuRoot.classList.toggle("is-open", open);
    userTrigger.setAttribute("aria-expanded", String(open));
    userDropdown.setAttribute("aria-hidden", String(!open));
    if (open) {
      userDropdown.removeAttribute("hidden");
    } else {
      userDropdown.setAttribute("hidden", "");
    }
  }

  function closeIfNarrow() {
    if (!mq.matches) {
      setMobileOpen(false);
    }
  }

  if (hamburger && panel) {
    hamburger.addEventListener("click", function (e) {
      e.stopPropagation();
      setUserMenuOpen(false);
      setMobileOpen(!panel.classList.contains("is-open"));
    });

    panel.querySelectorAll('a, button[type="submit"]').forEach(function (el) {
      el.addEventListener("click", function () {
        setMobileOpen(false);
      });
    });

    document.addEventListener("click", function (e) {
      if (!panel.classList.contains("is-open")) {
        return;
      }
      if (hamburger.contains(e.target) || panel.contains(e.target)) {
        return;
      }
      setMobileOpen(false);
    });
  }

  if (userTrigger && userDropdown && userMenuRoot) {
    userTrigger.addEventListener("click", function (e) {
      e.stopPropagation();
      setMobileOpen(false);
      setUserMenuOpen(!userMenuRoot.classList.contains("is-open"));
    });

    userDropdown
      .querySelectorAll('button[type="submit"]')
      .forEach(function (btn) {
        btn.addEventListener("click", function () {
          setUserMenuOpen(false);
        });
      });

    document.addEventListener("click", function (e) {
      if (!userMenuRoot.classList.contains("is-open")) {
        return;
      }
      if (userMenuRoot.contains(e.target)) {
        return;
      }
      setUserMenuOpen(false);
    });
  }

  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape") {
      setMobileOpen(false);
      setUserMenuOpen(false);
    }
  });

  mq.addEventListener("change", closeIfNarrow);
  window.addEventListener("resize", closeIfNarrow);
})();
