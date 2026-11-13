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
        var texte = statut === 403 ? 'Action refusée : vos droits ne le permettent pas.'
            : statut === 401 ? 'Session expirée. Reconnectez-vous pour continuer.'
            : 'Le serveur a répondu ' + statut + '. L\'action n\'a pas abouti.';
        signaler(texte);
    });
    document.addEventListener('htmx:sendError', function () {
        signaler('Le serveur ne répond pas. Vérifiez que le simulateur tourne.');
    });

    // Barre de chargement pendant les requêtes htmx.
    var enCours = 0;
    document.addEventListener('htmx:beforeRequest', function () {
        enCours++;
        document.body.classList.add('chargement');
    });
    document.addEventListener('htmx:afterRequest', function () {
        enCours = Math.max(0, enCours - 1);
        if (enCours === 0) {
            document.body.classList.remove('chargement');
        }
    });

    /* Notification discrète, en bas de l'écran, qui s'effface d'elle-même. */
    var minuteur = null;
    function signaler(texte) {
        var zone = document.getElementById('alerte-globale');
        if (!zone) {
            zone = document.createElement('div');
            zone.id = 'alerte-globale';
            zone.className = 'alerte globale';
            zone.setAttribute('role', 'alert');
            document.body.appendChild(zone);
        }
        zone.textContent = texte;
        zone.hidden = false;
        if (minuteur) {
            clearTimeout(minuteur);
        }
        minuteur = setTimeout(function () { zone.hidden = true; }, 8000);
    }

    /* --- Destination courante : surlignage dans le rail, puis fil d'Ariane. ---
       Une seule destination est retenue : celle dont l'adresse est le plus long prefixe de la page
       (/qualite/planifications ne surligne pas aussi /qualite). Un ecran sans entree dans le rail
       (fiche, flux ou export d'une simulation) nomme la sienne par <main data-parent="...">. */
    function marquerDestination() {
        var chemin = location.pathname.replace(/\/$/, '');
        var courante = null;
        var longueur = -1;
        document.querySelectorAll('.rail a.destination').forEach(function (a) {
            var cible = new URL(a.href, location.href).pathname.replace(/\/$/, '');
            if ((cible === chemin || (cible !== '' && chemin.indexOf(cible + '/') === 0)) && cible.length > longueur) {
                courante = a;
                longueur = cible.length;
            }
        });
        var contenu = document.querySelector('main[data-parent]');
        if (contenu) {
            courante = document.querySelector('.rail a.destination[data-cle="' + contenu.getAttribute('data-parent') + '"]')
                || courante;
        }
        if (courante) {
            courante.setAttribute('aria-current', 'page');
        }
        var fil = document.querySelector('[data-fil]');
        if (fil && !courante) {
            // Ecran hors du rail (Mon compte) : le fil porte son titre.
            var titre = document.querySelector('main h1');
            var seul = document.createElement('b');
            seul.textContent = titre ? titre.textContent.trim() : 'Echo';
            fil.textContent = '';
            fil.appendChild(seul);
        }
        if (fil && courante) {
            var groupe = courante.closest('.rail').querySelectorAll('.groupe');
            var nom = courante.querySelector('.libelle');
            var section = null;
            groupe.forEach(function (p) {
                if (p.compareDocumentPosition(courante) & Node.DOCUMENT_POSITION_FOLLOWING) {
                    section = p.textContent.trim();
                }
            });
            fil.textContent = '';
            if (section) {
                fil.appendChild(document.createTextNode(section + ' / '));
            }
            var fort = document.createElement('b');
            fort.textContent = nom ? nom.textContent.trim() : 'Echo';
            fil.appendChild(fort);
        }
    }

    /* --- Rail replié ou déplié : le choix est gardé dans le navigateur. --- */
    var CLE_RAIL = 'rail';
    function appliquerRail(etroit) {
        document.documentElement.setAttribute('data-rail', etroit ? 'etroit' : 'large');
        document.querySelectorAll('[data-replier-rail]').forEach(function (b) {
            b.setAttribute('aria-pressed', etroit ? 'true' : 'false');
            var libelle = b.querySelector('.libelle');
            var texte = etroit ? 'Déplier le rail' : 'Replier le rail';
            if (libelle) {
                libelle.textContent = texte;
            }
            b.setAttribute('title', texte);
        });
    }
    function railEtroit() {
        try {
            return localStorage.getItem(CLE_RAIL) === 'etroit';
        } catch (e) {
            return false;
        }
    }

    /* --- Telephone : le rail s'ouvre en menu par-dessus la page (bouton de l'en-tete, voile, Echap). --- */
    function ouvrirMenu(ouvert) {
        if (ouvert) {
            document.documentElement.setAttribute('data-menu', 'ouvert');
        } else {
            document.documentElement.removeAttribute('data-menu');
        }
        document.querySelectorAll('[data-menu-mobile]').forEach(function (b) {
            b.setAttribute('aria-expanded', ouvert ? 'true' : 'false');
            b.setAttribute('aria-label', ouvert ? 'Fermer le menu' : 'Ouvrir le menu');
        });
    }
    function brancherMenu() {
        document.querySelectorAll('[data-menu-mobile]').forEach(function (b) {
            b.addEventListener('click', function () {
                ouvrirMenu(document.documentElement.getAttribute('data-menu') !== 'ouvert');
            });
        });
        document.querySelectorAll('[data-fermer-menu]').forEach(function (v) {
            v.addEventListener('click', function () { ouvrirMenu(false); });
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                ouvrirMenu(false);
            }
        });
    }

    /* --- Recherche globale : palette de navigation au clavier (Ctrl+K). --- */
    function construirePalette() {
        var destinations = [];
        document.querySelectorAll('.rail a.destination').forEach(function (a) {
            var libelle = a.querySelector('.libelle');
            if (libelle) {
                destinations.push({nom: libelle.textContent.trim(), url: a.href});
            }
        });
        if (!destinations.length) {
            return null;
        }

        var fond = document.createElement('div');
        fond.className = 'palette-fond';
        fond.hidden = true;
        fond.innerHTML =
            '<div class="palette" role="dialog" aria-modal="true" aria-label="Recherche">' +
            '<input type="search" class="palette-saisie" placeholder="Aller à un écran…" aria-label="Rechercher un écran" autocomplete="off">' +
            '<ul class="palette-liste" role="listbox"></ul>' +
            '<p class="palette-pied">Entrée pour ouvrir, Échap pour fermer. La recherche des simulations et des dossiers arrive avec son écran dédié.</p>' +
            '</div>';
        document.body.appendChild(fond);

        var saisie = fond.querySelector('.palette-saisie');
        var liste = fond.querySelector('.palette-liste');
        var choix = 0;
        var visibles = [];

        function sansAccent(s) {
            return s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
        }

        function dessiner() {
            var q = sansAccent(saisie.value.trim());
            visibles = destinations.filter(function (d) {
                return q === '' || sansAccent(d.nom).indexOf(q) >= 0;
            });
            choix = Math.min(choix, Math.max(0, visibles.length - 1));
            liste.textContent = '';
            if (!visibles.length) {
                var rien = document.createElement('li');
                rien.className = 'palette-rien';
                rien.textContent = 'Aucun écran ne porte ce nom.';
                liste.appendChild(rien);
                return;
            }
            visibles.forEach(function (d, i) {
                var li = document.createElement('li');
                li.setAttribute('role', 'option');
                li.setAttribute('aria-selected', i === choix ? 'true' : 'false');
                li.textContent = d.nom;
                li.addEventListener('mouseenter', function () { choix = i; dessiner(); });
                li.addEventListener('click', function () { location.href = d.url; });
                liste.appendChild(li);
            });
        }

        function ouvrir() {
            fond.hidden = false;
            saisie.value = '';
            choix = 0;
            dessiner();
            saisie.focus();
        }
        function fermer() {
            fond.hidden = true;
        }

        saisie.addEventListener('input', dessiner);
        fond.addEventListener('click', function (e) {
            if (e.target === fond) {
                fermer();
            }
        });
        fond.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                fermer();
            } else if (e.key === 'ArrowDown') {
                e.preventDefault();
                choix = Math.min(choix + 1, visibles.length - 1);
                dessiner();
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                choix = Math.max(choix - 1, 0);
                dessiner();
            } else if (e.key === 'Enter' && visibles[choix]) {
                e.preventDefault();
                location.href = visibles[choix].url;
            }
        });
        return {ouvrir: ouvrir};
    }

    document.addEventListener('DOMContentLoaded', function () {
        marquerDestination();
        appliquerRail(railEtroit());
        brancherMenu();

        document.querySelectorAll('[data-replier-rail]').forEach(function (b) {
            b.addEventListener('click', function () {
                var etroit = !railEtroit();
                try {
                    localStorage.setItem(CLE_RAIL, etroit ? 'etroit' : 'large');
                } catch (e) { /* stockage indisponible : le choix vaut pour cette page seulement */ }
                appliquerRail(etroit);
            });
        });

        var palette = construirePalette();
        if (!palette) {
            return;
        }
        document.querySelectorAll('[data-recherche]').forEach(function (b) {
            b.addEventListener('click', palette.ouvrir);
        });
        document.addEventListener('keydown', function (e) {
            if ((e.ctrlKey || e.metaKey) && (e.key === 'k' || e.key === 'K')) {
                e.preventDefault();
                palette.ouvrir();
            }
        });
    });
})();
