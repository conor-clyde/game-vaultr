/**
 * Navbar clock + floating active-session card (single view — timer + End).
 * Session state is server-driven — see GlobalNavModelAdvice / navActivePlaySession*.
 */
(function () {
  const clockTimeEl = document.getElementById('navbarClock');
  const clockHmEl = document.getElementById('navbarClockHm');
  const clockAmPmEl = document.getElementById('navbarClockAmPm');
  const clockDateEl = document.getElementById('navbarClockDate');

  const WD = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
  const MON = [
    'JAN',
    'FEB',
    'MAR',
    'APR',
    'MAY',
    'JUN',
    'JUL',
    'AUG',
    'SEP',
    'OCT',
    'NOV',
    'DEC',
  ];

  function updateClock() {
    if (!clockHmEl || !clockAmPmEl || !clockDateEl) return;
    const now = new Date();
    if (clockTimeEl) clockTimeEl.dateTime = now.toISOString();

    let h24 = now.getHours();
    const ampm = h24 >= 12 ? 'PM' : 'AM';
    let h12 = h24 % 12;
    if (h12 === 0) h12 = 12;
    const hm = `${String(h12).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
    clockHmEl.textContent = hm;
    clockAmPmEl.textContent = ampm;

    const dateLine = `${WD[now.getDay()]}, ${MON[now.getMonth()]} ${now.getDate()}`;
    clockDateEl.textContent = dateLine;
  }

  function scheduleClockToNextMinute() {
    if (!clockHmEl) return;
    const now = new Date();
    const ms = 60000 - (now.getSeconds() * 1000 + now.getMilliseconds());
    window.setTimeout(function () {
      updateClock();
      window.setInterval(updateClock, 60000);
    }, ms);
  }

  if (clockHmEl && clockAmPmEl && clockDateEl) {
    updateClock();
    scheduleClockToNextMinute();
  }

  const sessionRoot = document.getElementById('navActivePlaySessionFloat');
  const sessionTimeEl = document.getElementById('navActivePlaySessionElapsed');
  const endBtn = document.getElementById('navActivePlaySessionEndBtn');

  if (!sessionRoot || !sessionTimeEl) {
    return;
  }

  const raw = String(sessionRoot.getAttribute('data-start-ms') || '').trim();
  const startMs = raw !== '' && !isNaN(Number(raw)) ? Number(raw) : NaN;
  if (!isFinite(startMs) || startMs <= 0) {
    return;
  }

  function pad2(n) {
    return n < 10 ? '0' + String(n) : String(n);
  }

  function formatElapsed(ms) {
    if (!isFinite(ms) || ms < 0) ms = 0;
    const totalSecs = Math.floor(ms / 1000);
    const h = Math.floor(totalSecs / 3600);
    const m = Math.floor((totalSecs % 3600) / 60);
    const s = totalSecs % 60;
    return pad2(h) + ':' + pad2(m) + ':' + pad2(s);
  }

  function tick() {
    const elapsed = Date.now() - startMs;
    sessionTimeEl.textContent = formatElapsed(elapsed);
    const canEnd = elapsed >= 60000;
    if (endBtn) {
      endBtn.disabled = !canEnd;
      endBtn.title = canEnd ? 'End session and save play log' : '';
    }
  }

  tick();
  window.setInterval(tick, 1000);

  if (endBtn) {
    endBtn.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      if (endBtn.disabled) {
        return;
      }
      if (typeof window.playstateOpenEndSessionFromFloat === 'function') {
        window.playstateOpenEndSessionFromFloat();
      } else {
        var gid = String(sessionRoot.getAttribute('data-game-api-id') || '').trim();
        if (gid) {
          window.location.href = '/collection/' + encodeURIComponent(gid);
        }
      }
    });
  }
})();
