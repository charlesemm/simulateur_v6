/* Tableau de bord : lit le flux SSE (/flux) et met à jour la page. Aucun HTML construit à partir des données :
   tout passe par textContent. */
(function () {
    'use strict';

    var racine = document.getElementById('tableau');
    if (!racine) {
        return;
    }
    var POINTS = 60;
    var etatFlux = document.getElementById('flux-etat');

    function el(id) {
        return document.getElementById(id);
    }

    function texte(id, valeur) {
        var e = el(id);
        if (e) {
            e.textContent = valeur;
        }
    }

    function nombre(v, decimales) {
        return Number(v || 0).toLocaleString('fr-FR', {
            minimumFractionDigits: decimales || 0,
            maximumFractionDigits: decimales || 0
        });
    }

    function badge(id, valeur, bon) {
        var e = el(id);
        if (!e) {
            return;
        }
        e.textContent = valeur;
        e.classList.toggle('echec', !bon);
        e.classList.toggle('actif', !!bon);
    }

    function courbe(idCanvas, libelle) {
        var canvas = el(idCanvas);
        if (!canvas || typeof Chart === 'undefined') {
            return null;
        }
        var couleur = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#0b5cad';
        return new Chart(canvas, {
            type: 'line',
            data: {labels: [], datasets: [{label: libelle, data: [], borderColor: couleur, backgroundColor: couleur,
                pointRadius: 0, tension: 0.2, borderWidth: 2}]},
            options: {
                animation: false,
                maintainAspectRatio: false,
                scales: {x: {display: false}, y: {beginAtZero: true}},
                plugins: {legend: {display: true}}
            }
        });
    }

    var debit = courbe('graphique-debit', 'Événements écrits par seconde');
    var latence = courbe('graphique-latence', 'Latence moyenne d\'un lot (ms)');

    function pousser(graphique, etiquette, valeur) {
        if (!graphique) {
            return;
        }
        var d = graphique.data;
        d.labels.push(etiquette);
        d.datasets[0].data.push(valeur);
        while (d.labels.length > POINTS) {
            d.labels.shift();
            d.datasets[0].data.shift();
        }
        graphique.update('none');
    }

    function afficher(etat) {
        var run = etat.run;
        if (run) {
            badge('sim-etat', run.etat, run.etat === 'EN_COURS');
            texte('sim-scenario', run.scenario);
            texte('sim-graine', run.seed);
            texte('sim-parcours', nombre(run.parcoursGeneres));
            texte('sim-emis', nombre(run.evenementsEmis));
        } else {
            badge('sim-etat', 'Aucun run', false);
            texte('sim-scenario', '-');
            texte('sim-graine', '-');
            texte('sim-parcours', '-');
            texte('sim-emis', '-');
        }
        var erreur = el('sim-erreur');
        if (erreur) {
            erreur.hidden = !(run && run.erreur);
            erreur.textContent = run && run.erreur ? run.erreur : '';
        }

        badge('base-etat', etat.base, etat.base === 'UP');
        badge('proxy-etat', etat.proxy.etat, etat.proxy.etat === 'ACTIF');
        texte('proxy-toxics', etat.proxy.toxics.length ? etat.proxy.toxics.join(', ') : 'aucune');

        texte('m-ecrits-s', nombre(etat.evenementsEcritsParSeconde, 1));
        texte('m-ecrits', nombre(etat.evenementsEcrits));
        texte('m-file', nombre(etat.fileTaille));
        texte('m-latence', nombre(etat.latenceLotMs, 1) + ' ms');
        texte('m-latence-max', nombre(etat.latenceLotMaxMs, 1) + ' ms');
        texte('m-erreurs', nombre(etat.lotsEnEchec));

        var corps = el('debits');
        corps.textContent = '';
        if (etat.debits.length === 0) {
            var vide = document.createElement('tr');
            var cellule = document.createElement('td');
            cellule.colSpan = 3;
            cellule.className = 'discret';
            cellule.textContent = 'Aucun run actif';
            vide.appendChild(cellule);
            corps.appendChild(vide);
        }
        etat.debits.forEach(function (d) {
            var ligne = document.createElement('tr');
            [d.domaine, nombre(d.parcoursParSeconde, 1), nombre(d.evenementsParSeconde, 1)].forEach(function (v) {
                var td = document.createElement('td');
                td.textContent = v;
                ligne.appendChild(td);
            });
            corps.appendChild(ligne);
        });

        var heure = new Date(etat.instant).toLocaleTimeString('fr-FR');
        pousser(debit, heure, etat.evenementsEcritsParSeconde);
        pousser(latence, heure, etat.latenceLotMs);
    }

    var source = new EventSource(racine.dataset.flux);
    source.addEventListener('etat', function (e) {
        try {
            afficher(JSON.parse(e.data));
            etatFlux.textContent = 'Flux en direct, mis à jour chaque seconde.';
        } catch (erreur) {
            etatFlux.textContent = 'Donnée illisible reçue du flux.';
        }
    });
    source.onerror = function () {
        etatFlux.textContent = 'Flux interrompu : nouvelle tentative en cours…';
    };
    window.addEventListener('beforeunload', function () {
        source.close();
    });
})();
