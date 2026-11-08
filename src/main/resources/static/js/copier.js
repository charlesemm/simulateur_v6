/*
 * Bouton « copier » : tout element portant data-copier copie le contenu de son attribut data-texte.
 * Replie sur une zone de texte cachee quand le presse-papiers n'est pas accessible (http, vieux navigateur).
 */
(function () {
    'use strict';

    function replier(texte) {
        var zone = document.createElement('textarea');
        zone.value = texte;
        zone.setAttribute('readonly', '');
        zone.style.position = 'fixed';
        zone.style.opacity = '0';
        document.body.appendChild(zone);
        zone.select();
        var ok = false;
        try {
            ok = document.execCommand('copy');
        } catch (e) {
            ok = false;
        }
        document.body.removeChild(zone);
        return ok;
    }

    function confirmer(bouton, ok) {
        var avant = bouton.dataset.libelleAvant || bouton.textContent.trim();
        bouton.dataset.libelleAvant = avant;
        bouton.textContent = ok ? 'Copié' : 'Copie impossible — sélectionnez le texte';
        window.setTimeout(function () {
            bouton.textContent = avant;
        }, 2500);
    }

    document.addEventListener('click', function (evenement) {
        var bouton = evenement.target.closest('[data-copier]');
        if (!bouton) {
            return;
        }
        evenement.preventDefault();
        var texte = bouton.getAttribute('data-texte') || '';
        if (navigator.clipboard && window.isSecureContext) {
            navigator.clipboard.writeText(texte).then(function () {
                confirmer(bouton, true);
            }, function () {
                confirmer(bouton, replier(texte));
            });
        } else {
            confirmer(bouton, replier(texte));
        }
    });
})();
