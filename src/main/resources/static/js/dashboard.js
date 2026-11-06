/* Tableau de bord : lit le flux SSE (/flux) et met à jour la page. Aucun HTML construit à partir des données :
   tout passe par textContent. */
(function () {
    'use strict';

    var racine = document.getElementById('tableau');
    if (!racine) {
        return;
    }
    var POINTS = 60;
    var resume = document.getElementById('sim-resume');

    /* Libellés des états du moteur, tels qu'ils doivent apparaître à l'écran. */
    var ETATS = {
        CREE: 'Prête à démarrer',
        EN_COURS: 'En marche',
        EN_PAUSE: 'En pause',
        ARRETE: 'Terminée',
        ECHOUE: 'Interrompue'
    };

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

    /* Badge d'état : vert quand ça tourne, orange quand c'est une charge ou une panne voulue, rouge sinon. */
    function badge(id, valeur, ton) {
        var e = el(id);
        if (!e) {
            return;
        }
        e.textContent = valeur;
        e.classList.toggle('actif', ton === 'ok');
        e.classList.toggle('charge', ton === 'charge');
        e.classList.toggle('echec', ton === 'echec');
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

    /* --- Traces des tuiles : une mini-courbe dessinée à la main, sans bibliothèque. --- */
    var traces = {};
    function tracer(id, valeur) {
        var svg = el(id);
        if (!svg) {
            return;
        }
        var points = traces[id] || (traces[id] = []);
        points.push(Number(valeur || 0));
        while (points.length > 40) {
            points.shift();
        }
        var ligne = svg.querySelector('polyline');
        if (points.length < 2) {
            ligne.setAttribute('points', '');
            return;
        }
        var max = Math.max.apply(null, points);
        var min = Math.min.apply(null, points);
        var etendue = max - min || 1;
        var pas = 120 / (points.length - 1);
        ligne.setAttribute('points', points.map(function (v, i) {
            return (i * pas).toFixed(1) + ',' + (26 - ((v - min) / etendue) * 24).toFixed(1);
        }).join(' '));
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
        degrade.addColorStop(0, couleur + '55');
        degrade.addColorStop(1, couleur + '00');
        var jeu = g.data.datasets[0];
        jeu.borderColor = couleur;
        jeu.backgroundColor = degrade;
        var gris = css('--discret', '#5b6c83');
        g.options.scales.y.grid = {color: css('--bord', '#dae3ee')};
        g.options.scales.y.ticks = {color: gris};
        g.options.plugins.legend.labels.color = gris;
        g.update('none');
    }
    document.addEventListener('themechange', function () {
        graphiques.forEach(habiller);
    });

    var debit = courbe('graphique-debit', 'Lignes enregistrées par seconde', '--bleu');
    var latence = courbe('graphique-latence', 'Temps d\'enregistrement d\'un paquet (ms)', '--vert');

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

    /* Le rythme métier : des feuilles de soins par minute, somme de tous les domaines actifs. */
    function rythmeParMinute(debits) {
        return (debits || []).reduce(function (total, d) {
            return total + Number(d.parcoursParSeconde || 0);
        }, 0) * 60;
    }

    function phrase(etat) {
        var run = etat.run;
        if (!run) {
            return 'Aucune simulation en cours. Lancez-en une depuis l\'écran Lancement.';
        }
        var morceaux = ['Scénario ' + run.scenario];
        morceaux.push('graine ' + run.seed);
        if (run.etat === 'EN_COURS') {
            morceaux.push(nombre(rythmeParMinute(etat.debits)) + ' feuilles de soins par minute');
        }
        return morceaux.join(', ') + '.';
    }

    function afficher(etat) {
        var run = etat.run;
        var enMarche = !!run && run.etat === 'EN_COURS';
        var baseDebout = etat.base === 'UP';
        var perturbations = (etat.proxy && etat.proxy.toxics) || [];

        texte('ecran-titre', run ? 'Simulation ' + (ETATS[run.etat] || run.etat).toLowerCase() : 'Aucune simulation');
        if (run) {
            badge('sim-etat', ETATS[run.etat] || run.etat,
                enMarche ? 'ok' : run.etat === 'ECHOUE' ? 'echec' : run.etat === 'EN_PAUSE' ? 'charge' : null);
        } else {
            badge('sim-etat', 'Rien en cours', null);
        }
        resume.textContent = phrase(etat);

        var erreur = el('sim-erreur');
        if (erreur) {
            erreur.hidden = !(run && run.erreur);
            erreur.textContent = run && run.erreur ? run.erreur : '';
        }

        /* --- Tuiles --- */
        var parcours = run ? run.parcoursGeneres : 0;
        var rythme = rythmeParMinute(etat.debits);
        animer('k-parcours', parcours, 0);
        animer('k-total', etat.evenementsEcrits, 0);
        animer('k-rythme', rythme, 0);
        animer('k-file', etat.fileTaille, 0);

        texte('k-parcours-detail', run
            ? nombre(run.evenementsEmis) + ' événements produits'
            : 'en attente d\'une simulation');
        texte('k-total-detail', nombre(etat.evenementsEcritsParSeconde, 1) + ' lignes par seconde');
        texte('k-rythme-detail', enMarche ? 'rythme tenu' : 'à l\'arrêt');
        texte('k-file-detail', etat.fileTaille > 0
            ? 'écriture en retard sur la génération'
            : 'rien en attente');

        var detailFile = el('k-file-detail');
        if (detailFile) {
            detailFile.classList.toggle('charge', etat.fileTaille > 0);
        }
        var detailRythme = el('k-rythme-detail');
        if (detailRythme) {
            detailRythme.classList.toggle('bon', enMarche);
        }

        tracer('trace-parcours', parcours);
        tracer('trace-total', etat.evenementsEcrits);
        tracer('trace-rythme', rythme);
        tracer('trace-file', etat.fileTaille);

        /* --- Santé --- */
        badge('base-etat', baseDebout ? 'Tout va bien' : 'Base injoignable', baseDebout ? 'ok' : 'echec');
        texte('s-ecriture', etat.lotsEnEchec > 0
            ? 'reprises après échec'
            : etat.fileTaille > 0 ? 'en retard de ' + nombre(etat.fileTaille) + ' lignes' : 'à jour');
        texte('s-liaison', perturbations.length
            ? 'perturbée volontairement'
            : baseDebout ? 'normale, ' + nombre(etat.latenceLotMs, 0) + ' ms par paquet' : 'coupée');
        texte('s-pannes', perturbations.length ? perturbations.join(', ') : 'aucune');
        texte('s-echecs', nombre(etat.lotsEnEchec));

        /* --- Ce qui tourne --- */
        texte('sim-scenario', run ? run.scenario : '—');
        texte('sim-graine', run ? run.seed : '—');
        texte('sim-parcours', run ? nombre(run.parcoursGeneres) : '—');
        texte('sim-emis', run ? nombre(run.evenementsEmis) : '—');

        /* --- Détail technique --- */
        badge('proxy-etat', etat.proxy.etat, etat.proxy.etat === 'ACTIF' ? 'ok' : 'echec');
        texte('proxy-toxics', perturbations.length ? perturbations.join(', ') : 'aucune');
        texte('m-ecrits-s', nombre(etat.evenementsEcritsParSeconde, 1));
        texte('m-ecrits', nombre(etat.evenementsEcrits));
        texte('m-file', nombre(etat.fileTaille));
        texte('m-latence', nombre(etat.latenceLotMs, 1) + ' ms');
        texte('m-latence-max', nombre(etat.latenceLotMaxMs, 1) + ' ms');
        texte('m-erreurs', nombre(etat.lotsEnEchec));

        /* --- Par domaine, en rythme métier --- */
        var corps = el('debits');
        corps.textContent = '';
        if (etat.debits.length === 0) {
            var vide = document.createElement('tr');
            var cellule = document.createElement('td');
            cellule.colSpan = 3;
            cellule.className = 'discret';
            cellule.textContent = 'Aucune simulation en cours.';
            vide.appendChild(cellule);
            corps.appendChild(vide);
        }
        etat.debits.forEach(function (d) {
            var ligne = document.createElement('tr');
            var cellules = [
                d.domaine,
                nombre(Number(d.parcoursParSeconde || 0) * 60, 0),
                nombre(Number(d.evenementsParSeconde || 0) * 60, 0)
            ];
            cellules.forEach(function (v, i) {
                var td = document.createElement('td');
                td.textContent = v;
                if (i > 0) {
                    td.className = 'chiffre';
                }
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
        } catch (erreur) {
            resume.textContent = 'Le flux a envoyé une donnée illisible.';
        }
    });
    source.onerror = function () {
        resume.textContent = 'Flux interrompu. Nouvelle tentative en cours…';
    };
    window.addEventListener('beforeunload', function () {
        source.close();
    });
})();
