# Module « Qualité des données » — dossier d'entrée

Dossier **intégré le 2026-11-08**, avant tout développement. Rien n'est encore
décidé pour la v6 : ces fichiers sont la demande, pas le plan.

## Contenu du dossier

| Fichier | Ce que c'est | Statut |
|---|---|---|
| `source/Simulateur_Module_Test_Qualite_Donnees_v1.docx` | Le cahier des charges d'origine, tel que reçu | Référence, ne pas modifier |
| `01-cahier-des-charges-v1.txt` | Le texte du docx extrait (tableaux et maquettes d'écran compris), pour pouvoir le citer et le chercher | Extraction fidèle |
| `02-ce-qui-est-demande.md` | Reformulation de la demande + points tranchés le 2026-08-28 (API dans les deux sens, B5/B10 volontairement absents, tables manquantes, rejouabilité sur le fichier) | À relire, écrit pour la v5 |
| `03-decoupage-propose.md` | Découpage proposé en 8 modules livrables M1–M8, chacun avec son test visuel | **Proposition**, pas validée pour la v6 |

## Avertissement : ces deux derniers fichiers parlent de la v5

Ils ont été écrits contre `simulateur_V5` (l'Écho Python en service sur
echo.ipscnam.ci) et citent des éléments qui **n'existent pas dans ce dépôt** :
`TB_SIMULATIONS`, `TB_ANOMALIES_INJECTIONS`, `TB_REF_ANOMALIES` (13 types),
le module `mdm/` et son générateur d'identités jumelles, les 19 règles de
qualité servant d'outil témoin, l'accueil à 4 cartes dont une grisée.

Le périmètre métier, lui, est également différent : la v5 parle du parcours
bénéficiaire CMU (affiliation, cotisations, OGD) ; la v6 n'a livré que la
**phase 1 prestations**.

Il faudra donc rejouer la lecture sur l'existant de la v6 avant de planifier.

## Ce que la v6 a déjà, et sous quel nom

| Demande du cahier | Dans ce dépôt |
|---|---|
| Le corrigé (liste et emplacement des anomalies posées) | table `anomaly_injected` + `AnomalyInjector`, écrits dans la transaction du lot |
| Catalogue des types d'anomalies, activables et dosés | `anomaly/AnomalyType` : **11 types**, taux par type ou global via `AnomalyProfile` (scénario YAML) |
| Un mutator par type | `anomaly/mutators/` (11 classes testées isolément) |
| Graine déterminant le jeu produit | `core/random/SeededRandom` ; l'aléa d'injection est dérivé de l'`event_id` |
| Exécution tracée et horodatée avec ses paramètres | `simulation_run` + `scenario` |
| Rapport d'un outil extérieur et comparaison | `quality/` : `POST /api/runs/{id}/quality/report`, `GET .../quality/comparison`, tables `quality_report` / `quality_detection`, écran `/qualite` |
| Exports | CSV (écran flux de données, plafonné à 100 000 lignes) |
| Règle de taux par régime | `tarification/` (taux en base, `V5`) |

## Ce que la v6 n'a pas du tout

- **La campagne** comme objet propre (volume, graine, fichier, corrigé, rapport,
  score) : aujourd'hui il n'y a que l'exécution de simulation temps réel.
- Le **générateur en lot** séquentiel et graîné de bout en bout, exigé par la
  rejouabilité « à l'octet près » (le moteur actuel est concurrent et daté sur
  l'horloge réelle).
- Le **parcours de création en trois étapes** avec taux par type à l'écran.
- Le **marquage non désactivable** des enregistrements de test.
- Les formats **JSON** et **script SQL**, et l'empreinte du fichier.
- Le **canal API vers l'outil testé** : aujourd'hui c'est l'outil qui pousse son
  rapport vers Écho ; rien ne part d'Écho (`QualityClient` n'a jamais été écrit).
- **Rappel et précision**, les faux positifs, le détail des anomalies manquées.
- Les **paliers de charge**, la mesure du temps de réponse de l'outil testé, la
  dégradation de détection avec le volume.
- Les trois **échecs d'échange** distingués d'un score de 0 %.
- Les anomalies des dimensions **unicité floue, intégrité référentielle,
  cohérence temporelle, cohérence croisée** (les 11 types actuels sont des
  corruptions de champ, pas des incohérences inter-lignes ou inter-tables).

## Questions à trancher avant le plan

1. Le module vise-t-il le **périmètre prestations de la v6** (assurés, droits,
   factures, lignes, référentiels) ou faut-il d'abord la phase 2 affiliation,
   que le cahier suppose (cotisations, OGD, délai de carence) ?
2. **Campagne et exécution de simulation** : deux objets séparés, comme le
   propose `03-decoupage-propose.md`, ou la campagne se greffe-t-elle sur
   `simulation_run` ?
3. L'**outil témoin** : la v5 réutilisait ses 19 règles. La v6 n'a pas de
   moteur de règles. Faut-il en écrire un minimal, ou se tester contre le
   `quality/` existant retourné derrière le contrat ?
4. Le découpage M1–M8 est-il repris tel quel, et dans quel ordre par rapport à
   la phase 2 (affiliation) ?
