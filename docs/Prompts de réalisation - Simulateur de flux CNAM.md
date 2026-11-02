# Prompts de réalisation - Simulateur de flux CNAM

Oct 29, 2026 · @Mathieu Charles N'GUESSAN

## Mode d'emploi

Le projet se construit en 12 briques, dans l'ordre. Chaque brique est un prompt à copier dans Claude Code, avec un résultat que tu peux vérifier toi-même avant de passer à la suivante.

**La boucle de travail, à répéter pour chaque brique**

1. **Préparer** : ouvrir la branche de la brique (`git switch -c step/03-facture`) et sauvegarder le prompt dans `prompts/03-facture.md`.
2. **Envoyer** : lancer `claude` à la racine du dépôt et coller le prompt.
3. **Lire** : regarder ce que Claude Code a créé ; pour les règles métier (calculs, droits), relire le code toi-même.
4. **Vérifier** : lancer `./mvnw clean verify`, puis les contrôles « À vérifier toi-même » de la brique.
5. **Sauvegarder** : `git add -A && git commit` avec le message proposé, puis fusionner la branche.
6. **Noter** : trois lignes dans `docs/journal.md` (date, fait, reste à faire).

**Règles**

- Une brique à la fois. Si la vérification échoue, on corrige avant de continuer ; on ne passe pas à la suite « pour voir ».
- Si Claude Code part dans une mauvaise direction : `git restore .` (ou retour au dernier commit) et reformule le prompt plus précisément.
- Pour les briques risquées (6, 7, 10, 11), commence en mode plan : Claude Code propose avant de modifier.
- Chaque prompt commence par lire `CLAUDE.md` : n'y recopie pas les conventions, elles s'appliquent déjà.
- Un prompt ne contient jamais de vrai mot de passe ni de vraie donnée.

## Modèle de prompt

Tous les prompts suivent le même moule en six rubriques. Garde-le pour tes propres prompts.

```text
# Prompt NN - Nom de la brique

Contexte :
  Lire CLAUDE.md, docs/journal.md et la section X du cahier des charges.

Objectif :
  Ce qui doit exister à la fin, en une ou deux phrases.

Emplacement :
  Les dossiers où le code doit être rangé.

Contraintes :
  Les règles à respecter (techniques, métier).

Critère de fin :
  Comment on sait que c'est réussi (tests, commande, résultat visible).

À ne pas faire :
  Ce qu'il ne doit pas toucher ou ajouter.

En fin de tâche :
  Résumer en 2 lignes, indiquer comment vérifier, proposer le message de commit,
  ajouter 3 lignes à docs/journal.md.
```

**Pourquoi ce moule** : le contexte évite qu'il invente, l'emplacement garde ton arborescence propre, le critère de fin rend la brique vérifiable, et la rubrique « À ne pas faire » empêche les débordements sur les briques suivantes.

## Brique 1 - Socle

Branche `step/01-socle` · prompt à sauvegarder dans `prompts/01-socle.md` · commit : `feat(socle): projet, conteneurs et migration initiale`

```text
# Prompt 01 - Socle

Contexte :
  Lire CLAUDE.md (sections 2, 7, 8, 9) et docs/00-cahier-des-charges.md (sections 11 et 13).

Objectif :
  Créer le squelette du projet : une application Spring Boot 3 / Java 21 qui démarre
  en conteneur avec PostgreSQL et Toxiproxy, et répond sur /simulateur/actuator/health.

Emplacement :
  Racine du dépôt : pom.xml + mvnw, Containerfile, compose.yaml, compose.override.yaml,
  .env.example, .gitignore, docker/toxiproxy.json, src/main/java/ci/cnam/simulator/,
  src/main/resources/application.yml et db/migration/.

Contraintes :
  - Maven, groupId ci.cnam, artifactId flow-simulator, package ci.cnam.simulator.
  - Dépendances : spring-boot-starter-web, actuator, jdbc, validation, postgresql,
    flyway-core et flyway-database-postgresql, lombok ; tests : spring-boot-starter-test,
    testcontainers (junit-jupiter, postgresql).
  - application.yml : server.servlet.context-path=/simulateur,
    server.forward-headers-strategy=framework ; datasource lue dans les variables
    d'environnement DB_URL, DB_USER, DB_PASSWORD ; Flyway activé.
  - Containerfile multi-étapes : construction Maven Temurin 21, exécution JRE 21 alpine,
    utilisateur non-root, couches Spring Boot.
  - compose.yaml : services postgres:16-alpine (volume, healthcheck), toxiproxy (config
    docker/toxiproxy.json, proxy postgres écoute 5432 vers postgres:5432) et simulator
    (DB_URL pointe sur toxiproxy:5432). Noms d'images complets, volumes avec :Z,
    restart: unless-stopped, limites mem_limit et cpus sur le simulateur.
  - compose.override.yaml (développement) : expose PostgreSQL sur 127.0.0.1:5432 seulement,
    pour pouvoir le consulter avec DBeaver.
  - .env.example avec DB_USER, DB_PASSWORD, API_KEY, ADMIN_INITIAL_PASSWORD (valeurs factices) ;
    .env et target/ dans .gitignore.
  - Migration V1__init.sql : une table technique info_application (cle, valeur).
  - Un test d'intégration Testcontainers qui démarre le contexte Spring sur un vrai PostgreSQL.

Critère de fin :
  - ./mvnw clean verify est vert.
  - podman compose up -d --build démarre les 3 conteneurs.
  - curl localhost:8080/simulateur/actuator/health renvoie {"status":"UP"}.
  - La table flyway_schema_history contient V1.

À ne pas faire :
  Aucun code métier, aucune sécurité, ni Kafka, Prometheus ou Grafana.

En fin de tâche :
  Résumer en 2 lignes, donner les commandes de vérification, proposer le message de commit,
  créer docs/journal.md avec la première entrée.
```

**À vérifier toi-même**

- `podman compose ps` : postgres, toxiproxy et simulator sont démarrés (postgres en bonne santé).
- La page de santé répond UP.
- Dans DBeaver (127.0.0.1:5432), la table `flyway_schema_history` existe.
- `git status` ne montre aucun fichier `.env`.

## Brique 2 - Référentiel synthétique

Branche `step/02-referentiel` · prompt dans `prompts/02-referentiel.md` · commit : `feat(reference): tables et générateur de jeu synthétique`

```text
# Prompt 02 - Référentiel synthétique

Contexte :
  Lire CLAUDE.md et docs/00-cahier-des-charges.md (sections 4 et 12).

Objectif :
  Créer les tables de référence et un générateur qui remplit la base d'un jeu
  entièrement fictif : assurés, ayants droit, établissements, médecins, pharmacies,
  actes, examens, médicaments et tarifs.

Emplacement :
  src/main/resources/db/migration/V2__reference.sql et V3__catalogues.sql ;
  src/main/java/ci/cnam/simulator/domain/reference/ (model/, catalog/, seed/,
  ReferenceService.java) ; core/random/SeededRandom.java.

Contraintes :
  - Tables en français, snake_case, identifiants UUID, clés étrangères et contraintes
    CHECK sur les valeurs fixes (genre M ou F, statut_cotisation A_JOUR ou EN_RETARD).
  - assure : numero_assure unique de 10 chiffres avec clé de contrôle (modulo 97), nom,
    prenoms, date_naissance, genre, statut_cotisation, date_debut_droits, date_fin_droits.
  - centre_sante avec un type (hôpital, clinique, centre de santé, CMR) ; medecin avec
    specialite ; pharmacie ; acte, examen et medicament avec code, libelle et tarif.
  - Créer SeededRandom (un seul point d'aléa, piloté par une graine) ; interdire
    Math.random() et new Random() ailleurs.
  - ReferenceSeeder : volume réglé par simulator.reference.nb-assures (défaut 50000) et
    simulator.reference.seed ; il ne s'exécute que si la table assure est vide ;
    insertion par lots. Même graine = mêmes données.
  - Noms, prénoms et libellés générés avec Datafaker (locale française) : tout est fictif.
  - Une méthode ReferenceService pour tirer un assuré, un établissement, un médecin,
    une pharmacie au hasard avec SeededRandom.

Critère de fin :
  - Tests : numéro d'assuré toujours valide et unique, même graine = mêmes 100 premiers assurés.
  - Au démarrage, la base contient le nombre d'assurés configuré.
  - ./mvnw clean verify est vert.

À ne pas faire :
  Pas de facture, pas de parcours, pas d'affiliation ; ne pas modifier V1.

En fin de tâche :
  Résumer, donner les commandes de vérification, proposer le message de commit,
  mettre à jour docs/journal.md.
```

**À vérifier toi-même**

- `SELECT count(*) FROM assure;` donne le volume attendu ; `SELECT * FROM assure LIMIT 20;` ne montre que des données fictives.
- Redémarre l'application : le nombre d'assurés ne double pas.
- `podman compose down -v` puis `up -d` : la base se reconstruit à l'identique.

## Brique 3 - Facture et états

Branche `step/03-facture` · prompt dans `prompts/03-facture.md` · commit : `feat(prestations): facture et machine à états`

```text
# Prompt 03 - Facture et états

Contexte :
  Lire CLAUDE.md (sections 4, 6, 7) et docs/00-cahier-des-charges.md (section 4).

Objectif :
  Créer la facture (feuille de soins), ses lignes, son historique et sa machine à états,
  avec uniquement les transitions autorisées.

Emplacement :
  src/main/resources/db/migration/V4__facture.sql ;
  src/main/java/ci/cnam/simulator/domain/prestations/facture/
  (Facture, FactureLigne, FactureStatut, TypeBon, FactureService).

Contraintes :
  - Tables facture, facture_ligne, facture_historique. facture : numero_facture lisible
    (FAC-AAAA-000123, issu d'une séquence), assure_id, beneficiaire_id (assuré ou ayant droit),
    centre_sante_id, type_bon, statut, date_ouverture et date_finalisation en temps SIMULÉ,
    montant_total, part_cnam, part_assurance_complementaire, part_assure en NUMERIC(14,2).
  - TypeBon : AMBULATOIRE, HOSPITALISATION, BIOLOGIE_IMAGERIE, DENTAIRE.
  - FactureStatut : OUVERTE, EN_COURS, PRESCRITE, MEDICAMENTS_DELIVRES, FINALISEE,
    REFUSEE, ABANDONNEE. Transitions autorisées : OUVERTE vers EN_COURS ; EN_COURS vers
    PRESCRITE ; PRESCRITE vers MEDICAMENTS_DELIVRES ; MEDICAMENTS_DELIVRES vers FINALISEE ;
    abandon possible depuis OUVERTE, EN_COURS ou PRESCRITE ; REFUSEE créée directement.
    Toute autre transition lève une exception métier claire.
  - facture_historique enregistre chaque transition (ancien statut, nouveau statut,
    horodatage simulé, horodatage réel).
  - Colonne run_id UUID sans clé étrangère pour l'instant (la contrainte vers simulation_run
    sera ajoutée à la brique 6). Constraint CHECK sur statut et type_bon.
  - Aucune logique métier dans les entités persistantes autre que la validation de transition.

Critère de fin :
  - Tests unitaires de toutes les transitions autorisées et d'au moins 5 transitions interdites.
  - Test d'intégration : ouvrir, ajouter une ligne, enchaîner jusqu'à FINALISEE ; l'historique
    contient chaque transition.
  - ./mvnw clean verify est vert.

À ne pas faire :
  Pas de parcours ni d'étapes, pas de calcul de tarif, pas de moteur.

En fin de tâche :
  Résumer, proposer le message de commit, mettre à jour docs/journal.md.
```

**À vérifier toi-même**

- Lis `FactureStatut` : les transitions correspondent au cahier des charges ?
- Dans DBeaver, après le test d'intégration, `facture_historique` montre la chaîne de statuts.
- Une insertion SQL avec un statut inventé est refusée par la base.

## Brique 4 - Étapes du parcours

Branche `step/04-parcours` · prompt dans `prompts/04-parcours.md` · commit : `feat(prestations): étapes et orchestration du parcours`

À la fin de cette brique, un parcours complet tourne déjà. C'est la brique la plus importante pour te repérer dans le métier : relis son code.

```text
# Prompt 04 - Étapes du parcours

Contexte :
  Lire CLAUDE.md (sections 3, 4, 5) et docs/00-cahier-des-charges.md (section 4).

Objectif :
  Faire parcourir à un patient fictif toutes les étapes, de l'arrivée à la facture
  finalisée (ou refusée), en produisant un événement par étape.

Emplacement :
  domain/prestations/etapes/ (une classe par étape),
  domain/prestations/parcours/ (ParcoursOrchestrator, ParcoursEtape, ParcoursGraph,
  ParcoursContext), domain/prestations/droits/ (ControleDroitsService, ResultatDroits),
  domain/prestations/events/PrestationEventType.java ;
  core/event/SimEvent.java (record minimal, seul ajout autorisé dans core/).

Contraintes :
  - Interface Etape : un résultat (facture mise à jour + événement émis) à partir d'un
    ParcoursContext. Étapes : VerificationDroits, OuvertureFacture, Consultation, Examens,
    Hospitalisation, Prescription, Pharmacie, Finalisation.
  - ControleDroitsService : droits ouverts à la date de soin ET statut_cotisation A_JOUR ;
    sinon ACCES_REFUSE, facture REFUSEE, fin du parcours.
  - ParcoursGraph : après la consultation, voie examens, hospitalisation ou sans examen,
    avec des probabilités lues dans la configuration (défaut 60 / 10 / 30), tirées avec SeededRandom.
  - Le type de bon de la facture est choisi selon le parcours (ambulatoire par défaut,
    hospitalisation si séjour, biologie/imagerie si examens). Règle simplifiée, à signaler
    en commentaire comme à valider.
  - SimEvent : eventId, runId, domaine, type, timestampSimule, payload (Map), anomalie (null).
    Les événements sont envoyés à une interface EventSink ; fournir une implémentation en
    mémoire pour les tests. Le temps vient d'un java.time.Clock injecté (jamais Instant.now()).
  - Chaque étape ajoute ses lignes à la facture ; les montants sont des valeurs fictives
    simples pour l'instant (la tarification arrive à la brique 5).

Critère de fin :
  - Test : un patient aux droits valides finit en FINALISEE avec la suite d'événements attendue.
  - Test : droits fermés ou cotisations en retard donnent REFUSEE et ACCES_REFUSE.
  - Test : sur 10000 parcours avec une graine fixe, les voies respectent 60/10/30 à 3 points près.
  - Test : même graine donne exactement les mêmes événements.
  - ./mvnw clean verify est vert.

À ne pas faire :
  Pas de moteur, pas de scénario YAML, pas d'anomalies, pas de calcul de taux, pas d'API.

En fin de tâche :
  Afficher dans le résumé la trace d'un parcours complet (liste des événements),
  proposer le message de commit, mettre à jour docs/journal.md.
```

**À vérifier toi-même**

- La trace d'un parcours suit bien les 9 étapes du cahier des charges.
- Relis `ControleDroitsService` : c'est ta règle métier, elle doit te paraître juste.
- Change la graine : le parcours change ; remets-la : il redevient identique.

## Brique 5 - Tarification

Branche `step/05-tarification` · prompt dans `prompts/05-tarification.md` · commit : `feat(prestations): tarification et taux configurables`

Les taux et tarifs sont fixés par les directions métier : ils sont ici des valeurs fictives, mais jamais écrites en dur dans le code.

```text
# Prompt 05 - Tarification

Contexte :
  Lire CLAUDE.md (sections 4, 6) et docs/00-cahier-des-charges.md (sections 4 et 15).

Objectif :
  Calculer pour chaque ligne et pour la facture : montant total, part CNAM (CMU),
  part de l'assurance complémentaire et part de l'assuré, avec des taux configurables.

Emplacement :
  domain/prestations/tarification/ (TarificationService, RegleTaux) ;
  src/main/resources/db/migration/V5__taux.sql (table taux) ;
  intégration dans domain/prestations/facture/FactureService.

Contraintes :
  - BigDecimal uniquement, échelle 2, arrondi HALF_UP.
  - Invariant : part CNAM + part assurance complémentaire + part assuré = montant total,
    à l'arrondi près : le reliquat d'arrondi est affecté à la part de l'assuré.
  - RegleTaux : un taux de prise en charge par type de prestation (consultation, examen,
    hospitalisation, médicament, dentaire) et un taux d'assurance complémentaire, lus dans
    la table taux ; valeurs fictives insérées par la migration, modifiables sans recompiler.
  - Le tarif d'un acte, d'un examen ou d'un médicament vient du catalogue du référentiel.
  - Les étapes du parcours appellent TarificationService au lieu des montants fictifs.
  - Aucune valeur de taux dans le code Java.

Critère de fin :
  - Tests paramétrés : au moins 15 cas chiffrés, dont des arrondis délicats (montant 100,01 ;
    taux 70 %), montant nul, quantité supérieure à 1.
  - Test : l'invariant des trois parts est vrai sur 1000 lignes aléatoires (graine fixe).
  - Les tests de la brique 4 passent toujours.
  - ./mvnw clean verify est vert.

À ne pas faire :
  Pas de moteur, pas d'anomalies, pas d'API ; ne pas toucher aux transitions de statut.

En fin de tâche :
  Lister les taux fictifs créés, proposer le message de commit, mettre à jour docs/journal.md.
```

**À vérifier toi-même**

- Prends trois lignes d'une facture et refais le calcul à la main.
- Change un taux dans la table `taux` : les nouvelles factures suivent, sans recompilation.
- Les taux fictifs sont documentés dans `docs/journal.md`, avec la mention « à remplacer par les valeurs des directions métier ».

## Brique 6 - Moteur de simulation

Branche `step/06-moteur` · prompt dans `prompts/06-moteur.md` · commit : `feat(core): moteur, horloge virtuelle et scénarios YAML`

Brique risquée : démarre en mode plan et relis le plan avant d'autoriser les modifications.

```text
# Prompt 06 - Moteur de simulation

Contexte :
  Lire CLAUDE.md (sections 3, 4, 12) et docs/00-cahier-des-charges.md (section 5).

Objectif :
  Piloter des parcours à un débit donné, sur une horloge virtuelle accélérable, à partir
  d'un scénario YAML, avec démarrage, pause, reprise et arrêt.

Emplacement :
  core/engine/ (SimulationEngine, RunManager, VirtualClock), core/scenario/ (Scenario,
  Phase, ScenarioLoader), domain/FlowGenerator.java, domain/prestations/PrestationsGenerator.java,
  src/main/resources/db/migration/V6__simulation.sql, scenarios/prestations/parcours-normal.yaml.

Contraintes :
  - VirtualClock : accélération x1, x10, x100, pause, reprise ; elle fournit le java.time.Clock
    utilisé par les étapes. Le temps réel et le temps simulé restent deux notions distinctes.
  - Scénario YAML (Jackson YAML) : nom, seed, accelerationTemps, domaines.prestations.debit
    (par seconde), probabilités du parcours, phases (de, a, charge). Validation à la lecture avec
    des messages d'erreur clairs (champ manquant, taux hors de 0 à 100, durée négative).
  - FlowGenerator : interface commune ; PrestationsGenerator l'implémente. Le moteur ne doit
    jamais importer le package domain/prestations.
  - RunManager : états CREE, EN_COURS, EN_PAUSE, ARRETE, ECHOUE ; un seul run actif à la fois
    pour cette version.
  - V6 : tables scenario (nom, contenu YAML) et simulation_run (scenario, seed, début, fin,
    statut, paramètres), puis ajout de la clé étrangère facture.run_id vers simulation_run
    avec ON DELETE CASCADE.
  - Sortie provisoire : les événements vont dans l'EventSink en mémoire ; l'écriture en base
    arrive à la brique 7.

Critère de fin :
  - Test : un run démarre, produit des événements au débit demandé (à 10 % près),
    se met en pause (plus d'événements), reprend, puis s'arrête proprement.
  - Test : même scénario et même graine donnent la même suite d'événements.
  - Test : un YAML invalide est refusé avec un message précis.
  - Test : à x100, 1 minute réelle couvre 100 minutes simulées.
  - ./mvnw clean verify est vert.

À ne pas faire :
  Pas d'API, pas d'anomalies, pas de chaos, pas d'écriture optimisée en base.

En fin de tâche :
  Résumer, proposer le message de commit, mettre à jour docs/journal.md.
```

**À vérifier toi-même**

- Lance un run court à partir du YAML et observe le nombre d'événements par seconde dans les journaux.
- Mets en pause, reprends, arrête : le statut du run change à chaque fois dans `simulation_run`.
- `grep -r "import ci.cnam.simulator.domain" src/main/java/ci/cnam/simulator/core` ne renvoie rien.

## Brique 7 - Écriture en base par lots

Branche `step/07-ecriture` · prompt dans `prompts/07-ecriture.md` · commit : `feat(sink): écriture par lots, file bornée et checkpoint`

Brique risquée : mode plan recommandé.

```text
# Prompt 07 - Écriture en base par lots

Contexte :
  Lire CLAUDE.md (sections 3, 6, 7) et docs/00-cahier-des-charges.md (sections 5, 7, 10, 12).

Objectif :
  Écrire les événements et les factures en base à haut débit, sans perte ni doublon,
  avec reprise après un arrêt brutal.

Emplacement :
  sink/ (EventSink, DatabaseSink), monitoring/MetricsService.java,
  src/main/resources/db/migration/V7__event_checkpoint.sql.

Contraintes :
  - Table event : event_id UUID clé primaire, run_id, facture_id, domaine, type,
    timestamp_simule, timestamp_reel, payload JSONB, statut. Table checkpoint : run_id,
    dernier événement écrit, compteur, date.
  - DatabaseSink écrit par lots JDBC (taille de lot et délai maximal d'attente configurables),
    avec INSERT ... ON CONFLICT (event_id) DO NOTHING : rejouer un lot ne crée aucun doublon.
  - File bornée (BlockingQueue de capacité configurable) entre le générateur et l'écriture ;
    quand elle est pleine, le générateur ralentit (contre-pression) au lieu de saturer la mémoire.
  - Chaque lot est écrit dans une seule transaction avec la mise à jour du checkpoint.
  - Au redémarrage d'un run interrompu, reprise après le dernier checkpoint.
  - Métriques Micrometer : événements écrits par seconde, taille de la file, durée des lots.

Critère de fin :
  - Test : 100000 événements écrits ; le débit est mesuré et noté dans docs/journal.md.
  - Test : une exception injectée au milieu d'un lot annule le lot entier (aucune ligne partielle).
  - Test : après un arrêt simulé et une reprise, le nombre d'événements est exact, sans doublon.
  - Test : une file volontairement lente fait ralentir le générateur sans erreur mémoire.
  - ./mvnw clean verify est vert.

À ne pas faire :
  Pas de Toxiproxy ni de chaos réseau (brique 10), pas d'API, pas d'anomalies.

En fin de tâche :
  Donner le débit mesuré (événements par seconde), proposer le message de commit,
  mettre à jour docs/journal.md.
```

**À vérifier toi-même**

- `SELECT count(*), count(DISTINCT event_id) FROM event;` donne deux nombres identiques.
- Arrête brutalement le conteneur du simulateur en plein run (`podman compose kill -s SIGKILL simulator`), redémarre-le : le compteur repart du checkpoint, sans doublon.
- Le débit mesuré est noté ; il servira de référence pour la brique 10.

## Brique 8 - API REST

Branche `step/08-api` · prompt dans `prompts/08-api.md` · commit : `feat(api): routes de pilotage et de lecture, Swagger, clé d'API`

```text
# Prompt 08 - API REST

Contexte :
  Lire CLAUDE.md (sections 3, 6, 10) et docs/00-cahier-des-charges.md (section 8).

Objectif :
  Exposer les routes qui permettent de lancer et piloter une simulation et de lire ses
  événements, protégées par une clé d'API, avec une documentation Swagger.

Emplacement :
  api/ (RunController, EventController, ScenarioController, dto/),
  security/ApiKeyFilter.java et sa configuration minimale.

Contraintes :
  - Routes : POST /api/runs ; POST /api/runs/{id}/pause, /resume, /stop ;
    GET /api/runs et /api/runs/{id} ; GET /api/runs/{id}/events (filtres domaine, type,
    statut ; pagination) ; GET et POST /api/scenarios.
  - Contrôleurs fins : aucune logique métier, ils appellent des services. DTO séparés des
    entités ; erreurs au format JSON homogène (code, message) ; validation des entrées.
  - Clé d'API lue dans la variable d'environnement API_KEY, en-tête X-API-Key, comparaison à
    temps constant ; 401 sans clé ou avec une mauvaise clé. La sécurité complète (formulaire de
    connexion, rôles) viendra à la brique 11 : ici uniquement /api/**.
  - springdoc-openapi : Swagger accessible sous /simulateur/swagger-ui.html, avec le schéma
    de la clé d'API déclaré pour pouvoir tester depuis Swagger.
  - Pagination bornée (taille maximale de page).

Critère de fin :
  - Tests MockMvc : lancer, mettre en pause, reprendre, arrêter ; 401 sans clé ; 400 sur un
    scénario invalide ; pagination et filtres des événements.
  - Swagger affiche toutes les routes.
  - ./mvnw clean verify est vert.

À ne pas faire :
  Pas d'interface web, pas de rôles utilisateur, pas d'anomalies, pas de chaos.

En fin de tâche :
  Donner un exemple curl pour chaque route, proposer le message de commit,
  mettre à jour docs/journal.md.
```

**À vérifier toi-même**

- Ouvre Swagger, renseigne la clé, lance un run, lis ses événements.
- Sans clé, la même requête renvoie 401.
- Un collègue de l'équipe qualité peut appeler l'API avec `curl` en suivant les exemples.

## Brique 9 - Anomalies

Branche `step/09-anomalies` · prompt dans `prompts/09-anomalies.md` · commit : `feat(anomaly): mutators, injection reproductible et vérité terrain`

```text
# Prompt 09 - Anomalies

Contexte :
  Lire CLAUDE.md (sections 3, 5, 6) et docs/00-cahier-des-charges.md (section 6).

Objectif :
  Corrompre volontairement une proportion réglable des événements avec des anomalies simples,
  et enregistrer pour chacune la valeur d'origine (vérité terrain).

Emplacement :
  anomaly/ (AnomalyInjector, AnomalyType, AnomalyRecord, config/AnomalyProfile),
  anomaly/mutators/ (interface Mutator + une classe par type),
  domain/prestations/anomalies/PrestationAnomalyProfile.java,
  src/main/resources/db/migration/V8__anomalies.sql, api/AnomalyController.java.

Contraintes :
  - Types : CHAMP_VIDE, FAUTE_ORTHOGRAPHE, ESPACES_PARASITES, CASSE_INCOHERENTE,
    CARACTERES_SPECIAUX, FORMAT_DATE_INVALIDE, FORMAT_NUMERIQUE_INVALIDE,
    FORMAT_IDENTIFIANT_INVALIDE, VALEUR_NEGATIVE, VALEUR_HORS_LISTE, DOUBLON_EXACT.
  - Faute d'orthographe : suppression, doublement, permutation de deux lettres ou lettre
    voisine du clavier. Champ vide : au hasard null, chaîne vide ou espaces. Date invalide :
    jour impossible, mois supérieur à 12, texte libre, mauvais format.
  - Le générateur produit toujours un événement propre ; AnomalyInjector décide, avec
    SeededRandom et les taux du scénario (section anomalies par domaine), quoi corrompre.
  - PrestationAnomalyProfile déclare quels champs de la feuille de soins sont éligibles à quel
    type ; priorité aux champs saisis à la main par l'agent (noms, prénoms, observations, codes).
  - Table anomaly_injected : run_id, event_id, facture_id, type, champ, valeur_origine,
    valeur_corrompue. DOUBLON_EXACT : le doublon reçoit un nouvel event_id et référence
    l'original (colonne doublon_de) ; la contrainte d'unicité de event_id reste intacte.
  - GET /api/runs/{id}/anomalies avec filtres par type et par champ, paginé.
  - Un taux global de 0 % ne produit aucune anomalie.

Critère de fin :
  - Un test par mutator, avec entrées et sorties exactes.
  - Test : même graine, mêmes anomalies aux mêmes endroits ; taux 0 % = aucune ligne.
  - Test : sur 10000 événements avec un taux de 3 %, le nombre d'anomalies est proche de 300.
  - Test : chaque ligne de anomaly_injected permet de retrouver la valeur propre d'origine.
  - ./mvnw clean verify est vert.

À ne pas faire :
  Pas de chaos, pas de rapport qualité, pas de modification du moteur ni des étapes
  hors injection de la configuration.

En fin de tâche :
  Montrer 5 exemples avant/après, proposer le message de commit, mettre à jour docs/journal.md.
```

**À vérifier toi-même**

- Lance un run à 3 % de champ vide : `SELECT type, count(*) FROM anomaly_injected GROUP BY type;` donne des volumes cohérents avec les taux.
- Relis les 5 exemples avant/après : sont-ils réalistes pour un agent de saisie ?
- Relance avec la même graine : les mêmes événements sont corrompus de la même façon.

## Brique 10 - Chaos et crash tests

Branche `step/10-chaos` · prompt dans `prompts/10-chaos.md` · commit : `feat(chaos): surcharge, Toxiproxy et tests de reprise`

Brique risquée : mode plan recommandé. À lancer uniquement sur ta machine ou sur un environnement dédié au simulateur.

```text
# Prompt 10 - Chaos et crash tests

Contexte :
  Lire CLAUDE.md (sections 3, 7, 8, 10) et docs/00-cahier-des-charges.md (section 7).

Objectif :
  Pouvoir casser volontairement l'environnement pendant un run (coupure de la base, latence,
  surcharge) et prouver que le système se rétablit sans perte ni doublon.

Emplacement :
  chaos/ (ChaosInjector, ChaosType, LoadMultiplier, ToxiproxyClient),
  api/ChaosController.java, src/main/resources/db/migration/V9__chaos.sql,
  scripts/chaos/ (scripts shell d'arrêt brutal), docs/04-chaos.md.

Contraintes :
  - ToxiproxyClient appelle l'API de Toxiproxy (variable TOXIPROXY_URL) : désactiver le proxy,
    réinitialiser la connexion (reset_peer), ajouter de la latence, supprimer les toxics.
  - LoadMultiplier : multiplie le débit (x5, x20, x100) ; la file bornée de la brique 7 doit
    absorber la charge par contre-pression.
  - ChaosType : COUPURE_BASE, LATENCE, SURCHARGE, MESSAGE_TRONQUE. Table chaos_action :
    run_id, type, paramètres, début, fin, auteur.
  - Routes : POST /api/runs/{id}/chaos (type, durée, paramètres) ; DELETE /api/runs/{id}/chaos
    (tout rétablir immédiatement). Une durée maximale est imposée à chaque action.
  - Les arrêts brutaux de conteneurs se font par scripts depuis l'extérieur (podman compose kill) :
    ne jamais monter le socket du moteur de conteneurs dans le simulateur.
  - Le simulateur reste connecté uniquement à sa propre base.

Critère de fin :
  - Test d'intégration avec Testcontainers (PostgreSQL + Toxiproxy) : coupure de 30 secondes
    en plein lot ; après rétablissement, aucun événement perdu ni dupliqué, aucune facture
    à moitié écrite.
  - Test : latence de 500 ms ; le débit baisse, aucune erreur fatale.
  - Test : surcharge x20 ; la file se remplit, le générateur ralentit, le service reste vivant.
  - docs/04-chaos.md décrit chaque scénario, les commandes, et les résultats mesurés
    (point de rupture, temps de reprise).
  - ./mvnw clean verify est vert.

À ne pas faire :
  Pas d'interface (brique 11), pas de rapport qualité, pas de modification du modèle d'événement.

En fin de tâche :
  Donner le point de rupture mesuré, proposer le message de commit, mettre à jour docs/journal.md.
```

**À vérifier toi-même**

- Pendant un run, coupe la base 30 secondes depuis l'API : le débit tombe à zéro, puis repart seul.
- Compare `SELECT count(*), count(DISTINCT event_id) FROM event;` avant et après : aucun doublon.
- `podman compose kill -s SIGKILL simulator` puis redémarrage : le run reprend au checkpoint.

## Brique 11 - Sécurité et interface

Cette brique est grosse : elle se fait en **deux prompts**, avec un commit chacun. Branche `step/11-securite-interface`.

### 11a - Sécurité et comptes

Commit : `feat(security): connexion, rôles, audit`

```text
# Prompt 11a - Sécurité et comptes

Contexte :
  Lire CLAUDE.md (sections 9, 10) et docs/00-cahier-des-charges.md (sections 3 et 9).

Objectif :
  Protéger l'interface par une page de connexion et des rôles, en gardant l'API protégée
  par clé d'API, avec un journal d'audit.

Emplacement :
  security/ (SecurityConfig, AppUserDetailsService, LoginAttemptService),
  persistence/entity (AppUser, ApiKey, AuditLog), web/LoginController.java,
  src/main/resources/db/migration/V10__security.sql, templates/login.html.

Contraintes :
  - Deux chaînes Spring Security : /api/** (clé d'API, sans session, CSRF désactivé) et le reste
    (formulaire, session, CSRF actif). Le contexte /simulateur doit fonctionner partout.
  - Rôles ADMIN, OPERATEUR, OBSERVATEUR ; droits vérifiés côté serveur avec @PreAuthorize.
  - BCrypt ; verrouillage temporaire après 5 échecs ; session de 30 minutes ; cookies Secure et
    HttpOnly ; message d'erreur de connexion générique.
  - Tables app_user, api_key (clé stockée hachée) et audit_log (qui, quoi, quand).
  - Premier compte ADMIN créé au démarrage depuis ADMIN_INITIAL_PASSWORD, changement de mot de
    passe obligatoire à la première connexion ; aucun mot de passe dans le code ni les journaux.
  - Audit : lancement, arrêt, action de chaos, création d'utilisateur, changement de rôle.

Critère de fin :
  - Tests : accès refusé sans connexion, rôle OBSERVATEUR sans droit d'écriture, verrouillage
    après 5 échecs, CSRF actif sur le formulaire, API inchangée avec la clé.
  - ./mvnw clean verify est vert.

À ne pas faire :
  Pas d'écrans autres que la page de connexion et un écran vide d'accueil.
```

### 11b - Écrans et boutons

Commit : `feat(web): tableau de bord, pilotage, chaos, événements`

```text
# Prompt 11b - Écrans et boutons

Contexte :
  Lire CLAUDE.md et docs/00-cahier-des-charges.md (section 9).

Objectif :
  Une interface web légère pour piloter le simulateur sans ligne de commande.

Emplacement :
  web/ (contrôleurs), src/main/resources/templates/ (layout.html, dashboard.html,
  run-launch.html, scenario-editor.html, chaos-panel.html, events.html, history.html,
  admin-users.html), static/ (htmx, chart.js, css), monitoring/SseController.java.

Contraintes :
  - Thymeleaf + HTMX + Chart.js, sans framework JavaScript. Liens toujours avec th:href="@{...}".
  - Tableau de bord en direct (flux SSE) : débit par domaine, erreurs, latence, état du
    simulateur, de la base et du proxy.
  - Lancement : choix du scénario, graine, vitesse du temps ; boutons démarrer, pause, reprendre,
    arrêter, rejouer avec la même graine.
  - Éditeur de scénarios : formulaire qui génère le YAML, validation, import et export.
  - Panneau chaos : couper la base, latence, surcharge, anomalies, tout rétablir ;
    confirmation (hx-confirm) sur chaque action ; boutons visibles seulement pour ADMIN et
    OPERATEUR (sec:authorize) en plus du contrôle serveur.
  - Explorateur d'événements : filtres, pagination, export CSV, rejeu d'un événement échoué.
  - Historique des runs ; administration des utilisateurs (ADMIN).
  - Aucun appel à l'extérieur : ressources JavaScript et CSS servies localement.

Critère de fin :
  - Tests MockMvc sur chaque contrôleur et sur les droits par rôle.
  - Parcours manuel décrit dans le résumé : connexion, lancement, chaos, rétablissement, export.
  - ./mvnw clean verify est vert.

À ne pas faire :
  Pas de rapport qualité (brique 12), pas de modification du moteur ni de l'API existante.
```

**À vérifier toi-même**

- Connecte-toi avec chaque rôle : un OBSERVATEUR ne voit ni ne peut déclencher le chaos (essaie aussi l'adresse directement).
- Derrière un reverse proxy simulé (`/simulateur/`), tous les liens et boutons fonctionnent.
- Le tableau de bord se met à jour seul pendant un run.

## Brique 12 - Qualité

Branche `step/12-qualite` · prompt dans `prompts/12-qualite.md` · commit : `feat(quality): rapport injectées contre détectées`

C'est la brique qui donne sa valeur au projet : elle mesure ce que l'outil de qualité détecte vraiment.

```text
# Prompt 12 - Qualité

Contexte :
  Lire CLAUDE.md et docs/00-cahier-des-charges.md (sections 6, 8 et 9).

Objectif :
  Recevoir les détections de l'outil de qualité des données, les comparer à la vérité terrain
  (anomalies injectées) et produire un rapport : détectées, ratées, faux positifs.

Emplacement :
  quality/ (QualityReportService, QualityClient), api/QualityController.java,
  src/main/resources/db/migration/V11__quality.sql, templates/quality-report.html.

Contraintes :
  - POST /api/runs/{id}/quality/report : liste de détections (event_id, champ, type d'anomalie,
    éventuellement une règle ou un score). Plusieurs envois pour un même run sont acceptés.
  - GET /api/runs/{id}/quality/comparison : pour chaque type d'anomalie, nombre injecté,
    détecté, raté (injecté non détecté) et faux positifs (détecté sans anomalie injectée) ;
    taux de détection et précision.
  - Le rapport sépare les anomalies structurelles (champ vide, format, valeur négative, hors
    liste) des sémantiques (orthographe, casse, caractères spéciaux), dont la détection est
    plus difficile.
  - Tables quality_report et quality_detection ; un rapport est rattaché à un run et à une
    version de l'outil qualité (texte libre).
  - Écran « Rapport qualité » : tableau par type, comparaison de deux runs, export CSV.
  - Un run à 0 % d'anomalie sert de référence : tout ce qui y est détecté est un faux positif.

Critère de fin :
  - Test avec un jeu connu : 100 anomalies injectées, 80 détections correctes, 20 ratées,
    10 faux positifs ; le rapport renvoie exactement ces chiffres, par type.
  - Test : envoi en deux fois, résultat identique à un envoi unique.
  - Test : détection sur un événement inconnu rejetée avec un message clair.
  - ./mvnw clean verify est vert.

À ne pas faire :
  Aucune modification du moteur, des étapes, des mutators ni du chaos.

En fin de tâche :
  Proposer le message de commit, mettre à jour docs/journal.md, et marquer la phase 1 terminée.
```

**À vérifier toi-même**

- Lance un run avec anomalies, envoie des détections fictives avec `curl`, ouvre l'écran : les chiffres correspondent à ce que tu as envoyé.
- Fais le test à blanc avec 0 % d'anomalie : le rapport affiche uniquement des faux positifs.
- Passe le rapport à l'équipe qualité pour qu'elle le branche sur son outil.

## Tableau de suivi et suite du projet

Coche chaque brique quand sa vérification est réussie et que le commit est fait.

- [ ] 1 Socle : l'application démarre en conteneur, santé UP
- [ ] 2 Référentiel : le jeu synthétique est en base
- [ ] 3 Facture : transitions testées
- [ ] 4 Étapes du parcours : un parcours complet tourne
- [ ] 5 Tarification : calculs vérifiés à la main
- [ ] 6 Moteur : lancer, pause, reprise, arrêt
- [ ] 7 Écriture en base : débit mesuré, reprise sans doublon
- [ ] 8 API : Swagger et clé d'API
- [ ] 9 Anomalies : vérité terrain enregistrée
- [ ] 10 Chaos : coupure en plein lot sans perte
- [ ] 11a Sécurité et comptes
- [ ] 11b Écrans et boutons
- [ ] 12 Qualité : rapport injectées contre détectées

**Modèle de ligne de journal** (`docs/journal.md`)

```text
2026-11-04 | Brique 3 - Facture | Fait : facture, lignes, états, 12 tests verts.
           | Reste : relier aux étapes (brique 4). | Commit : a1b2c3d
```

**Quand une brique coince**

1. Ne passe pas à la suivante.
2. Relance `./mvnw clean verify` et colle l'erreur complète à Claude Code, avec la consigne « corrige la cause, sans contourner le test ».
3. Si ça ne s'arrange pas en trois essais, reviens au dernier commit (`git restore .`) et reformule le prompt en le rendant plus précis, notamment le critère de fin.

**Phases suivantes**

- **Affiliation** : mêmes sous-dossiers que les prestations, un nouveau `FlowGenerator`, ses anomalies propres ; elle alimente le référentiel en vrais enrôlements. Reprends le moule des prompts 02 à 05 et 09.
- **Recouvrement** : déclarations, cotisations, relances ; il alimente le statut de cotisation lu par la vérification des droits.
- **Mise en service** sur le domaine existant : un prompt de déploiement (13) à écrire avec les informations relevées sur le serveur actuel.
