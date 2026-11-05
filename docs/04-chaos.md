# 04 - Chaos et crash tests

Objectif : casser volontairement l'environnement pendant un run et prouver que le système se rétablit
**sans événement perdu, sans doublon et sans facture à moitié écrite**.

## 1. Principe

- Le simulateur se connecte à PostgreSQL **via Toxiproxy** (`toxiproxy:5432`). Le chaos réseau passe par l'API de Toxiproxy
  (variable `TOXIPROXY_URL`), appelée par `ToxiproxyClient`.
- Les arrêts brutaux de conteneurs se font **depuis l'extérieur** (`scripts/chaos/`, `podman compose kill`).
  Le socket Podman/Docker n'est jamais monté dans le simulateur.
- Une action de chaos est **toujours bornée** : durée obligatoire, plafonnée par `simulateur.chaos.duree-max-secondes`
  (défaut 300 s). Elle prend fin toute seule à l'échéance, ou quand le run s'arrête.
- Chaque action laisse une trace dans la table `chaos_action` (run, type, paramètres, début, fin, motif de fin, auteur).
- Une seule action active par type à la fois.

## 2. Types d'action

| Type | Effet | Paramètres |
|---|---|---|
| `COUPURE_BASE` | Toxiproxy coupe la liaison avec PostgreSQL | `mode` : `DESACTIVER` (défaut) ou `RESET_PEER` |
| `LATENCE` | Délai ajouté à chaque réponse de PostgreSQL | `latenceMs` (max `simulateur.chaos.latence-max-ms`, 10 000), `jitterMs` |
| `SURCHARGE` | Multiplie le débit du scénario | `multiplicateur` : 5, 20 ou 100 |
| `MESSAGE_TRONQUE` | Une proportion des événements perd une partie de ses champs avant l'écriture | `proportion` dans ]0 ; 1] |

## 3. Commandes

Variables PowerShell : `$K = "<clé d'API>"`, `$RUN = "<id du run>"`. Le run doit être actif.

```powershell
# Coupure de la base pendant 30 s
curl.exe -s -X POST "http://localhost:8080/simulateur/api/runs/$RUN/chaos" `
  -H "X-API-Key: $K" -H "Content-Type: application/json" `
  -d '{"type":"COUPURE_BASE","dureeSecondes":30,"parametres":{"mode":"DESACTIVER"}}'

# Latence de 500 ms pendant 60 s
curl.exe -s -X POST "http://localhost:8080/simulateur/api/runs/$RUN/chaos" `
  -H "X-API-Key: $K" -H "Content-Type: application/json" `
  -d '{"type":"LATENCE","dureeSecondes":60,"parametres":{"latenceMs":500}}'

# Surcharge x20 pendant 60 s
curl.exe -s -X POST "http://localhost:8080/simulateur/api/runs/$RUN/chaos" `
  -H "X-API-Key: $K" -H "Content-Type: application/json" `
  -d '{"type":"SURCHARGE","dureeSecondes":60,"parametres":{"multiplicateur":20}}'

# Tout rétablir immédiatement
curl.exe -s -X DELETE "http://localhost:8080/simulateur/api/runs/$RUN/chaos" -H "X-API-Key: $K"
```

Arrêts brutaux (depuis l'hôte, pas depuis le simulateur). Sous PowerShell sans WSL, lancer les scripts avec Git Bash :

```powershell
& "C:\Program Files\Git\bin\sh.exe" scripts/chaos/kill-postgres.sh 20   # kill -9 de PostgreSQL pendant 20 s
& "C:\Program Files\Git\bin\sh.exe" scripts/chaos/kill-simulator.sh     # kill -9 du simulateur, puis redémarrage
& "C:\Program Files\Git\bin\sh.exe" scripts/chaos/tout-retablir.sh      # remet toxiproxy et les conteneurs en état
```

## 4. Garanties et vérification

Après chaque scénario, les tests vérifient (`verifierAucunePerteAucunDoublonAucuneFactureIncomplete`) :

- aucun événement perdu : tout ce que le run a émis est en base ;
- aucun doublon (clé d'événement unique, écriture `ON CONFLICT DO NOTHING`, lot + checkpoint dans une transaction) ;
- aucune facture à moitié écrite : un parcours ne touche pas la base, ses factures sont tenues en mémoire puis écrites
  (facture, lignes, historique) **dans la transaction du lot d'événements** ; jamais d'événement en base avant sa facture ;
- le run reste `EN_COURS` : une panne de liaison est attendue jusqu'à `simulateur.ecriture.indisponibilite-max-ms`
  (défaut 120 s) avant de déclarer l'échec du run. Une vraie erreur SQL échoue après `tentatives` essais.

### Chemin d'écriture (depuis la brique 10b)

```
parcours (mémoire) -> tampon du parcours -> file bornée -> lot -> UNE transaction :
                                                                  factures, lignes, historique,
                                                                  événements, anomalies, checkpoint
```

Avant la 10b, chaque parcours faisait une dizaine de requêtes une par une (hors lot), et la file ne se remplissait
jamais : le générateur était le goulot, pas la file. Désormais le générateur n'interroge la base que pour réserver un
bloc de 1000 numéros de facture, et c'est la file bornée qui exerce la contre-pression.

## 5. Résultats mesurés

Environnement : Testcontainers sur Podman/Windows (machine Podman de 2 Gio), PostgreSQL 16 + Toxiproxy, file bornée de
**200** éléments dans les tests (10 000 par défaut), lots de 500. Les valeurs sont celles affichées par les tests
(`CHAOS-MESURE`) ; elles dépendent de la machine et servent de repère, pas de garantie.

| Scénario | Avant la 10b | Après la 10b |
|---|---|---|
| Coupure 30 s | trou d'écriture 33,7 s ; file à 0/200 (le générateur était bloqué avant) ; reprise en 1,4 s | trou d'écriture 33,7 s ; **file à 200/200** (contre-pression réelle) ; reprise en **1,0 s** ; débit de rattrapage 1 171 evt/s ; 0 perte, 0 doublon, 0 facture à moitié écrite |
| Reset des connexions (`RESET_PEER`, 6 s) | 0 perte, 0 doublon | 0 perte, 0 doublon |
| Latence 500 ms | 114 -> **0,5 evt/s** | 134 -> **5,6 evt/s** (x11), retour à 137 evt/s après rétablissement ; aucune erreur fatale |
| Surcharge x20 | 100 demandés, **15,8 obtenus** (base 5/s) | base 50/s : 1 000 demandés, **155 obtenus** ; file à 200/200 ; service vivant |
| Messages tronqués | comptés, trace d'audit | inchangé |

La surcharge x20 d'une base de 5 parcours/s (100/s) passe désormais entièrement (file à 39/200) : le scénario de test a
donc été monté à 50 parcours/s pour retrouver le point de saturation.

### Point de rupture

- **Débit de parcours** : environ **150 parcours/s** (155 mesurés, contre 16 avant la 10b, soit environ x9). Au-delà, la
  file est pleine, le générateur est ralenti par la contre-pression, rien n'est perdu et le service reste vivant.
- **Latence** : à 500 ms par réponse, le débit tombe à environ 6 evt/s. Il reste plus faible qu'en régime normal
  parce que chaque lot coûte plusieurs allers-retours ; mais il n'est plus quasi nul. La rupture fonctionnelle (échec du
  run) n'arrive qu'après `indisponibilite-max-ms` de liaison coupée, soit 120 s par défaut.
- **Temps de reprise** : environ 1,0 s après le retour de la base (coupure de 30 s).

### Réserves

- Mesures obtenues avec Testcontainers sur Podman/Windows : à refaire sur le serveur cible avant d'en tirer des seuils.
- Les numéros d'un bloc de factures réservé mais non consommé sont perdus à l'arrêt (trous dans la numérotation, jamais
  de doublon).
- Le moteur ne sait toujours pas **reprendre** un run interrompu en sautant les parcours déjà écrits : le sink fournit
  `checkpoint(runId)` et les factures sont désormais dans la même transaction, la brique de reprise reste à écrire.
- `AnomaliesIntegrationTest` échouait au démarrage de son conteneur (`Container startup failed`, `localhost:2375 failed to
  respond`) dans la suite complète. Cause établie (correctif hors brique) : juste après la fin de `ChaosIntegrationTest`, la
  première requête vers l'API Podman (tube nommé `docker_engine`) est refusée, puis tout repasse. Ce n'est ni un manque
  de mémoire (la machine WSL dispose de 8 Gio, `.wslconfig`) ni un défaut du code. Correctif : `withStartupAttempts(3)`
  sur les conteneurs PostgreSQL des tests ; `clean verify` vert (294 tests), une reprise observée dans le journal.
