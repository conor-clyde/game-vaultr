(function () {
    function bindClose(btn) {
        if (btn.dataset.psFlashDismissBound) {
            return;
        }
        btn.dataset.psFlashDismissBound = '1';
        btn.addEventListener('click', function () {
            var row = btn.closest('.cg-flash');
            if (!row) {
                return;
            }
            var host = row.parentElement;
            row.remove();
            if (
                host &&
                (host.classList.contains('cg-page-flashes') ||
                    host.classList.contains('coll-page-flashes') ||
                    host.classList.contains('sr-flashes')) &&
                !host.querySelector('.cg-flash')
            ) {
                host.remove();
            }
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.cg-flash__close').forEach(bindClose);
    });
})();
