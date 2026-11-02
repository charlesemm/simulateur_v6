# Cahier des charges - Simulateur de flux de données CNAM

Oct 29, 2026 · @Mathieu Charles N'GUESSAN

## 1. Contexte et objectifs

Le Service Gouvernance des Données de la DSI de la CNAM a besoin d'un simulateur de flux qui produit des données synthétiques réalistes, les écrit en base et met l'outil de qualité de données à l'épreuve, y compris en cas de panne.

**Objectifs**

- Générer des flux cohérents pour trois domaines : prestations, affiliation, recouvrement.
- Ne jamais utiliser de vraies données d'assurés : tout est synthétique.
- Mesurer ce que l'outil qualité détecte, grâce à des anomalies injectées et tracées à l'avance (vérité terrain).
- Tester la robustesse : coupure brutale en pleine transmission, surcharge, latence, reprise sans perte ni doublon.
- Rejouer exactement la même simulation (même graine) pour comparer avant et après une correction.
- Offrir une API aux applications internes et une interface pour piloter sans ligne de commande.

**Principe de gouvernance.** Les règles métier (tarifs, taux de prise en charge) sont fixées par les directions métier : le simulateur les lit dans une configuration, il ne les code pas en dur.

## 2. Périmètre et phasage

La phase 1 couvre uniquement le domaine Prestations, de bout en bout (parcours, anomalies, crash tests, API, interface). Affiliation et recouvrement réutilisent ensuite le même moteur.

| Phase | Domaine | Contenu | Dépend de |
| --- | --- | --- | --- |
| 1 | Prestations | Parcours du patient et facture, de la vérification des droits à la finalisation | Référentiel synthétique |
| 2 | Affiliation | Enrôlements, mises à jour, radiations ; alimente le référentiel | Moteur de la phase 1 |
| 3 | Recouvrement | Déclarations, cotisations, relances ; alimente le statut de cotisation | Affiliation |

**Hors périmètre**

- Toute connexion aux bases de production de la CNAM.
- Toute donnée réelle d'assuré, de prestataire ou d'employeur.
- Le calcul réglementaire officiel des remboursements : les taux sont des paramètres de simulation.
- Les moyens de paiement réels.

## 3. Utilisateurs et rôles

Trois rôles humains et un accès machine, avec des droits séparés.

| Acteur | Accès | Droits |
| --- | --- | --- |
| ADMIN | Interface web | Tout : utilisateurs, clés d'API, scénarios, chaos, suppression de runs |
| OPERATEUR | Interface web | Lancer, mettre en pause, arrêter une simulation ; déclencher le chaos ; éditer des scénarios |
| OBSERVATEUR | Interface web | Lecture seule : tableau de bord, événements, rapports (équipe qualité des données) |
| Application interne | API REST, clé d'API | Lire événements et anomalies ; envoyer les détections de l'outil qualité |

## 4. Domaine Prestations : le parcours de la facture

La facture (feuille de soins) est l'unité centrale : elle est ouverte après la vérification des droits, enrichie à chaque étape du parcours, puis finalisée. Chaque étape produit un événement rattaché à la facture.

**Étapes du parcours**

1. Arrivée du patient dans l'établissement.
2. Vérification des droits : droits ouverts et cotisations à jour. En cas d'échec, accès refusé et fin du parcours.
3. Ouverture de la facture.
4. Soins : consultation initiale.
5. Examens (biologie, imagerie) : optionnel.
6. Hospitalisation : optionnel.
7. Retour chez le médecin : consultation de suivi et prescription.
8. Pharmacie : délivrance des médicaments.
9. Finalisation de la facture.

Après la consultation, le parcours se ramifie selon des probabilités réglables dans le scénario (valeurs de départ : 60 % vers les examens, 10 % vers l'hospitalisation, 30 % directement vers la prescription).

&#91;embedded content: parcours de la facture · 9 étapes, 1 décision, 3 voies\]

Les pourcentages sont des valeurs de départ, réglables dans le scénario ; les trois voies se rejoignent à la prescription.

**États de la facture** : OUVERTE, EN\_COURS, PRESCRITE, MEDICAMENTS\_DELIVRES, FINALISEE ; sorties anticipées : REFUSEE (droits ou cotisations) et ABANDONNEE (patient parti, incident). Seules les transitions autorisées sont acceptées.

**Événements émis** : PATIENT\_ARRIVE, DROITS\_VERIFIES (ou ACCES\_REFUSE), FACTURE\_OUVERTE, CONSULTATION\_REALISEE, EXAMEN\_REALISE, HOSPITALISATION\_REALISEE, PRESCRIPTION\_EMISE, MEDICAMENTS\_DELIVRES, FACTURE\_FINALISEE.

**Les quatre types de bons de prise en charge** (d'après les feuilles de soins de référence)

| Type de bon | Réf. du formulaire | Feuillets |
| --- | --- | --- |
| Soins ambulatoires, consultations et médicaments | ER-01-GPR | 3 : établissement sanitaire, pharmacie, patient |
| Hospitalisation et soins | ER-02-GPR | 2 : formation sanitaire, patient |
| Biologie, imagerie et autres | ER-03-GPR | 2 : formation sanitaire, patient |
| Soins et prothèses dentaires | à confirmer | 2 : formation sanitaire, patient |

**Contenu d'une feuille de soins**

- Identification : date de soins, n° OGD, n° FS initiale, n° transaction, n° d'entente (CNAM et assurance complémentaire).
- Assuré : nom et prénoms, n° sécu, date de naissance, genre, données de l'assurance complémentaire (n° assuré, code, nom).
- Établissement d'accueil : code, nom, statut (CMR, urgence, éloignement, référence, autre).
- Professionnel de santé : code, nom et prénoms, spécialité.
- Informations complémentaires : maternité/grossesse, AVP, AT/MP, programme spécial, immatriculation du véhicule, observations.
- Prestations : code affection, code acte, désignation, dates, quantité, montant, part CMU, part assurance complémentaire, part assuré, cases examens / exéat / référé / hospitalisation / décès.
- Prescription et pharmacie : désignation, quantité prescrite, code pharmacie, code médicament, quantité servie, prix unitaire et total, répartition des parts.
- Spécifiques : date de rendez-vous pour le retrait des résultats (biologie) ; n° de dent et schéma dentaire (dentaire).

Sur ces formulaires, chaque champ est marqué « Agent » (saisi à la main) ou « SYSTÈME » (renseigné automatiquement). Cette distinction guide l'injection d'anomalies (section 6).

## 5. Moteur de simulation et scénarios

Le moteur est générique : il ne connaît pas le métier. Chaque domaine fournit un générateur de flux branché sur la même interface.

- **Run** : une exécution d'un scénario, avec les états créé, en cours, en pause, arrêté, échoué.
- **Horloge virtuelle** : le temps simulé peut être accéléré (x1, x10, x100) pour couvrir des cycles longs en quelques minutes ; chaque événement porte un horodatage réel et un horodatage simulé.
- **Graine (seed)** : deux runs avec la même graine et le même scénario produisent exactement les mêmes événements.
- **Débit et phases** : débit par domaine et par seconde, avec des phases (charge normale, surcharge x20, coupure).
- **Idempotence et checkpoint** : chaque événement a un identifiant unique ; l'avancement est enregistré pour reprendre sans perte ni doublon après un arrêt brutal.
- **File bornée** : entre le générateur et l'écriture en base, pour ralentir proprement en surcharge au lieu de saturer la mémoire.

Un scénario est un fichier YAML versionné, ou édité depuis l'interface :

```yaml
nom: "Prestations - journée normale puis surcharge"
seed: 42
accelerationTemps: 60
domaines:
  prestations:
    debit: 100/s
    parcours:
      examens: 60%
      hospitalisation: 10%
      prescriptionDirecte: 30%
    anomalies:
      champVide: 3%
      fauteOrthographe: 2%
phases:
  - { de: 0m, a: 5m, charge: normale }
  - { de: 5m, a: 8m, charge: x20 }
  - { de: 8m, chaos: coupure-base, duree: 30s }
```

## 6. Anomalies de qualité de données

Les anomalies sont simples, courantes et vérifiables : elles sont injectées volontairement dans une proportion réglable, et chacune est enregistrée avec sa valeur d'origine. L'outil qualité est ensuite évalué contre cette vérité terrain.

| Anomalie | Exemple | Champs concernés |
| --- | --- | --- |
| CHAMP\_VIDE | nom vide ou null | nom, prénoms, date de naissance, établissement, acte |
| FAUTE\_ORTHOGRAPHE | Kouassi devient Kouasi | noms, prénoms, ville, libellés |
| ESPACES\_PARASITES | espaces avant, après ou doublés | tout texte |
| CASSE\_INCOHERENTE | KOUASSI, kouassi | noms, prénoms |
| CARACTERES\_SPECIAUX | Kou@ssi# | noms, prénoms |
| FORMAT\_DATE\_INVALIDE | 31/02/2026, 2026-13-45 | dates |
| FORMAT\_NUMERIQUE\_INVALIDE | montant 12a0 | montants, quantités |
| FORMAT\_IDENTIFIANT\_INVALIDE | numéro trop court, lettres dans un numéro | identifiant assuré |
| VALEUR\_NEGATIVE | montant = -5000 | montants |
| VALEUR\_HORS\_LISTE | genre = X, statut inexistant | champs à valeurs fixes |
| DOUBLON\_EXACT | même événement envoyé deux fois | événement entier |

**Règles**

- L'injection est reproductible (même graine, mêmes anomalies).
- Chaque anomalie est tracée : événement, facture, type, champ, valeur d'origine, valeur corrompue.
- Un taux global de 0 % donne un flux de référence parfaitement propre, utile pour mesurer les faux positifs.
- Priorité aux champs saisis à la main par l'agent, les plus exposés dans la réalité.
- Les anomalies structurelles (champ vide, format invalide) sont détectables par règle ; les anomalies sémantiques (orthographe, casse) ne le sont que par référentiel ou similarité. Le rapport qualité les distingue.
- Chaque type d'anomalie est une petite classe indépendante (mutator) : en ajouter un ne touche pas au reste.

## 7. Crash tests (chaos)

Le simulateur doit pouvoir casser volontairement son environnement pendant une simulation, puis prouver qu'il se rétablit sans perte ni doublon.

| Test | Méthode | Ce qu'on vérifie |
| --- | --- | --- |
| Coupure brutale de la base en plein lot | Toxiproxy : proxy désactivé ou connexion réinitialisée | Transaction annulée proprement, aucun enregistrement à moitié écrit |
| Latence réseau | Toxiproxy : délai ajouté | Le débit baisse, le système ne s'effondre pas |
| Surcharge x5, x20, x100 | Multiplicateur de charge interne, limites CPU et mémoire du conteneur | Ralentissement maîtrisé (file bornée), point de rupture mesuré |
| Arrêt brutal du simulateur | Arrêt forcé du conteneur, redémarrage automatique | Reprise au dernier checkpoint, sans doublon |
| Arrêt brutal de la base | Arrêt forcé du conteneur PostgreSQL | Reprise de l'écriture après retour de la base |
| Messages tronqués ou doublonnés | Injection côté flux | Détection par l'outil qualité |

**Règles de sécurité**

- Une action de chaos demande une confirmation et laisse une trace d'audit (qui, quand, quoi).
- Un bouton Tout rétablir annule immédiatement tous les chaos actifs.
- Le simulateur ne monte pas le socket du moteur de conteneurs : les arrêts brutaux de conteneurs se font depuis l'extérieur (scripts ou ligne de commande).
- Le simulateur pointe toujours vers sa propre base, jamais vers une base réelle de la CNAM.

## 8. API REST

L'API sert aux applications internes, en priorité l'outil de qualité des données. Elle est documentée automatiquement (Swagger) et protégée par une clé d'API dans l'en-tête X-API-Key.

| Méthode | Route | Rôle |
| --- | --- | --- |
| POST | /api/runs | Lancer une simulation à partir d'un scénario |
| POST | /api/runs/{id}/pause et /api/runs/{id}/stop | Mettre en pause, arrêter |
| GET | /api/runs et /api/runs/{id} | Historique et statut |
| POST | /api/runs/{id}/chaos | Déclencher une action de chaos |
| GET | /api/runs/{id}/events | Lire les événements (filtres, pagination) |
| GET | /api/runs/{id}/anomalies | Lire la vérité terrain |
| POST | /api/runs/{id}/quality/report | L'outil qualité envoie ses détections |
| GET | /api/runs/{id}/quality/comparison | Injectées contre détectées : détectées, ratées, faux positifs |
| GET, POST | /api/scenarios | Gérer les scénarios |
| GET | /api/stream/metrics | Flux temps réel (SSE) |

## 9. Interface et sécurité

Une interface web légère rend le simulateur utilisable sans ligne de commande.

**Écrans**

- Tableau de bord en direct : débit par domaine, erreurs, latence, état du simulateur, de la base et du proxy.
- Lancement : choix du scénario, graine, vitesse du temps, boutons démarrer, pause, reprendre, arrêter, rejouer.
- Éditeur de scénarios : formulaire, validation, import et export YAML.
- Panneau chaos : couper la base, ajouter de la latence, surcharge, anomalies, tout rétablir.
- Explorateur d'événements : filtres, export CSV, rejeu d'un événement échoué.
- Rapport qualité : injectées contre détectées, comparaison de deux runs.
- Administration (ADMIN) : utilisateurs, clés d'API.

**Sécurité**

- Page de connexion avec message d'erreur générique ; mots de passe hachés (BCrypt) ; verrouillage temporaire après 5 échecs ; expiration de session après 30 minutes d'inactivité.
- Protection CSRF, cookies Secure et HttpOnly, HTTPS derrière le reverse proxy.
- Droits vérifiés côté serveur, pas seulement par masquage des boutons.
- Clés d'API stockées hachées ; deux chaînes de sécurité séparées (interface par session, API par clé).
- Journal d'audit des lancements, arrêts et actions de chaos.
- Premier compte ADMIN créé depuis une variable d'environnement, avec changement de mot de passe obligatoire à la première connexion.
- Branchement possible à l'annuaire de la CNAM (LDAP ou Active Directory) dans une version ultérieure.

## 10. Exigences non fonctionnelles

| Exigence | Niveau attendu |
| --- | --- |
| Reproductibilité | Même graine et même scénario donnent les mêmes événements |
| Fiabilité | Aucune perte ni doublon après une coupure ; reprise automatique |
| Performance | Écriture en base par lots ; débit de départ de 100 événements par seconde et par domaine, à confirmer par mesure |
| Résistance à la charge | Surcharge jusqu'à x20 sans arrêt du service ; point de rupture mesuré et documenté |
| Observabilité | Métriques Micrometer exposées à Prometheus, tableau de bord Grafana, journaux structurés |
| Maintenabilité | Un domaine par dossier, une classe par étape, une classe par anomalie ; ajout d'un domaine sans toucher au moteur |
| Portabilité | Tout tourne en conteneurs (Podman, sans droits root), configuré par variables d'environnement |
| Confidentialité | Données exclusivement synthétiques ; aucun secret dans le dépôt |
| Testabilité | Tests unitaires et tests d'intégration avec de vrais conteneurs (Testcontainers) |

## 11. Architecture technique et conteneurs

Le simulateur est un monolithe modulaire Spring Boot : un seul déploiement, des modules bien séparés (moteur, domaines, anomalies, chaos, sorties, API, interface). Un domaine isolé plus tard pourra être extrait en service sans refonte.

| Besoin | Choix |
| --- | --- |
| Langage et framework | Java 21, Spring Boot 3 |
| Base de données | PostgreSQL 16, migrations Flyway, écriture par lots (JDBC) |
| API et documentation | Spring Web, springdoc-openapi (Swagger) |
| Interface | Thymeleaf, HTMX, Chart.js, flux SSE pour le temps réel |
| Sécurité | Spring Security : formulaire de connexion (interface), clé d'API (API) |
| Génération de données | Datafaker et règles métier propres, aléa piloté par la graine |
| Chaos réseau | Toxiproxy placé entre l'application et PostgreSQL |
| Supervision | Micrometer, Prometheus, Grafana (profil optionnel) |
| Tests | JUnit 5, AssertJ, Testcontainers |
| Conteneurs | Podman sans droits root et compose ; noms d'images complets ; volumes avec option :Z |

**Conteneurs** : postgres, toxiproxy et simulator (obligatoires) ; prometheus et grafana (profil observabilité) ; kafka (profil optionnel). L'application se connecte à PostgreSQL uniquement via Toxiproxy ; seuls le simulateur (et Grafana au besoin) sont exposés, jamais la base ni l'API de contrôle de Toxiproxy.

&#91;embedded content: architecture des conteneurs · 5 éléments\]

Toutes les écritures passent par Toxiproxy, qui permet de couper ou de ralentir la connexion à chaud ; Prometheus et Grafana s'ajoutent en profil optionnel.

## 12. Modèle de données

La base est créée uniquement par des scripts SQL numérotés (Flyway) : on peut la détruire et la recréer à l'identique. Un script déjà appliqué ne se modifie jamais ; pendant la construction, on repart simplement d'une base vide.

| Famille | Tables principales |
| --- | --- |
| Référence | assure, ayant\_droit, centre\_sante, medecin, pharmacie |
| Catalogues | acte, examen, medicament, tarif, taux |
| Prestations | facture, facture\_ligne, facture\_historique, parcours |
| Simulation | scenario, simulation\_run, event, checkpoint, chaos\_action |
| Qualité et sécurité | anomaly\_injected, quality\_report, app\_user, api\_key, audit\_log |

**Conventions**

- Noms de tables et de colonnes en français, minuscules, snake\_case ; identifiants en UUID.
- Montants en NUMERIC(14,2), jamais en flottant.
- Deux notions de date séparées : temps réel (écriture) et temps simulé (date fictive du soin).
- Statuts contraints par une règle CHECK ; colonne run\_id partout, avec suppression en cascade depuis le run.
- Contenu complet de chaque événement en JSONB ; index limités à run\_id, statut, facture\_id, ajoutés dans un script séparé.

**Décision à trancher : données propres ou corrompues dans les tables métier.** Option A (recommandée) : les tables métier gardent la donnée propre, l'événement envoyé porte la version corrompue, et la table des anomalies garde l'original. Option B : les tables métier contiennent directement la donnée corrompue, plus réaliste si l'outil qualité lit la base directement.

## 13. Déploiement

Le simulateur est d'abord développé et testé en local, puis hébergé sur le domaine déjà utilisé par l'application existante, derrière le même reverse proxy.

- Chemin dédié du même domaine (hypothèse : /simulateur/), pas de sous-domaine ni de nouveau certificat.
- L'application déclare son contexte (server.servlet.context-path=/simulateur) et lit les en-têtes du proxy ; aucun lien codé en dur vers la racine.
- Le proxy envoie /simulateur/ vers un port interne libre du serveur, avec la mise en mémoire tampon désactivée pour le flux temps réel (SSE).
- L'application existante reste inchangée ; sa disponibilité est la première vérification après la mise en service.
- Base dédiée au simulateur ; ni PostgreSQL, ni l'API de contrôle de Toxiproxy ne sont exposés hors du serveur.
- Les informations du serveur actuel (proxy utilisé, emplacement de la configuration, ports déjà pris, mode de lancement) sont à relever avant la mise en service et à consigner dans docs/deploiement.md.

## 14. Plan de réalisation

Chaque brique est une mission courte avec un résultat vérifiable, un commit et une ligne au journal. On ne passe à la suivante que si la vérification est réussie.

| N° | Brique | Livrable | Vérification |
| --- | --- | --- | --- |
| 1 | Socle | Projet, Containerfile, compose (postgres, toxiproxy, simulateur), Flyway | /actuator/health répond UP |
| 2 | Référentiel | Tables et générateur de jeu synthétique | Nombre attendu d'assurés en base |
| 3 | Facture | Facture, lignes, états et transitions | Tests de transitions verts |
| 4 | Étapes du parcours | Une classe par étape, graphe pondéré | Un patient fictif fait le parcours complet |
| 5 | Tarification | Part CNAM, ticket modérateur, taux configurables | Tests de calcul verts |
| 6 | Moteur | Horloge virtuelle, scénario YAML, contrôle du run | Un run démarre, se met en pause, s'arrête |
| 7 | Écriture en base | Sortie par lots, checkpoint | Débit mesuré, reprise sans doublon |
| 8 | API | Routes de la section 8, Swagger | Swagger affiche les routes |
| 9 | Anomalies | Mutators et vérité terrain | Anomalies enregistrées en base |
| 10 | Chaos | Surcharge, Toxiproxy, tests de reprise | Coupure en plein lot sans perte ni doublon |
| 11 | Sécurité et interface | Connexion, rôles, écrans, boutons | Parcours utilisateur testé de bout en bout |
| 12 | Qualité | Réception des détections, rapport de comparaison | Rapport injectées contre détectées affiché |

À la fin de la brique 4, un parcours complet tourne déjà ; les briques suivantes l'enrichissent. Affiliation et recouvrement viennent ensuite, en réutilisant le moteur.

## 15. Hypothèses et décisions ouvertes

Valeurs retenues par défaut pour avancer ; chacune peut être changée sans remettre en cause la structure.

| Sujet | Hypothèse par défaut | À confirmer |
| --- | --- | --- |
| Identifiant assuré | 10 chiffres avec clé de contrôle | Format réel du n° sécu |
| Contrôle des cotisations | Porte sur l'assuré, via un statut du référentiel | Assuré ou employeur |
| Prescription | Toujours possible après une consultation, avec ou sans examen | Règle métier |
| Pharmacie | Une seule facture par parcours, pharmacie incluse | Facture propre à la pharmacie (tiers payant) |
| Volume du référentiel | 50 000 assurés synthétiques | Volume voulu |
| Données propres ou corrompues | Option A (section 12) | Option A ou B |
| Types de bons | Les quatre types de la section 4 | Référence du bon dentaire |
| Chemin de l'application | /simulateur/ | Chemin retenu |
| Taux et tarifs | Valeurs fictives en configuration | Valeurs fixées par les directions métier |

## 16. Critères de recette

Le projet est réussi lorsque tous les points suivants sont vérifiés.

- [ ] Un parcours de prestation complet est généré, de la vérification des droits à la finalisation, pour les quatre types de bons.
- [ ] Deux runs avec la même graine produisent des événements identiques.
- [ ] Chaque anomalie injectée est retrouvable dans la vérité terrain, avec sa valeur d'origine.
- [ ] Un run avec 0 % d'anomalie produit un flux sans aucun défaut.
- [ ] Après une coupure brutale de la base en plein lot, aucune ligne n'est perdue, dupliquée ou à moitié écrite.
- [ ] Après un arrêt brutal du simulateur, le run reprend au dernier checkpoint.
- [ ] Une surcharge x20 ne provoque pas d'arrêt du service ; le point de rupture est documenté.
- [ ] L'outil qualité peut lire les événements et envoyer ses détections par l'API, et le rapport affiche détectées, ratées et faux positifs.
- [ ] Les rôles ADMIN, OPERATEUR et OBSERVATEUR sont respectés côté serveur.
- [ ] Tout démarre avec une seule commande de compose, sans droits root.
- [ ] L'application existante sur le même domaine reste disponible après la mise en service.
