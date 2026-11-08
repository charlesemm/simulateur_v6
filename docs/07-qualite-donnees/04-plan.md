# Plan retenu — v6, 5 briques

> Arbitré le 2026-11-08 avec le porteur du projet. Ce plan **remplace** le
> découpage M1–M8 de `03-decoupage-propose.md`, écrit pour la v5.

## Ce que le brief change par rapport au cahier

Le cahier suppose un jeu de données fabriqué exprès par un générateur en lot, et
un outil extérieur inconnu. Le brief tranche autrement, et c'est plus simple :

| Le cahier | Ce qu'on fait |
|---|---|
| Un générateur en lot dédié aux campagnes | **Le flux déjà produit par les simulations est le jeu testé.** Pas de second générateur. |
| Un objet « campagne » distinct de l'exécution | **L'exécution de simulation EST la campagne.** `simulation_run` porte déjà graine, scénario, horodatages. Aucune table de campagne. |
| Un outil extérieur qu'Écho pousse (push) | **Un script autonome qui vient chercher (pull).** Écho expose, le script prend. |
| Les paliers de charge, le banc d'essai | **Hors de ces 5 briques.** Reporté : la v6 sait déjà monter en volume par le scénario. |

### Les deux décisions qui en découlent

1. **Deux téléchargements, jamais un seul.** Le *jeu à analyser* (assurés,
   factures, lignes, événements) et le *corrigé* (`anomaly_injected` : type,
   champ, valeur d'origine, valeur injectée) sont deux fichiers distincts. Le
   corrigé n'entre jamais dans le fichier remis au script — sinon le script peut
   lire les réponses et le score ne mesure plus rien. Le corrigé reste
   téléchargeable par un humain (rôle ADMIN ou OPERATEUR) pour l'audit.
2. **La clé d'une ligne est `event_id`, pas son numéro de ligne.** Décision
   actée : on ne crée aucune table de lignes exportées, on interroge les tables
   métier existantes. Donc chaque ligne de chaque format porte son `event_id`,
   et c'est là-dessus que le rapport du script se raccroche — exactement ce que
   `POST /api/runs/{id}/quality/report` attend déjà aujourd'hui.

## Ce qui existe déjà et qu'on ne réécrit pas

- `anomaly_injected` : le corrigé, écrit dans la transaction du lot. **Rien à faire.**
- `POST /api/runs/{id}/quality/report` : réception d'un rapport, ancrée sur
  (`event_id`, type, champ), refus en bloc d'un événement inconnu (422).
- `GET /api/runs/{id}/quality/comparison` : `LigneQualite` calcule déjà
  injectées / détectées / ratées / faux positifs, `tauxDetection` (= le **rappel**
  du cahier) et `precision`, par type et par catégorie, total compris.
- Écran `/qualite` : les 4 tuiles et le tableau par type.
- `SeededRandom` et la graine du scénario : la rejouabilité du jeu.

Autrement dit le chapitre 2.4 du cahier (rapprochement, rappel, précision) est
**déjà livré**. Ce qui manque est en amont (les fichiers) et en aval (la
planification et les écrans).

---

## Q1 — L'export complet d'une simulation, en 3 formats

**Objectif** : tout ce qu'une simulation a produit, téléchargeable.

- Nouveau paquet `export/` : `ExportService` + un écrivain par format.
- Contenu : une table par feuille / fichier / clé JSON — `assures` (ceux
  concernés par le run), `factures`, `facture_lignes`, `evenements`.
- Formats, tous **en flux** (jamais le fichier entier en mémoire) :
  - `xlsx` : Apache POI **SXSSF** (fenêtre de lignes) — nouvelle dépendance ;
  - `csv` : une archive zip, un `.csv` par table, BOM UTF-8 comme l'export actuel ;
  - `json` : un `JsonGenerator` Jackson, un tableau par table.
- **Marquage non désactivable** (cahier 2.2) : chaque ligne porte
  `donnee_fictive = OUI` et la référence de la simulation. Aucun réglage ne
  l'enlève.
- Chaque ligne porte son `event_id`.
- Table `export_fichier` : simulation, format, horodatage, nombre de lignes,
  **empreinte SHA-256**. Deux téléchargements de la même simulation donnent la
  même empreinte : c'est la preuve de rejouabilité, visible à l'écran.
- Le corrigé part séparément (`/export/corrige.csv`), réservé par `@PreAuthorize`.

**Test visuel** : je termine une simulation, je télécharge les 3 formats, je les
ouvre, chaque ligne porte son marquage et son `event_id`. Je retélécharge :
l'empreinte affichée est la même.

**Points à surveiller** : POI est une dépendance lourde, à passer par CI et
Trivy ; un run de 100 000 événements doit s'exporter sans faire enfler la
mémoire du conteneur (`mem_limit 2g`) — à mesurer, pas à supposer.

## Q2 — Le canal de retrait, pour un script autonome

**Objectif** : un script extérieur se débrouille seul, sans qu'Écho le lance.

- `GET /api/runs?statut=ARRETE&limit=1` : la dernière simulation terminée
  (existe partiellement, à vérifier).
- `GET /api/runs/{id}/export.{csv|json|xlsx}` : le jeu, sous clé d'API.
- `GET /api/analyses/attendues` : ce qu'Écho attend qu'on analyse maintenant,
  d'après la planification de Q3 — c'est ce point qui réconcilie « le script est
  autonome » et « la planification est dans Écho ».
- Le rapport revient sur l'endpoint **existant**, inchangé.
- Les **trois échecs d'échange** traités pour eux-mêmes et jamais confondus avec
  un score de 0 % : analyse attendue jamais honorée (*silence du script*),
  rapport refusé en 422 (*rapport malformé* — existe), rapport sans champ ni type
  exploitable (*rapport sans détail*).

**Test visuel** : avec `curl.exe` et la clé d'API, je récupère l'export puis je
pousse un rapport ; l'écran montre l'envoi et la réception horodatés. Je laisse
passer l'heure sans rien pousser : l'écran dit « en attente du script », pas 0 %.

**Livré avec** : un script d'exemple en Python dans `scripts/analyse/`, qui fait
le cycle complet. Il sert de référence d'intégration et d'outil témoin ; il
n'est pas le produit.

## Q3 — La planification des analyses

**Objectif** : « chaque jour à 3 h, analyse la dernière simulation terminée ».

- Table `planification_analyse` : libellé, heure, jours de la semaine, cible
  (**la dernière simulation terminée**), actif, horodatages.
- Table `analyse_attendue` : une ligne par échéance — simulation visée, créée
  le, retirée par le script le, rapport reçu le, statut
  (`ATTENDUE`, `RETIREE`, `RAPPORT_RECU`, `SILENCE`, `RAPPORT_REFUSE`).
- Un `@Scheduled` à la minute ouvre les échéances dues et marque en `SILENCE`
  celles dépassées d'un délai réglable. **Pas de Quartz** : Spring suffit.
- Aucune simulation terminée à l'heure dite → l'échéance le dit, elle n'échoue pas.

**Test visuel** : je règle une analyse à l'heure qui vient, elle apparaît en
« attendue » avec la simulation visée ; mon script la prend, le statut change.

## Q4 — L'onglet Analyse remanié

**Objectif** : l'écran `/qualite` devient le poste de pilotage.

- Trois blocs : *Planification* (créer, activer, supprimer), *Analyses*
  (l'historique des échéances et leur statut), *Résultat* (l'existant).
- Vocabulaire du cahier : `tauxDetection` s'affiche **« Taux de rappel »**, la
  précision reste la précision. Le calcul ne change pas.
- Les **anomalies ratées** et les **faux positifs** deviennent cliquables :
  chaque ligne ouvre l'événement correspondant dans Flux de données, filtré sur
  son `event_id`. C'est ce que le brief appelle « accéder aux lignes en base ».
- Mention du périmètre : deux simulations de scénarios différents ne se
  comparent pas, l'écran le dit.
- `@PreAuthorize` sur toute écriture, audit sur la création et la suppression
  d'une planification.

**Test visuel** : j'ouvre l'onglet, je vois la planification, l'historique des
analyses, le rappel et la précision ; je clique une anomalie ratée et je tombe
sur la ligne exacte que le script a manquée.

## Q5 — Les anomalies que le catalogue ne sait pas poser

**Objectif** : les dimensions du cahier que 11 corruptions de champ ne couvrent pas.

Les 11 types actuels abîment **un champ d'une ligne**. Quatre dimensions du
cahier portent sur des **relations** et n'ont aujourd'hui aucun mutator :

| Dimension | À ajouter | Pourquoi ce n'est pas un mutator ordinaire |
|---|---|---|
| Unicité floue | Doublon approximatif | Il faut écrire une **seconde ligne** proche, pas corrompre celle-ci |
| Intégrité référentielle | Référence brisée | Viser un identifiant qui n'existe dans aucun référentiel |
| Cohérence temporelle | Date de soin hors période de droits, antidatée, future | Se juge contre une autre date de la même simulation |
| Cohérence croisée | Droits actifs alors que la période est close | Contradiction entre deux champs, chacun valide seul |

À quoi s'ajoutent deux anomalies **de fichier**, qui ne peuvent pas exister dans
une colonne typée et s'injectent donc à l'écriture de l'export (Q1) :
caractères cassés / encodage, et format de date incohérent.

Hors périmètre, faute de tables : téléphone, RCCM, compte contribuable, OGD,
date de liquidation, délai de carence — ils arriveront avec la phase 2
(affiliation) et n'ajouteront que des types au catalogue.

**Test visuel** : les nouvelles familles apparaissent dans l'écran de scénario,
et je les retrouve dans le corrigé après une simulation.

---

## Ordre et dépendances

Q1 (export) puis Q2 (canal) puis Q3 (planification) puis Q4 (écran).
Q5 se branche sur Q4 pour l'affichage, mais ne dépend d'aucune des trois autres.

Q1 est le préalable de tout. Q5 est indépendant de Q2–Q4 et peut passer en
dernier, ou se glisser plus tôt si les dimensions manquantes pressent : elles
changent ce que le script a à trouver, pas la mécanique.

## Ce que ce plan laisse dehors, et c'est assumé

- **Les paliers de charge** et la mesure du temps de réponse du script (cahier 3.1).
- **Le mode poussé (push)** : décision actée, le script vient chercher.
- **La reprise après interruption** d'un gros export.
- **Les tables non intégrées** et la phase 2 affiliation.
- **B5 et B10** : volontairement absents de la numérotation du cahier.

## Hypothèses à confirmer

1. La planification est **dans Écho**, le script la lit par
   `GET /api/analyses/attendues` ; le script reste maître de son exécution.
2. « Dernière simulation terminée » = dernier `simulation_run` au statut `ARRETE`.
3. `assures` dans l'export = les assurés **concernés par le run**, pas les
   50 000 du référentiel.
4. Ajouter Apache POI est accepté (xlsx demandé explicitement).
