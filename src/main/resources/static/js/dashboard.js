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

    var reduit = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    function css(nom, defaut) {
        return getComputedStyle(document.documentElement).getPropertyValue(nom).trim() || defaut;
    }

    // Nombre qui glisse vers sa nouvelle valeur au lieu de sauter.
    var cours = {};
    function animer(id, cible, decimales) {
        var e = el(id);
        if (!e) {
            return;
        }
        var depart = cours[id] ? cours[id].valeur : 0;
        cible = Number(cible || 0);
        if (cours[id] && cours[id].raf) {
            cancelAnimationFrame(cours[id].raf);
        }
        if (reduit || depart === cible) {
            cours[id] = {valeur: cible};
            e.textContent = nombre(cible, decimales);
            return;
        }
        var debut = performance.now();
        var etat = cours[id] = {valeur: depart};
        (function pas(t) {
            var p = Math.min(1, (t - debut) / 600);
            var f = 1 - Math.pow(1 - p, 3);
            etat.valeur = depart + (cible - depart) * f;
            e.textContent = nombre(etat.valeur, decimales);
            if (p < 1) {
                etat.raf = requestAnimationFrame(pas);
            }
        })(debut);
    }

    var graphiques = [];

    function courbe(idCanvas, libelle, variableCouleur) {
        var canvas = el(idCanvas);
        if (!canvas || typeof Chart === 'undefined') {
            return null;
        }
        var graphique = new Chart(canvas, {
            type: 'line',
            data: {labels: [], datasets: [{label: libelle, data: [],
                fill: true, pointRadius: 0, pointHoverRadius: 4, tension: 0.35, borderWidth: 2.5}]},
            options: {
                animation: reduit ? false : {duration: 400},
                maintainAspectRatio: false,
                interaction: {mode: 'index', intersect: false},
                scales: {
                    x: {display: false},
                    y: {beginAtZero: true, border: {display: false}}
                },
                plugins: {legend: {labels: {usePointStyle: true, boxWidth: 8}}}
            }
        });
        graphique.variableCouleur = variableCouleur;
        graphiques.push(graphique);
        habiller(graphique);
        return graphique;
    }

    // Couleurs lues dans le thème courant (clair / sombre) ; rappelé à chaque changement de thème.
    function habiller(g) {
        var couleur = css(g.variableCouleur, '#1565c0');
        var degrade = g.canvas.getContext('2d').createLinearGradient(0, 0, 0, 240);
        degrade.addColorStop(0, couleur + '66');
        degrade.addColorStop(1, couleur + '00');
        var jeu = g.data.datasets[0];
        jeu.borderColor = couleur;
        jeu.backgroundColor = degrade;
        var texte = css('--discret', '#5a6b80');
        g.options.scales.y.grid = {color: css('--bord', '#dde6f0')};
        g.options.scales.y.ticks = {color: texte};
        g.options.plugins.legend.labels.color = texte;
        g.update('none');
    }
    document.addEventListener('themechange', function () {
        graphiques.forEach(habiller);
    });

    var debit = courbe('graphique-debit', 'Événements écrits par seconde', '--accent');
    var latence = courbe('graphique-latence', 'Latence moyenne d\'un lot (ms)', '--vert');

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
        graphique.update();
    }

    function kpis(etat) {
        animer('k-debit', etat.evenementsEcritsParSeconde, 1);
        animer('k-total', etat.evenementsEcrits, 0);
        animer('k-file', etat.fileTaille, 0);
        animer('k-latence', etat.latenceLotMs, 1);
        var base = etat.base === 'UP';
        el('kpi-debit').classList.toggle('ok', base && etat.evenementsEcritsParSeconde > 0);
        el('kpi-file').classList.toggle('alerte-kpi', etat.fileTaille > 0 && etat.lotsEnEchec > 0);
        el('kpi-latence').classList.toggle('alerte-kpi', !base || etat.latenceLotMs > 500);
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

        kpis(etat);

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
