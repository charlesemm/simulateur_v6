/* Thème clair / sombre / automatique. Chargé dans <head> SANS defer : le thème est posé avant l'affichage
   (pas de flash). Le choix est gardé dans le navigateur ; sans choix, on suit le réglage du système. */
(function () {
    'use strict';
    var CLE = 'theme';
    var ORDRE = ['auto', 'light', 'dark'];
    var LIBELLES = {auto: 'automatique', light: 'clair', dark: 'sombre'};

    function lire() {
        try {
            var v = localStorage.getItem(CLE);
            return ORDRE.indexOf(v) >= 0 ? v : 'auto';
        } catch (e) {
            return 'auto';
        }
    }

    function appliquer(choix) {
        var racine = document.documentElement;
        racine.setAttribute('data-choix', choix);
        if (choix === 'auto') {
            racine.removeAttribute('data-theme');
        } else {
            racine.setAttribute('data-theme', choix);
        }
        document.dispatchEvent(new CustomEvent('themechange', {detail: choix}));
    }

    appliquer(lire());

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-bascule-theme]').forEach(function (bouton) {
            function etiquette() {
                var c = lire();
                var texte = 'Thème : ' + LIBELLES[c] + ' (cliquer pour changer)';
                bouton.setAttribute('aria-label', texte);
                bouton.setAttribute('title', texte);
            }
            etiquette();
            bouton.addEventListener('click', function () {
                var suivant = ORDRE[(ORDRE.indexOf(lire()) + 1) % ORDRE.length];
                try {
                    localStorage.setItem(CLE, suivant);
                } catch (e) { /* stockage indisponible : le choix vaut pour cette page seulement */ }
                appliquer(suivant);
                etiquette();
            });
        });
    });

    // En mode automatique, suivre le système en direct.
    if (window.matchMedia) {
        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function () {
            document.dispatchEvent(new CustomEvent('themechange', {detail: lire()}));
        });
    }
})();
