# Prompt 13 - Export complet d'une simulation (brique Q1)

Première brique du module Qualité des données. Plan : `docs/07-qualite-donnees/04-plan.md`.

```text
# Prompt 13 - Export complet d'une simulation

Contexte :
  Lire CLAUDE.md, docs/journal.md, puis docs/07-qualite-donnees/04-plan.md (sections
  « Les deux décisions qui en découlent » et « Q1 »).
  Le jeu de données testé n'est pas fabriqué par un générateur dédié : c'est le flux
  que les simulations produisent déjà. L'exécution de simulation EST la campagne.

Objectif :
  Rendre téléchargeable, en xlsx, csv et json, tout ce qu'une simulation a produit,
  avec un marquage de donnée fictive sur chaque ligne et une empreinte qui prouve que
  deux téléchargements de la même simulation donnent le même fichier.

Emplacement :
  export/ (nouveau paquet : ExportService, JeuSimulation, TableExport, et un écrivain
  par format : EcrivainXlsx, EcrivainCsvZip, EcrivainJson),
  persistence/ (ExportFichier + repository), api/ExportController.java,
  src/main/resources/db/migration/V13__export.sql.

Contraintes :
  - Quatre tables dans l'export, dans cet ordre : assures, factures, facture_lignes,
    evenements. Les assurés sont ceux CONCERNÉS par la simulation (jointure depuis
    facture.assure_id), jamais les 50 000 du référentiel.
  - LE CORRIGÉ NE PART JAMAIS DANS CE FICHIER. anomaly_injected a son propre
    téléchargement (GET /export/corrige.csv), réservé par @PreAuthorize aux rôles
    ADMIN et OPERATEUR. Un script qui lirait le corrigé rendrait le score sans valeur.
  - Chaque ligne de chaque table porte son identifiant métier, et les lignes de la
    table evenements portent leur event_id : c'est la clé sur laquelle le rapport de
    l'outil qualité se raccroche (POST /api/runs/{id}/quality/report existe déjà et
    s'ancre sur event_id + type + champ). On ne crée AUCUNE table de lignes exportées.
  - Marquage systématique et NON DÉSACTIVABLE (cahier 2.2) : chaque ligne porte deux
    colonnes de tête, donnee_fictive = 'OUI' et simulation = la référence du run.
    Aucun paramètre, aucun réglage, aucun en-tête de requête ne les enlève.
  - Les trois formats s'écrivent EN FLUX dans la réponse, jamais en mémoire :
      . xlsx : Apache POI SXSSF, fenêtre de 100 lignes, un onglet par table ;
      . csv  : une archive zip, un .csv par table, BOM UTF-8 et échappement comme
               EvenementsCsv (le réutiliser, ne pas réécrire une deuxième règle) ;
      . json : un JsonGenerator Jackson, un objet racine avec un tableau par table.
    La lecture en base se fait aussi en flux (curseur ou pagination par clé), jamais
    un findAll() de 100 000 lignes.
  - Table export_fichier : id, run_id (REFERENCES simulation_run ON DELETE CASCADE),
    format, date_creation, nombre_lignes, empreinte CHAR(64) (SHA-256 du fichier).
    Une ligne par téléchargement. L'empreinte est calculée sur l'octet qui part.
  - Deux téléchargements de la même simulation dans le même format donnent la MÊME
    empreinte : l'ordre de lecture doit donc être totalement déterministe (trier sur
    une clé stable, jamais sur un timestamp réel seul).
  - Export refusé en 409 si la simulation n'est pas terminée : un run en cours
    donnerait un fichier différent à chaque appel.
  - Les endpoints d'API (GET /api/runs/{id}/export.{csv|json|xlsx}) passent par la
    clé d'API ; les téléchargements d'écran passent par la session. Aucune logique
    dans les contrôleurs.
  - Nouvelle dépendance : org.apache.poi:poi-ooxml, version fixée dans pom.xml.
    Signaler ce qu'elle tire comme dépendances transitives.

Critère de fin :
  - Test d'intégration (Testcontainers) : une simulation de 2 000 événements exportée
    dans les trois formats ; chaque fichier contient les quatre tables, le bon nombre
    de lignes, et chaque ligne porte donnee_fictive = OUI.
  - Test : deux exports successifs du même run dans le même format donnent la même
    empreinte SHA-256, et deux lignes dans export_fichier.
  - Test : le corrigé n'apparaît dans AUCUN des trois formats (chercher une valeur
    d'origine connue de anomaly_injected dans les octets du fichier).
  - Test : export d'un run EN_COURS refusé en 409 avec un code d'erreur stable.
  - Test : le xlsx s'ouvre réellement (relu par POI, onglets et en-têtes vérifiés).
  - Mesure à REPORTER dans le journal : mémoire et durée d'un export de 100 000
    événements en xlsx, sous mem_limit 2g. Mesurer, pas supposer.
  - ./mvnw clean verify est vert.

À ne pas faire :
  - Ne pas toucher au moteur, aux étapes, aux mutators, au chaos, ni à quality/.
  - Ne pas créer de table de campagne : simulation_run est la campagne.
  - Ne pas créer de table de lignes exportées.
  - Ne pas écrire de générateur en lot : le flux existant est le jeu testé.
  - Ne rien ajouter sur la planification ni sur le canal de retrait (prompts 14 et 15).

En fin de tâche :
  Proposer le message de commit, ajouter l'entrée dans docs/journal.md avec la mesure
  des 100 000 événements, et signaler toute hypothèse prise.
```

**À vérifier toi-même**

- Lance une simulation, laisse-la finir, télécharge les trois formats depuis l'écran.
- Ouvre le xlsx dans Excel : quatre onglets, et les deux premières colonnes de chaque onglet disent que c'est une donnée fictive.
- Télécharge deux fois le csv : l'écran affiche la même empreinte pour les deux.
- Télécharge le corrigé avec un compte OBSERVATEUR : refusé.

