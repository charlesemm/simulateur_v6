# CLAUDE.md — Simulateur de flux de données CNAM

Ce fichier est relu à chaque session. Il décrit le projet, ses règles et sa méthode de travail.
Le cahier des charges complet est dans `docs/00-cahier-des-charges.md` (à exporter du document de cadrage).
**Avant toute tâche : lire ce fichier, puis `docs/journal.md` (où en est-on ?).**

## 1. But du projet

Application Spring Boot qui simule des flux de données pour la CNAM (Côte d'Ivoire), domaine par domaine :
**prestations (phase 1, en cours)**, puis affiliation, puis recouvrement.
Elle génère des événements synthétiques, les écrit en base, injecte volontairement des anomalies de qualité de données
(vérité terrain), subit des crash tests (coupure brutale, surcharge, latence) et expose une API aux applications internes,
surtout l'outil de qualité des données.

**Règle absolue : aucune vraie donnée.** Tout est synthétique (assurés, établissements, médecins, montants).
Jamais de connexion à une base de production de la CNAM.

## 2. Stack

- Java 21, Spring Boot 3, Maven (wrapper `./mvnw`, `.\mvnw.cmd` sous PowerShell)
- PostgreSQL 16, migrations **Flyway**, écriture en base **par lots** (JDBC batch)
- API : Spring Web + springdoc-openapi (Swagger)
- Interface : Thymeleaf + HTMX + Chart.js, temps réel par SSE
- Sécurité : Spring Security (formulaire pour l'interface, clé d'API `X-API-Key` pour `/api/**`)
- Données : Datafaker + règles métier propres, aléa piloté par la graine (seed)
- Chaos réseau : Toxiproxy entre l'application et PostgreSQL
- Métriques : Micrometer, Prometheus, Grafana (profil optionnel)
- Tests : JUnit 5, AssertJ, Testcontainers
- Conteneurs : **Podman rootless** (pas Docker)

## 3. Architecture : monolithe modulaire

Un seul déploiement, des modules séparés. Pas de microservices.

```
src/main/java/ci/cnam/simulator/
├── core/                      MOTEUR GÉNÉRIQUE (ne connaît pas le métier)
│   ├── engine/    SimulationEngine, RunManager, VirtualClock
│   ├── scenario/  Scenario, Phase, ScenarioLoader
│   ├── event/     SimEvent, EventBus, EventType
│   └── random/    SeededRandom
├── domain/
│   ├── FlowGenerator.java     interface commune à tous les domaines
│   ├── reference/             référentiel synthétique partagé
│   │   ├── model/    Assure, AyantDroit, CentreSante, Pharmacie, Medecin
│   │   ├── catalog/  Acte, Examen, Medicament, Tarif
│   │   ├── seed/     ReferenceSeeder
│   │   └── ReferenceService.java
│   ├── prestations/           ★ TOUT LE PARCOURS EST ICI
│   │   ├── PrestationsGenerator.java
│   │   ├── parcours/     ParcoursOrchestrator, ParcoursEtape, ParcoursGraph, ParcoursContext
│   │   ├── etapes/       Etape + UNE classe par étape (VerificationDroitsEtape, OuvertureFactureEtape,
│   │   │                 ConsultationEtape, ExamensEtape, HospitalisationEtape, PrescriptionEtape,
│   │   │                 PharmacieEtape, FinalisationEtape)
│   │   ├── facture/      Facture, FactureLigne, FactureStatut, FactureService
│   │   ├── droits/       ControleDroitsService, ResultatDroits
│   │   ├── tarification/ TarificationService, RegleTaux
│   │   ├── events/       PrestationEventType
│   │   └── anomalies/    déclaration des champs corruptibles du domaine
│   ├── affiliation/           plus tard (même sous-structure)
│   └── recouvrement/          plus tard (même sous-structure)
├── anomaly/                   mécanisme générique : AnomalyInjector, AnomalyType, AnomalyRecord
│   └── mutators/              UNE classe par type d'anomalie (interface Mutator)
├── chaos/        ChaosInjector, ChaosType, LoadMultiplier, ToxiproxyClient
├── sink/         EventSink, DatabaseSink (batch), CsvSink (optionnel)
├── persistence/  entity/, repository/
├── api/          contrôleurs REST + dto/
├── quality/      QualityReportService, QualityClient
├── monitoring/   MetricsService, SseController
├── security/     SecurityConfig, ApiKeyFilter, AppUserDetailsService, LoginAttemptService
└── web/          contrôleurs Thymeleaf
```

Ressources : `db/migration/` (scripts Flyway `V1__…sql`), `templates/` (Thymeleaf), `static/`.
Autres dossiers du dépôt : `scenarios/` (YAML), `docs/`, `prompts/`, `docker/` (config Toxiproxy, Prometheus).

### Règles de rangement (pour se retrouver)

- Comment une étape se déroule → `prestations/etapes/`
- Dans quel ordre → `prestations/parcours/`
- La facture → `prestations/facture/`
- Un calcul d'argent → `prestations/tarification/`
- Une anomalie → `anomaly/mutators/` (le mécanisme) et `prestations/anomalies/` (les champs visés)
- Le moteur ne doit **jamais** importer un domaine ; un domaine importe le moteur, pas l'inverse.
- Ajouter un domaine = écrire un `FlowGenerator` de plus, sans toucher à `core/`.

## 4. Le métier : parcours d'une prestation

La **facture (feuille de soins)** est l'unité centrale. Parcours :

1. Arrivée du patient
2. Vérification des droits (droits ouverts, cotisations à jour) → sinon `ACCES_REFUSE`, facture `REFUSEE`, fin
3. **Ouverture de la facture** (`OUVERTE`)
4. Consultation (`EN_COURS`)
5. Examens (optionnel) / 6. Hospitalisation (optionnel) — probabilités réglables (départ : 60 % / 10 % / 30 % direct)
7. Retour médecin et prescription (`PRESCRITE`)
8. Pharmacie : délivrance (`MEDICAMENTS_DELIVRES`)
9. Finalisation (`FINALISEE`)

Sorties anticipées : `REFUSEE`, `ABANDONNEE`. **Seules les transitions autorisées sont acceptées** (machine à états dans `FactureStatut`).
Chaque étape émet un événement portant le `factureId` : `PATIENT_ARRIVE`, `DROITS_VERIFIES`, `ACCES_REFUSE`,
`FACTURE_OUVERTE`, `CONSULTATION_REALISEE`, `EXAMEN_REALISE`, `HOSPITALISATION_REALISEE`, `PRESCRIPTION_EMISE`,
`MEDICAMENTS_DELIVRES`, `FACTURE_FINALISEE`.

Quatre types de bons de prise en charge : ambulatoire/médicaments (ER-01-GPR), hospitalisation (ER-02-GPR),
biologie/imagerie (ER-03-GPR), dentaire. La facture est rattachée à l'un de ces quatre types.
Les taux de prise en charge et les tarifs sont **configurables** (jamais en dur) : ce sont les directions métier qui les fixent.

## 5. Anomalies de qualité de données

Anomalies **simples** : `CHAMP_VIDE`, `FAUTE_ORTHOGRAPHE`, `ESPACES_PARASITES`, `CASSE_INCOHERENTE`,
`CARACTERES_SPECIAUX`, `FORMAT_DATE_INVALIDE`, `FORMAT_NUMERIQUE_INVALIDE`, `FORMAT_IDENTIFIANT_INVALIDE`,
`VALEUR_NEGATIVE`, `VALEUR_HORS_LISTE`, `DOUBLON_EXACT`.

- Le générateur produit un événement **propre** ; l'`AnomalyInjector` le corrompt selon les taux du scénario.
- Reproductible : tout tirage passe par `SeededRandom`, jamais `Math.random()` ni `new Random()`.
- Chaque corruption est enregistrée dans `anomaly_injected` (événement, facture, type, champ, **valeur d'origine**, valeur corrompue).
- Taux global 0 % = flux de référence propre.
- Un mutator = une petite classe testable isolément.

## 6. Conventions de code

- **Métier en français** (`Facture`, `ControleDroitsService`, `date_naissance`), **technique en anglais** (`Engine`, `Sink`, `Injector`).
- Tables et colonnes en français, minuscules, `snake_case`. Identifiants UUID. Montants en `NUMERIC(14,2)`, jamais en `double`.
- Deux dates distinctes : temps **réel** (écriture) et temps **simulé** (date fictive du soin).
- Colonne `run_id` sur toute table liée à une simulation, `ON DELETE CASCADE` depuis `simulation_run`.
- **Aucune logique métier dans les contrôleurs** : contrôleur → service.
- Une classe par étape, une classe par mutator. Pas de classe fourre-tout.
- Aucun secret dans le dépôt : tout par variables d'environnement (`.env` non versionné, `.env.example` versionné).
- Toute règle métier a un test unitaire ; tout ce qui touche la base a un test d'intégration (Testcontainers).

## 7. Base de données

- La base n'est créée que par **Flyway** (`src/main/resources/db/migration/V<n>__<nom>.sql`). Jamais de table créée à la main.
- **Un script déjà appliqué ne se modifie jamais** : on ajoute un nouveau script. Tant que rien n'est en production,
  on peut repartir de zéro : `podman compose down -v && podman compose up -d`.
- Les index vont dans un script séparé, limités à `run_id`, `statut`, `facture_id` et aux besoins mesurés.
- Écriture par **lots** (batch) ; jamais ligne par ligne en charge.
- L'application se connecte à PostgreSQL **via Toxiproxy** (`toxiproxy:5432`), jamais en direct.

## 8. Conteneurs : Podman (rootless)

- Fichiers : `Containerfile` et `compose.yaml` (compatible `podman compose`).
- **Noms d'images complets** : `docker.io/library/postgres:16-alpine`, `ghcr.io/shopify/toxiproxy:2.9.0`.
- Volumes de fichiers avec `:Z` (SELinux) : `./scenarios:/app/scenarios:Z`.
- Ports ≥ 1024 uniquement. Exposer seulement le simulateur (et Grafana si besoin). **Jamais** PostgreSQL ni l'API de contrôle de Toxiproxy (8474) hors du serveur.
- **Ne jamais monter le socket Podman/Docker dans un conteneur.** Les arrêts brutaux de conteneurs se font depuis l'extérieur.
- Redémarrage auto : `restart: unless-stopped` (en rootless, durablement via systemd/Quadlet).
- Profils compose : cœur (postgres, toxiproxy, simulator), `observability` (prometheus, grafana), `kafka` (optionnel).

## 9. Déploiement sous un chemin

L'application sera hébergée sur un domaine existant, derrière un reverse proxy, sous un chemin dédié (hypothèse : `/simulateur/`).
Donc **dès le départ** :

- `server.servlet.context-path=/simulateur` et `server.forward-headers-strategy=framework`
- Thymeleaf : toujours `th:href="@{/dashboard}"` ; **jamais** de lien codé en dur vers `/`
- Flux SSE : prévoir `proxy_buffering off` côté proxy
- Ne rien changer à l'application déjà en place sur ce domaine

## 10. Sécurité

- Deux chaînes Spring Security séparées : `/api/**` (clé d'API, sans session, CSRF désactivé) et le reste (formulaire, session, CSRF actif).
- Rôles : `ADMIN`, `OPERATEUR`, `OBSERVATEUR`. Droits vérifiés **côté serveur** (`@PreAuthorize`), pas seulement en masquant des boutons.
- Mots de passe BCrypt ; verrouillage après 5 échecs ; session 30 min ; cookies `Secure` et `HttpOnly`.
- Clés d'API stockées hachées. Journal d'audit pour lancements, arrêts et actions de chaos.
- Premier ADMIN créé depuis la variable `ADMIN_INITIAL_PASSWORD`, changement obligatoire à la première connexion.
- Actions de chaos : confirmation obligatoire + bouton « Tout rétablir ».

## 11. Commandes

```bash
./mvnw -q clean verify                      # compiler + tests (obligatoire avant de terminer une tâche)
./mvnw -q test                              # tests rapides
podman compose up -d --build                # démarrer le cœur
podman compose --profile observability up -d
podman compose ps                           # état des conteneurs
podman compose logs -f simulator            # journaux
podman compose down -v                      # tout arrêter ET effacer la base
curl -s localhost:8080/simulateur/actuator/health
```

## 12. Méthode de travail (très important)

Le travail se fait par **prompts-actions** : une petite mission, un résultat vérifiable, un commit.

1. **Une brique à la fois.** Ne jamais enchaîner plusieurs briques sans vérification.
2. Avant de coder : lire ce fichier, `docs/journal.md` et le prompt demandé (`prompts/NN-*.md`).
3. Respecter les **« À ne pas faire »** du prompt (ne pas toucher au moteur, ne pas ajouter d'anomalies, etc.).
4. **Avant de dire « terminé »** : `./mvnw clean verify` vert, et si le prompt le demande, `podman compose up -d` démarre et `/actuator/health` répond `UP`.
5. Si un point du cahier des charges est ambigu : **poser la question** plutôt que d'inventer ; signaler toute hypothèse prise.
6. Terminer par : ce qui a été fait (2 lignes), comment le vérifier, et le **message de commit** proposé
   (`feat(prestations): facture et statuts`).
7. Ajouter 3 lignes à `docs/journal.md` : date, fait, reste à faire.
8. Une branche par brique (`step/03-facture`), fusionnée quand tout est vert.

### Ordre des briques

1 Socle · 2 Référentiel · 3 Facture · 4 Étapes du parcours · 5 Tarification · 6 Moteur · 7 Écriture en base ·
8 API · 9 Anomalies · 10 Chaos · 11 Sécurité et interface · 12 Qualité.
À la fin de la brique 4, un parcours complet tourne déjà.

## 13. Interdits

- Vraies données, vraies bases CNAM, secrets dans Git.
- Modifier un script Flyway déjà appliqué.
- Logique métier dans un contrôleur ; `Math.random()` ; montants en `double`.
- Exposer PostgreSQL, Toxiproxy ou le socket du moteur de conteneurs.
- Dépendance ou refonte non demandée dans le prompt en cours.

## 14. Environnement de travail (Windows)

- Poste Windows 11, IntelliJ IDEA, terminal PowerShell. Pas de WSL.
- Moteur de conteneurs : Podman Desktop (machine Podman). Jamais Docker.
- Java 21 (Temurin). Maven : `.\mvnw.cmd` sous PowerShell, `./mvnw` sous bash.
- Fins de ligne LF (voir `.gitattributes`). Aucun chemin absolu Windows dans le code ni dans `compose.yaml`.
- Les donnees PostgreSQL vont dans un **volume nomme** Podman (`pgdata`), jamais dans un dossier du projet monte.
- Si le suffixe `:Z` pose probleme avec Podman sous Windows, le retirer et le signaler.
- Testcontainers avec Podman sous Windows : variable `DOCKER_HOST` (npipe) et `TESTCONTAINERS_RYUK_DISABLED=true`.
- Donner toutes les commandes de lancement en version PowerShell.
- Curl dans PowerShell : `curl.exe`.

## 15. Etat du socle (config initiale deja posee)

`pom.xml` (toutes les dependances), `application.yml`, `Containerfile`, `compose.yaml` (+ `compose.override.yaml` de dev),
`docker/toxiproxy.json`, `.env.example`, Flyway `V1__init.sql`, squelette des packages (`package-info.java`),
`SecurityConfig` minimale (a remplacer a la brique Securite) et un test de socle. Le prompt 01 verifie et complete ce socle,
il ne le recree pas.
