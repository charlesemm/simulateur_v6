# Prompts de réalisation

Les prompts 01 à 12 (phase 1, prestations) sont dans
`docs/Prompts de réalisation - Simulateur de flux CNAM.md`. Phase terminée le 2026-11-06.

Ce dossier porte les prompts écrits depuis. Règle inchangée (CLAUDE.md, section 12) :
**une brique à la fois**, `./mvnw clean verify` vert avant de dire « terminé »,
une branche par brique, trois lignes dans `docs/journal.md`.

## Module « Qualité des données » — briques Q1 à Q5

Cahier des charges et plan : `docs/07-qualite-donnees/`.
Le plan (`04-plan.md`) remplace le découpage M1–M8, qui était écrit pour la v5.

| Prompt | Brique | Branche | Dépend de |
|---|---|---|---|
| [13](13-export-simulation.md) | Q1 — Export complet xlsx / csv / json, marquage, empreinte | `step/13-export-simulation` | — |
| [14](14-canal-retrait.md) | Q2 — Canal de retrait pour un script autonome, échecs d'échange | `step/14-canal-retrait` | 13 |
| [15](15-planification-analyses.md) | Q3 — Planification des analyses | `step/15-planification-analyses` | 13, 14 |
| [16](16-onglet-analyse.md) | Q4 — L'onglet Analyse en poste de pilotage | `step/16-onglet-analyse` | 13, 14, 15 |
| [17](17-anomalies-de-relation.md) | Q5 — Les anomalies de relation et de fichier | `step/17-anomalies-de-relation` | 13 |

Q1 est le préalable de tout. **Q5 est indépendant de Q2–Q4** : il peut passer en
dernier, ou juste après Q1 si les dimensions manquantes pressent. Tant qu'il n'est pas
fait, quatre des huit dimensions du cahier n'ont rien à mesurer.

## Migrations réservées

Pour éviter deux scripts portant le même numéro si deux briques avancent en parallèle :

| Script | Brique |
|---|---|
| `V13__export.sql` | Q1 |
| `V14__echange.sql` | Q2 |
| `V15__planification.sql` | Q3 |
| (aucune) | Q4 |
| `V16__anomalies_relation.sql` | Q5 |

Q5 ne peut pas prendre un numéro inférieur à celui de la dernière migration déjà
appliquée : si Q5 passe avant Q2, renuméroter son script et corriger ce tableau.

## Suivi

- [ ] Q1 — Export : les trois formats, même empreinte deux fois
- [ ] Q2 — Canal : le script d'exemple fait le cycle seul
- [ ] Q3 — Planification : « chaque jour à telle heure » tient
- [ ] Q4 — Onglet : une anomalie ratée s'ouvre d'un clic
- [ ] Q5 — Relations : les six nouveaux types au corrigé
