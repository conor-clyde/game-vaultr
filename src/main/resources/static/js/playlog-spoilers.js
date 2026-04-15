(function () {
    'use strict';

    function openSpoiler(wrap) {
        wrap.classList.add('is-revealed');
        var revealBtn = wrap.querySelector('.js-playlog-spoiler-reveal');
        var overlay = wrap.querySelector('.cg-playlog-spoiler-overlay');
        var hideBtn = wrap.querySelector('.js-playlog-spoiler-hide');
        if (revealBtn) {
            revealBtn.setAttribute('aria-expanded', 'true');
        }
        if (overlay) {
            overlay.setAttribute('aria-hidden', 'true');
        }
        if (hideBtn) {
            hideBtn.hidden = false;
            hideBtn.setAttribute('aria-hidden', 'false');
        }
        document.dispatchEvent(
            new CustomEvent('playstate-playlog-spoiler-opened', {
                bubbles: true,
                detail: { container: wrap },
            }));
    }

    function closeSpoiler(wrap) {
        wrap.classList.remove('is-revealed');
        var revealBtn = wrap.querySelector('.js-playlog-spoiler-reveal');
        var overlay = wrap.querySelector('.cg-playlog-spoiler-overlay');
        var hideBtn = wrap.querySelector('.js-playlog-spoiler-hide');
        if (revealBtn) {
            revealBtn.setAttribute('aria-expanded', 'false');
        }
        if (overlay) {
            overlay.setAttribute('aria-hidden', 'false');
        }
        if (hideBtn) {
            hideBtn.hidden = true;
            hideBtn.setAttribute('aria-hidden', 'true');
        }
    }

    document.addEventListener('click', function (e) {
        var revealTrigger = e.target && e.target.closest('.js-playlog-spoiler-reveal');
        if (revealTrigger) {
            e.preventDefault();
            e.stopPropagation();
            var wrap = revealTrigger.closest('.cg-playlog-spoiler');
            if (wrap) {
                openSpoiler(wrap);
            }
            return;
        }
        var hideTrigger = e.target && e.target.closest('.js-playlog-spoiler-hide');
        if (hideTrigger) {
            e.preventDefault();
            e.stopPropagation();
            var wrap = hideTrigger.closest('.cg-playlog-spoiler');
            if (wrap) {
                closeSpoiler(wrap);
            }
        }
    });
})();
