# Prompt 16 - L'onglet Analyse en poste de pilotage (brique Q4)

Dépend des prompts 13, 14 et 15. Plan : `docs/07-qualite-donnees/04-plan.md`.

```text
# Prompt 16 - L'onglet Analyse en poste de pilotage

Contexte :
  Lire CLAUDE.md, docs/journal.md, docs/07-qualite-donnees/04-plan.md (section Q4),
  puis l'écran existant : web/QualiteController.java et templates/quality-report.html.
  Le système de design est posé (css/tokens.css, base.css, composants.css,
  mise-en-page.css). Aucune ressource externe : htmx et Chart.js sont servis en local.

Objectif :
  Que /qualite cesse d'être un simple tableau de résultats et devienne l'onglet d'où
  l'on programme les analyses, suit les échanges, lit le rappel et la précision, et
  ouvre d'un clic la ligne exacte que le script a manquée.

Emplacement :
  web/QualiteController.java, web/AnalyseWebService (ou extension du service
  existant), templates/quality-report.html et nouveaux fragments.
  Aucune migration.

Contraintes :
  - Trois blocs sur l'écran, dans cet ordre :
      1. Planification : la liste des analyses programmées, un formulaire pour en
         créer une (libellé, heure, jours, cible), activer/désactiver, supprimer.
         Suppression derrière hx-confirm, comme les autres actions destructrices.
      2. Analyses : l'historique des échéances, triable par tri.js, avec pour chacune
         sa simulation, son horodatage de retrait, celui du rapport et son STATUT EN
         CLAIR. Les trois échecs d'échange s'affichent comme des échecs d'échange :
         « en attente du script », « rapport refusé », « rapport sans détail ».
         JAMAIS un taux de 0 % à leur place — c'est l'exigence 4.5 du cahier.
      3. Résultat : l'existant (4 tuiles, tableau par type), inchangé dans son calcul.
  - VOCABULAIRE : tauxDetection s'affiche « Taux de rappel », avec sous le chiffre
    « anomalies retrouvées sur anomalies semées ». La précision garde son nom, avec
    « anomalies justes sur anomalies signalées ». Le calcul de LigneQualite ne bouge
    pas d'une ligne : seul le libellé change.
  - LES LIGNES DEVIENNENT CLIQUABLES. C'est ce que le porteur du projet appelle
    « accéder aux lignes directement en base » :
      . chaque anomalie ratée et chaque faux positif porte un lien vers l'écran Flux
        de données, filtré sur son event_id ;
      . l'écran Flux de données accepte donc un filtre par event_id (le vérifier,
        l'ajouter sinon) et affiche la ligne seule, avec son payload lisible.
    On ne crée aucune table : on interroge les tables métier existantes.
  - Un volet « Anomalies ratées » dépliable sous le tableau par type : la liste des
    event_id manqués, leur type, leur champ, la valeur d'origine et la valeur
    corrompue (lues dans anomaly_injected), plafonnée et paginée comme ailleurs.
  - Mention du périmètre, visible et pas en note de bas de page : la simulation
    comparée, son scénario et sa graine. Deux simulations de scénarios différents ne
    se comparent pas, l'écran le dit en une phrase.
  - Lien de téléchargement de l'export (les trois formats) et du corrigé depuis cet
    écran, avec l'empreinte affichée à côté de chaque format déjà téléchargé.
  - @PreAuthorize sur toute écriture, sec:authorize pour masquer les boutons, et les
    deux ensemble : jamais le masquage seul.
  - Refus métier en 422, comme le reste de l'application.
  - Aucune taille de police ni couleur en dur : tout par les tokens existants.

Critère de fin :
  - Tests MockMvc : l'écran répond 200 et contient les trois blocs ; un OBSERVATEUR
    voit l'écran mais aucun bouton d'écriture, et ses POST sont refusés.
  - Test : un run dont l'échange est en SILENCE affiche « en attente du script » et
    AUCUN pourcentage.
  - Test : le lien d'une anomalie ratée mène à l'écran Flux de données filtré sur
    cet event_id, qui affiche exactement une ligne.
  - Test : les libellés « Taux de rappel » et « Précision » sont présents.
  - ./mvnw clean verify est vert.
  - Vérification visuelle dans un navigateur, sur l'application lancée par podman :
    à faire, et à reporter dans le journal (les entrées précédentes la reportent
    depuis plusieurs briques, ne pas accumuler la dette).

À ne pas faire :
  - Ne pas changer le calcul du rappel, de la précision, des ratées ni des faux
    positifs. LigneQualite est juste et testé.
  - Ne pas créer de table de lignes exportées.
  - Ne pas ajouter de dépendance front, ne pas appeler de ressource externe.
  - Ne pas trier avec tri.js un tableau paginé (règle posée à la brique UI).
  - Ne pas toucher au moteur, aux étapes, aux mutators ni au chaos.

En fin de tâche :
  Proposer le message de commit, mettre à jour docs/journal.md, et dire explicitement
  si la vérification navigateur a été faite ou non.
```

**À vérifier toi-même**

- Ouvre `/qualite` : la planification, l'historique des analyses et le résultat sont là, dans cet ordre.
- Coupe le script et laisse passer une échéance : l'écran dit « en attente du script ».
- Clique une anomalie ratée : tu tombes sur la ligne exacte dans Flux de données.
- Vérifie en mode sombre et sous 720 px de large.

