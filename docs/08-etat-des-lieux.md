# Écho — état des lieux de la branche `main`

*Établi le 12/11/2026 sur `main` (commit `b907f17`, étiquette `v1.1.0`, identique à `origin/main`). Sources : cahier des charges, journal, code de `main`, et une base jetable montée avec les migrations V1 à V12 de `main`, supprimée après l'audit.*

> **Mise à jour du 12/11/2026, après ce constat.** Les branches 13 à 17 ont été fusionnées dans `main`
> (avance rapide jusqu'à `4160a2d`, `clean verify` vert : 533 tests, 0 échec, 2 ignorés). `main` porte
> donc désormais le module Qualité, les tables `TB_ECHO_*` et les migrations V13 à V20. Les sections 1, 2
> et 4.1 à 4.3 décrivent `main` **avant** cette fusion. Restent valables : les critères de recette
> (section 3), les manques de la base (index, feuille de soins, contraintes sur les événements), les
> écarts fonctionnels (section 5) et l'ordre proposé à partir du point 5.

## En bref

- `main` contient **la phase 1 complète** (briques 1 à 12) et l'interface Écho. C'est la version `v1.1.0`.
- `main` **ne contient rien du module Qualité** (export, canal de retrait, planification, onglet Analyse, anomalies de relation) ni des tables de valeurs : tout cela attend sur des branches, dont une qui n'existe que sur ce poste.
- **Trois critères de recette ne sont pas tenus** : le bon dentaire, la reprise du run après un arrêt brutal, la vérification sur le serveur.
- **Base de `main`** : 24 tables, 12 migrations qui passent, schéma propre, mais **aucun index**, des listes de valeurs en texte libre et une feuille de soins très incomplète.
- **Le certificat `*.ipscnam.ci` expire le 20/11/2026**, dans 8 jours.

Non vérifié dans cette passe : `clean verify` n'a pas été relancé sur `main` (le journal annonce 408 tests verts au 08/11), et rien n'a été contrôlé sur le serveur.

---

## 1. Ce que `main` contient

| Élément | État sur `main` |
|---|---|
| Code | 234 classes Java, 70 classes de test |
| Paquets | `anomaly`, `api`, `chaos`, `core`, `domain`, `monitoring`, `persistence`, `quality`, `security`, `sink`, `web` |
| Migrations | V1 à V12 |
| Anomalies | 11 types, un mutator par type |
| Scénarios | `parcours-normal.yaml`, `anomalies-3pct.yaml` |
| Écrans | tableau de bord, lancement, crash tests, flux de données, simulations passées, qualité, scénarios, administration |
| Documentation | chaos, déploiement, cycle de travail, journal (dernière entrée : 08/11, cycle de travail) |
| Intégration continue | CI, CodeQL, sécurité (Gitleaks, Trivy), publication de l'image |

## 2. Ce que `main` n'a pas, et qui existe ailleurs

| Travail | Où il se trouve | Migrations |
|---|---|---|
| Q1 — export xlsx / csv / json, marquage, empreinte | `step/13-export-simulation` (poussée) | V13 |
| Q2 — canal de retrait, échecs d'échange, script Python | `step/14-canal-retrait` (poussée) | V14 |
| Q3 — planification des analyses | `step/15-planification-analyses` (poussée) | V15 |
| Q4 — onglet Analyse | `step/17-anomalies-de-relation` (**locale seulement**) | aucune |
| Q5 — six anomalies de relation et de fichier | `step/17-anomalies-de-relation` (**locale seulement**) | V16 |
| Renommage des tables en `TB_ECHO_*` | `step/17-anomalies-de-relation` (**locale seulement**) | V17 |
| Tables de valeurs CNAM, régimes, provenance | `step/17-anomalies-de-relation` (**locale seulement**) | V18 à V20 |
| Cahier et plan du module Qualité, prompts 13 à 17 | `step/17-anomalies-de-relation` (**locale seulement**) | — |

La branche 17 porte 11 commits d'avance sur `main` et contient les branches 13 à 15. Elle n'a pas d'amont : si ce poste tombe, ce travail est perdu.

S'y ajoutent dix propositions Dependabot ouvertes (Datafaker, springdoc, Chart.js, htmx, Maven, cinq actions GitHub), non fusionnées.

---

## 3. Critères de recette (cahier, section 16) sur `main`

| Critère | État | Détail |
|---|---|---|
| Parcours complet pour les quatre types de bons | **Non** | Le bon `DENTAIRE` existe (enum, actes, taux) mais le parcours ne produit qu'ambulatoire, hospitalisation et biologie. |
| Même graine, mêmes événements | Oui | Tests de déterminisme. |
| Chaque anomalie retrouvable avec sa valeur d'origine | Oui | Table `anomaly_injected`, 11 types. |
| 0 % d'anomalie = flux propre | Oui | |
| Coupure de la base en plein lot sans perte ni doublon | Oui | Test de chaos, reprise en 1 s environ. |
| Après un arrêt brutal du simulateur, reprise au dernier checkpoint | **Non** | Le checkpoint est écrit par le sink, mais rien au démarrage ne relance un run interrompu. Reporté depuis la brique 7. |
| Surcharge x20 sans arrêt, point de rupture documenté | Partiel | Mesuré en local (environ 150 parcours/s). À refaire sur le serveur cible. |
| L'outil qualité lit, envoie, et le rapport s'affiche | Oui | Routes `quality/report` et `quality/comparison`, écran `/qualite`. |
| Rôles respectés côté serveur | Oui | `@PreAuthorize` et tests de refus en 403. |
| Une seule commande de compose, sans root | Oui en local | En production, le dispositif décrit tourne en `sudo podman-compose`, pas en rootless. |
| L'application existante reste disponible | Non vérifié | À confirmer sur le serveur. |

---

## 4. La base de données de `main`

### 4.1 Ce qui est en place

| Point | Constat |
|---|---|
| Migrations | V1 à V12, toutes appliquées sans erreur sur une base vide. |
| Tables | 24 : référence (5), catalogues (4), prestations (3), simulation (4), qualité (3), sécurité (3), `scenario`, `info_application`. |
| Nommage | Minuscules, `snake_case`, conforme au cahier. |
| Montants | Tous en `NUMERIC(14,2)`. Seule exception : `taux.taux`, qui est un taux. |
| Flottants | Un seul : `quality_detection.score`, la confiance de l'outil. Ce n'est pas un montant. |
| Cascade depuis le run | `facture`, `evenement`, `checkpoint_evenement`, `anomaly_injected`, `chaos_action`, `quality_report`, `quality_detection` : clé étrangère `run_id` avec `ON DELETE CASCADE`. |
| Deux dates | `timestamp_simule` et `timestamp_reel` sur `evenement`. |
| Contenu des événements | `payload` en JSONB. |
| Statuts contraints | `CHECK` sur le statut de la facture, du run, des actions de chaos, sur le type d'anomalie et le type de bon. |

### 4.2 Ce qui manque ou s'écarte du cahier

| Écart | Gravité | Détail |
|---|---|---|
| **Aucun index** | Forte | Zéro index hors clés primaires et unicités. **14 clés étrangères n'ont pas d'index couvrant**, dont `run_id` sur `evenement`, `facture`, `anomaly_injected`, `chaos_action`, `quality_report` et `quality_detection`. Toute lecture par run et toute suppression de run parcourent la table entière. Le « script séparé » annoncé en tête de chaque migration n'a jamais été écrit. |
| **Feuille de soins incomplète** | Forte | `facture` ne porte ni médecin, ni pharmacie, ni motif de refus, ni n° OGD, n° de transaction, n° d'entente, code affection, informations complémentaires (maternité, AVP, AT/MP), ni les champs dentaires. Ces données n'existent que dans le JSON des événements, quand elles existent. |
| Listes de valeurs en texte libre | Moyenne | `centre_sante.ville`, `pharmacie.ville` et `medecin.specialite` sont du texte sans contrainte ni référentiel. Aucune table de valeurs. Corrigé par V18 et V19 sur la branche 17. |
| Pas de régime sur l'assuré | Moyenne | `assure` n'a pas de régime (général ou assistance) : le taux ne dépend que du type de prestation. Ajouté par V19 et V20 sur la branche 17. |
| `evenement` sans contrainte | Faible | Aucun `CHECK` : `statut`, `type` et `domaine` sont libres, alors que le cahier demande des statuts contraints. |
| `run_id` sans clé étrangère | Faible | Sur `facture_ligne` et `facture_historique`. La cascade passe par la facture, donc sans conséquence, mais la colonne n'est pas garantie cohérente. |
| Tables prévues et absentes | Faible | `parcours` et `tarif` (section 12 du cahier). Le tarif est une colonne du catalogue ; le parcours n'est tracé que par les événements. |
| Noms en anglais | Faible | `anomaly_injected`, `quality_report`, `quality_detection`, `app_user`, `api_key`, `audit_log`, alors que le cahier demande des noms français. Ce sont les noms que le cahier lui-même cite. |
| Identifiants non UUID | Faible | `anomaly_injected.id` et `quality_detection.id` sont en `bigint`. |
| Commentaires | Faible | Aucune des 24 tables n'a de `COMMENT ON TABLE`. |

### 4.3 Ce qui attend la base au moment de la fusion

Fusionner la branche 17 fera passer la base de V12 à V20 en une fois :

- V13 à V15 ajoutent cinq tables (export, échanges, planification).
- V16 recrée deux contraintes de `anomaly_injected`. **Le test sur une base portant déjà des anomalies n'est pas écrit.**
- V17 **renomme 23 tables** en `TB_ECHO_*` majuscules. **Jamais joué sur une base non vide.**
- V18 à V20 créent 14 tables de valeurs et remplacent les textes libres par des clés étrangères.

Une base à la version de `main` qui contient déjà des simulations subira donc V16, V17 et V19 sur des données. C'est à répéter sur une copie avant le serveur.

Ni la branche 17 ni `main` ne règlent les index, la feuille de soins et les contraintes sur `evenement`.

### 4.4 Pour finir la base

1. Éprouver V13 à V20 sur une base de `main` remplie (une simulation avec anomalies), puis fusionner.
2. **Script d'index** (numéro suivant la dernière migration fusionnée) : `run_id` sur toutes les tables de run, `(run_id, facture_id)` et `(run_id, type)` sur les événements, `facture_id` sur l'historique, `assure_id` sur les ayants droit et les factures, `(run_id, type)` sur les anomalies.
3. **Feuille de soins** : médecin, pharmacie et motif de refus sur la facture, puis les champs d'identification. À cadrer avant d'écrire : quels champs vont en colonnes, lesquels restent dans le JSON.
4. **Contraintes** : `CHECK` sur le statut des événements, clés étrangères `run_id` manquantes.

---

## 5. Écarts fonctionnels avec le cahier, sur `main`

| Section | Écart |
|---|---|
| 4 — Parcours | La sortie `ABANDONNEE` existe dans la machine à états, aucune étape ne la déclenche. |
| 5 — Scénarios | Les phases de chaos écrites dans le YAML sont ignorées avec un avertissement. Le chaos ne part qu'à la main ou par l'API. |
| 5 — Débit | Le moteur ne ventile pas le débit par domaine. Sans conséquence aujourd'hui, bloquant en phase 2. |
| 6 — Anomalies | Les 11 types du cahier sont là. Les anomalies de relation (doublon approximatif, référence brisée, incohérences) ne sont que sur la branche 17. |
| 8 — API | `GET /api/stream/metrics` n'existe pas. Le flux temps réel est sur `/flux`, réservé à l'interface. Les dix autres routes du cahier existent. |
| 9 — Administration | Pas d'écran pour les clés d'API (le service existe). |
| 10 — Observabilité | Grafana n'a que sa source de données, aucun tableau de bord. Pas de journaux structurés. |
| 11 — Conteneurs | Profil `kafka` absent (il est optionnel). |

---

## 6. Ce qui attend quelqu'un d'autre

- **Directions métier** : taux complémentaire, référence du bon dentaire, format réel du n° de sécurité sociale ; et, une fois la branche 17 fusionnée, produits, motifs de rejet et statuts de liquidation.
- **Hypothèses ouvertes du cahier (section 15)** : contrôle des cotisations sur l'assuré ou l'employeur, facture propre à la pharmacie, données propres ou corrompues dans les tables métier.
- **Serveur** : `chmod` des scénarios, `deploy.yml`, limites mémoire et processeur à revoir après mesures.

## 7. Phases 2 et 3

Affiliation et recouvrement ne sont pas commencés : `domain/` ne contient que `prestations` et `reference`.

---

## 8. Ordre proposé

| N° | Action | Pourquoi maintenant |
|---|---|---|
| 1 | Renouveler le certificat | Échéance dans 8 jours. |
| 2 | Pousser la branche 17 | Onze commits n'existent que sur ce poste. |
| 3 | Éprouver V13 à V20 sur une base de `main` remplie | Préalable à la fusion et à toute mise à jour du serveur. |
| 4 | Fusionner 13 à 17 dans `main`, étiqueter | `main` rattrape cinq briques. |
| 5 | Script d'index | Dette la plus ancienne ; aucun risque fonctionnel. |
| 6 | Reprise du run par le moteur | Critère de recette le plus lourd. |
| 7 | Parcours dentaire et sortie `ABANDONNEE` | Critère de recette n° 1. |
| 8 | Feuille de soins complète | À cadrer avec les directions métier. |
| 9 | Clés d'API, chaos dans le YAML, `/api/stream/metrics`, Grafana | Écarts restants de la phase 1. |
| 10 | Mesures sur le serveur, puis phase 2 | |

Les points 5 à 9 demandent chacun un prompt dans `prompts/` (18 et suivants), selon la méthode du projet.
