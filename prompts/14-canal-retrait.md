# Prompt 14 - Canal de retrait et échecs d'échange (brique Q2)

Dépend du prompt 13 (l'export doit exister). Plan : `docs/07-qualite-donnees/04-plan.md`.

```text
# Prompt 14 - Canal de retrait et échecs d'échange

Contexte :
  Lire CLAUDE.md, docs/journal.md, puis docs/07-qualite-donnees/04-plan.md (section Q2)
  et docs/07-qualite-donnees/02-ce-qui-est-demande.md (sections 4.3 et 4.5).
  Décision actée : l'outil de qualité est un SCRIPT AUTONOME qui vient chercher le jeu
  (pull). Écho ne le lance pas et ne connaît pas sa machine. Écho expose, le script prend.

Objectif :
  Qu'un script extérieur fasse le cycle complet sans intervention : trouver la dernière
  simulation terminée, retirer son export, pousser son rapport. Et qu'un échange qui
  échoue soit affiché comme un échec d'échange, JAMAIS comme un taux de détection de 0 %.

Emplacement :
  api/ (AnalyseController, compléments à RunController), quality/ (EchangeService),
  persistence/ (Echange + repository), src/main/resources/db/migration/V14__echange.sql,
  scripts/analyse/ (script Python d'exemple + son README).

Contraintes :
  - GET /api/runs : vérifier que le filtre par statut et une limite existent déjà ;
    sinon les ajouter. Le script doit pouvoir demander la dernière simulation ARRETE.
  - GET /api/analyses/attendues : ce qu'Écho attend qu'on analyse maintenant. Rend une
    liste vide tant que la planification du prompt 15 n'existe pas — l'endpoint est
    écrit ici, sa source de vérité arrive au prompt 15. C'est ce point qui réconcilie
    « le script est autonome » et « la planification est dans Écho ».
  - Table echange : id, run_id, sens ('ENVOI' ou 'RECEPTION'), date, version_outil,
    statut, detail. Un envoi est enregistré quand le script retire l'export ; une
    réception quand il pousse un rapport, qu'il soit accepté ou refusé.
  - LES TROIS ÉCHECS D'ÉCHANGE, chacun avec son statut propre et son libellé :
      . SILENCE          : l'export a été retiré, aucun rapport n'est arrivé dans le
                           délai réglable (simulateur.qualite.delai-rapport-minutes) ;
      . RAPPORT_MALFORME : l'envoi a été refusé en bloc (422, mécanisme existant) ;
      . RAPPORT_SANS_DETAIL : le rapport est accepté syntaxiquement mais aucune
                           détection ne porte à la fois un type et un champ
                           exploitables — cahier 2.3 : aucune mesure fiable n'est
                           possible, c'est une information sur l'outil, à documenter.
    Aucun des trois ne doit produire un chiffre de détection. L'écran dira
    « échec d'échange », pas « 0 % détecté ».
  - Un rapport reçu alors qu'aucun export n'a été retiré pour ce run est accepté et
    tracé : le script a le droit de travailler sans passer par la planification.
  - Le POST /api/runs/{id}/quality/report EXISTANT ne change pas de contrat : on
    l'enveloppe pour tracer l'échange, on ne le réécrit pas.
  - scripts/analyse/ : un script Python d'exemple, sans dépendance hors bibliothèque
    standard si possible, qui lit l'URL et la clé d'API dans l'environnement, récupère
    l'export csv, applique quelques règles simples (champ vide, montant négatif,
    format d'identifiant) et pousse son rapport. Son README dit en trois lignes ce
    qu'il est : une RÉFÉRENCE D'INTÉGRATION et un outil témoin, pas le produit, et
    remplaçable par le vrai outil CNAM sans toucher à Écho.
  - Le script n'est pas construit par Maven, n'entre pas dans l'image, et n'est pas
    appelé par les tests Java.

Critère de fin :
  - Test MockMvc : les nouveaux endpoints sous clé d'API, 401 sans clé.
  - Test d'intégration : retrait de l'export puis envoi d'un rapport ; deux lignes
    dans echange, horodatées, dans le bon ordre.
  - Test : retrait sans rapport, délai dépassé, l'échange passe en SILENCE et la
    comparaison ne rend AUCUN taux (ni 0 %) pour ce run.
  - Test : rapport dont toutes les détections sont sans champ ni type exploitable,
    statut RAPPORT_SANS_DETAIL, aucun chiffre produit.
  - Vérification manuelle à reporter : le script d'exemple exécuté contre
    l'application lancée, de bout en bout, avec la commande PowerShell exacte.
  - ./mvnw clean verify est vert.

À ne pas faire :
  - Ne pas faire pousser Écho vers l'outil (pas de mode push, pas de client HTTP
    sortant, pas de QualityClient).
  - Ne pas modifier le calcul du rappel ni de la précision (LigneQualite est juste).
  - Ne pas écrire la planification ici (prompt 15), seulement l'endpoint qui la lira.
  - Ne pas toucher au moteur, aux étapes, aux mutators ni au chaos.

En fin de tâche :
  Proposer le message de commit, mettre à jour docs/journal.md, signaler les hypothèses.
```

**À vérifier toi-même**

- Avec `curl.exe` et la clé d'API : récupère la dernière simulation terminée, puis son export, puis pousse un rapport. L'écran montre les deux échanges horodatés.
- Lance le script d'exemple : il doit tourner seul, sans qu'Écho l'appelle.
- Retire un export et ne pousse rien : passé le délai, l'écran dit « en attente du script », pas 0 %.

