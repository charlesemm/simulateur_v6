/* Comportements communs. Aucune dépendance externe ; htmx est servi localement (js/vendor). */
(function () {
    'use strict';

    function meta(nom) {
        var m = document.querySelector('meta[name="' + nom + '"]');
        return m ? m.getAttribute('content') : null;
    }

    // Les requêtes htmx portent le jeton CSRF (la protection reste active côté serveur).
    document.addEventListener('htmx:configRequest', function (e) {
        var jeton = meta('csrf-token');
        var entete = meta('csrf-header') || 'X-CSRF-TOKEN';
        if (jeton) {
            e.detail.headers[entete] = jeton;
        }
    });

    // Un refus métier (422) porte un message à afficher : htmx doit l'insérer comme une réponse normale.
    document.addEventListener('htmx:beforeSwap', function (e) {
        if (e.detail.xhr && e.detail.xhr.status === 422) {
            e.detail.shouldSwap = true;
            e.detail.isError = false;
        }
    });

    // Erreur réseau ou serveur : message visible plutôt qu'un bouton qui ne fait rien.
    document.addEventListener('htmx:responseError', function (e) {
        var statut = e.detail.xhr ? e.detail.xhr.status : '?';
        var texte = statut === 403 ? 'Action refusée : droits insuffisants.'
            : statut === 401 ? 'Session expirée : reconnectez-vous.'
            : 'Erreur ' + statut + ' : l\'action n\'a pas abouti.';
        alerte(texte);
    });
    document.addEventListener('htmx:sendError', function () {
        alerte('Le serveur ne répond pas.');
    });

    function alerte(texte) {
        var zone = document.getElementById('alerte-globale');
        if (!zone) {
            zone = document.createElement('div');
            zone.id = 'alerte-globale';
            zone.className = 'alerte globale';
            zone.setAttribute('role', 'alert');
            document.body.insertBefore(zone, document.body.firstChild.nextSibling);
        }
        zone.textContent = texte;
    }
})();
