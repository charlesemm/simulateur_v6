/* Tri des tableaux entierement rendus par le serveur (<table data-triable>).
   Volontairement absent des tableaux pagines : trier une seule page donnerait un classement faux.
   Une colonne porte data-type="nombre" pour un tri numerique, data-tri="non" pour ne pas etre triable. */
(function () {
    'use strict';

    function valeur(ligne, index, type) {
        var cellule = ligne.cells[index];
        var brut = cellule ? cellule.textContent.trim() : '';
        if (type === 'nombre') {
            var n = parseFloat(brut.replace(/[^\d,.-]/g, '').replace(/\s/g, '').replace(',', '.'));
            return isNaN(n) ? -Infinity : n;
        }
        // Date au format jj/mm/aaaa a hh:mm : comparee comme aaaammjjhhmm.
        var d = brut.match(/^(\d{2})\/(\d{2})\/(\d{4})(?:\D+(\d{2}):(\d{2}))?/);
        if (d) {
            return Number(d[3] + d[2] + d[1] + (d[4] || '00') + (d[5] || '00'));
        }
        return brut.toLocaleLowerCase('fr');
    }

    function equiper(table) {
        var corps = table.tBodies[0];
        if (!corps || table.dataset.triPret === 'oui') {
            return;
        }
        table.dataset.triPret = 'oui';
        var entetes = table.tHead ? table.tHead.rows[0].cells : [];

        Array.prototype.forEach.call(entetes, function (th, index) {
            if (th.dataset.tri === 'non') {
                return;
            }
            var libelle = th.textContent.trim();
            th.textContent = '';
            var bouton = document.createElement('button');
            bouton.type = 'button';
            bouton.className = 'tri';
            bouton.appendChild(document.createTextNode(libelle));
            var fleche = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
            fleche.setAttribute('class', 'ico');
            fleche.setAttribute('aria-hidden', 'true');
            var usage = document.createElementNS('http://www.w3.org/2000/svg', 'use');
            usage.setAttribute('href', '#i-tri');
            fleche.appendChild(usage);
            bouton.appendChild(fleche);
            th.appendChild(bouton);

            bouton.addEventListener('click', function () {
                var croissant = th.getAttribute('aria-sort') !== 'ascending';
                Array.prototype.forEach.call(entetes, function (autre) {
                    autre.removeAttribute('aria-sort');
                });
                th.setAttribute('aria-sort', croissant ? 'ascending' : 'descending');
                usage.setAttribute('href', croissant ? '#i-chevron-bas' : '#i-tri');

                var lignes = Array.prototype.slice.call(corps.rows);
                var type = th.dataset.type;
                lignes.sort(function (a, b) {
                    var va = valeur(a, index, type);
                    var vb = valeur(b, index, type);
                    var ordre = va < vb ? -1 : va > vb ? 1 : 0;
                    return croissant ? ordre : -ordre;
                });
                lignes.forEach(function (l) { corps.appendChild(l); });
                bouton.setAttribute('aria-label', libelle + ', tri ' + (croissant ? 'croissant' : 'décroissant'));
            });
        });
    }

    function equiperTout() {
        document.querySelectorAll('table[data-triable]').forEach(equiper);
    }

    document.addEventListener('DOMContentLoaded', equiperTout);
    // Les tableaux remplaces par htmx doivent retrouver leur tri.
    document.addEventListener('htmx:afterSwap', equiperTout);
})();
