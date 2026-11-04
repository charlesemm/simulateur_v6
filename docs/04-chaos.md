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
- aucune facture à moitié écrite : un parcours est écrit de façon atomique et rejoué en entier si la liaison tombe ;
- le run reste `EN_COURS` : une panne de liaison est attendue jusqu'à `simulateur.ecriture.indisponibilite-max-ms`
  (défaut 120 s) avant de déclarer l'échec du run. Une vraie erreur SQL échoue après `tentatives` essais.

## 5. Résultats mesurés

Environnement : Testcontainers sur Podman/Windows, PostgreSQL 16 + Toxiproxy, file bornée de **200** événements dans les
tests (10 000 par défaut), lots de 500. Les valeurs sont celles affichées par les tests (`CHAOS-MESURE`) ; elles dépendent
de la machine et servent de repère, pas de garantie.

| Scénario | Mesure |
|---|---|
| Coupure 30 s | Débit avant 112 evt/s ; trou d'écriture 33,7 s ; file jamais au-delà de sa capacité ; **reprise en 1,4 s** après rétablissement ; débit après 119 evt/s ; 0 perte, 0 doublon |
| Reset des connexions (`RESET_PEER`, 6 s) | Run toujours `EN_COURS`, écriture reprise, 0 perte, 0 doublon |
| Latence 500 ms | Débit 114 evt/s -> **0,5 evt/s** ; retour à 104 evt/s après rétablissement ; aucune erreur fatale |
| Surcharge x20 | Débit demandé 100 parcours/s, obtenu **15,8** (normal : 5) ; service vivant ; base répond |
| Messages tronqués | Comptés ; la trace d'audit l'indique |
| Écriture seule (brique 7, sans chaos) | ~3 300 à 3 500 evt/s |

### Point de rupture

- **Débit de parcours** : sur cet environnement, le système plafonne à environ **16 parcours/s**
  (demandé 100, obtenu 15,8). Au-delà, le générateur est ralenti par la contre-pression ; il ne perd rien et ne plante pas.
- **Latence** : à 500 ms par réponse, le débit s'effondre (0,5 evt/s) parce que chaque parcours fait de nombreux
  allers-retours vers la base. Rien n'échoue, mais le débit utile est quasi nul. La rupture fonctionnelle (échec du run)
  n'arrive qu'après `indisponibilite-max-ms` de liaison coupée, soit 120 s par défaut.
- **Temps de reprise** : environ 1,4 s après le retour de la base (coupure de 30 s).

### Réserves

- Pendant la surcharge, le pic de la file mesuré est de 8/200 : c'est le **générateur** (écriture parcours par parcours)
  qui limite le débit, avant que la file ne se remplisse. La contre-pression est bien exercée mais la file n'est pas
  l'élément saturé. Cela confirme le reste à faire de la brique 7 (écriture des factures hors lot).
- Mesures obtenues avec Testcontainers sur Podman/Windows : à refaire sur le serveur cible avant d'en tirer des seuils.
- Le test `AnomaliesIntegrationTest` a échoué une fois au démarrage du conteneur (`Container startup failed`) lors d'un
  `clean verify` complet, puis est passé seul. Cause probable : saturation de Podman Desktop quand les conteneurs
  de test s'enchaînent. À surveiller.
