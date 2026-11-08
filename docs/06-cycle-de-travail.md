# Le cycle de travail : modifier, essayer en local, mettre sur le serveur

Ce document décrit **ce qu'on refait à chaque fois**. Il ne remplace pas [`05-deploiement.md`](05-deploiement.md),
qui raconte l'installation initiale du serveur — celle-là ne se fait qu'une fois. Ici, uniquement la boucle
quotidienne.

```
   MODIFIER            ESSAYER EN LOCAL              PUBLIER                 METTRE EN SERVICE
   le code       ->    podman compose          ->    git tag v1.2.0    ->    sur le serveur :
   (IntelliJ)          up -d --build                 git push --tags         .env + pull + up -d
                       localhost:8080                (GitHub construit       echo.ipscnam.ci
                                                      l'image)
```

Trois règles qui expliquent tout le reste :

1. **Le serveur ne compile jamais.** Il tire une image déjà construite et déjà testée par GitHub Actions.
2. **Ce qui se déploie porte un numéro de version.** Pas de `latest` : une production qui suit `latest` ne sait pas
   ce qu'elle exécute.
3. **Le déploiement est manuel**, volontairement. Poser un tag publie une image ; elle ne part en service que
   lorsqu'on va la chercher.

---

## A. La boucle locale (plusieurs fois par jour)

### A.1 Démarrer la pile

```powershell
cd C:\Users\charles.nguessan\simulateur_v6
podman compose up -d --build
```

Trois conteneurs : `simulateur-postgres-1`, `simulateur-toxiproxy-1`, `simulateur-simulator-1`.
L'application est sur **<http://localhost:8080/simulateur/>** (le `/simulateur` n'est pas négociable : c'est le
`context-path`, dont dépendent toutes les URL Thymeleaf — CLAUDE.md §9).

```powershell
podman compose ps
curl.exe -s localhost:8080/simulateur/actuator/health      # {"status":"UP"}
```

### A.2 Après une modification

| Ce qui a changé | Ce qu'il faut faire |
|---|---|
| Code Java, `pom.xml`, migration Flyway | `podman compose up -d --build` |
| Template Thymeleaf, CSS, JS | pareil — l'image embarque les ressources |
| Scénario YAML en local | pareil (en production, voir C.1 : ils sont montés depuis le disque) |
| Rien (reprise après un arrêt) | `podman compose start` |

> **Il n'y a pas de rechargement à chaud.** Même pour une virgule dans un fichier CSS, il faut reconstruire :
> les ressources sont dans le JAR, lui-même dans l'image. C'est le prix de « ce qui tourne en local est
> exactement ce qui tournera en production ».

Toujours **avant** de reconstruire, et obligatoirement avant de dire qu'une tâche est finie (CLAUDE.md §12) :

```powershell
.\mvnw.cmd clean verify
```

### A.3 Vérifier que la nouvelle version tourne vraiment

Le piège : `up -d --build` affiche `Using cache` un peu partout et on croit avoir déployé alors qu'on regarde
encore l'ancien écran. La preuve se lit dans les journaux, pas dans le navigateur :

```powershell
podman compose ps                                  # « Up 30 seconds » sur simulator, pas « Up 2 days »
podman compose logs simulator | Select-String "Started SimulatorApplication"
```

Et quand une migration a été ajoutée :

```powershell
podman compose logs simulator | Select-String "Migrating schema|Successfully applied"
```

### A.4 Arrêter

```powershell
podman compose stop          # arrete tout, GARDE la base
podman compose start         # repart ou on en etait
```

> **`podman compose down -v` efface la base** (volume `pgdata`) : référentiel, factures, événements, comptes,
> et la table `flyway_schema_history`. C'est utile pour repartir propre — c'est permis tant que rien n'est en
> production (CLAUDE.md §7) — mais ce n'est pas « arrêter ». Au redémarrage suivant tout est recréé de zéro,
> y compris le compte administrateur initial.

Si Grafana et Prometheus se sont lancés au passage (profil `observability`), ils ne sont pas couverts par un
`stop` ordinaire :

```powershell
podman compose --profile observability stop
```

---

## B. Publier une version et la mettre sur le serveur

C'est la partie répétitive. Elle se fait en deux temps : **GitHub construit**, puis **le serveur tire**.

### B.1 Sur le poste — poser la version

```powershell
cd C:\Users\charles.nguessan\simulateur_v6

.\mvnw.cmd clean verify                      # VERT, sans exception

# 1. enregistrer le travail sur sa branche (une branche par brique, CLAUDE.md §12)
git add -A
git commit -m "feat(ui): ..."                # convention : feat|fix|chore(portee): sujet
git push origin <ma-branche>

# 2. fusionner dans main, puis poser le numero de version
#    pom.xml ligne 16 :  <version>1.1.0</version>
git checkout main
git pull
git add pom.xml
git commit -m "chore: version 1.1.0"
git tag v1.1.0
git push origin main --tags
```

Quel numéro ? `MAJEUR.MINEUR.CORRECTIF` : un correctif d'affichage → `1.0.1` ; un écran ou une fonction en plus →
`1.1.0` ; une rupture (migration non réversible, changement d'API) → `2.0.0`.

Onglet **Actions** du dépôt → chantier *Image*. Il publie, en quelques minutes :

- `ghcr.io/charlesemm/simulateur_v6:1.1.0`
- `ghcr.io/charlesemm/simulateur_v6:sha-a1b2c3d`

> **Pour essayer une image sans poser de tag** : Actions → *Image* → *Run workflow*. Seule la forme
> `sha-<court>` est publiée. Pratique pour une vérification sur le serveur avant d'officialiser un numéro.

### B.2 Sur le serveur — tirer la nouvelle image

```bash
ssh adm-mnguessan@10.10.4.165
cd /opt/echo6

# 1. pointer la nouvelle version
sudo vi .env                 # ECHO_IMAGE=ghcr.io/charlesemm/simulateur_v6:1.1.0

# 2. tirer, puis relancer le seul service concerne
sudo podman-compose -f compose.prod.yaml pull simulator
sudo podman-compose -f compose.prod.yaml up -d simulator

# 3. regarder demarrer
sudo podman logs --tail 60 echo6_simulator_1
```

On relance **`simulator` seul** : PostgreSQL et Toxiproxy n'ont pas changé, inutile de couper la base à chaque
livraison.

Ce qu'on cherche dans les journaux, dans cet ordre : les migrations Flyway éventuelles, puis
`Started SimulatorApplication`. Une pile d'exception à la place, c'est presque toujours une migration qui a
échoué — Flyway tourne **dans** l'application : si elle échoue, l'application ne démarre pas.

### B.3 Vérifier

```bash
# sur le serveur
curl -s http://127.0.0.1:8088/simulateur/actuator/health                            # {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8088/simulateur/login     # 200
```

```bash
# depuis le reseau IPS-CNAM
curl -sf https://echo.ipscnam.ci/simulateur/actuator/health
```

Puis dans un navigateur : se connecter, lancer une simulation courte, **et regarder le tableau de bord bouger
tout seul**. Des chiffres figés pendant qu'une simulation tourne, ce n'est pas une panne de l'application : c'est
le flux SSE mis en tampon par nginx (bloc `location /simulateur/flux`, voir `05-deploiement.md` section 7).

### B.4 Le retour arrière

Il n'y a rien à reconstruire : l'image précédente est toujours dans le registre.

```bash
sudo vi /opt/echo6/.env      # ECHO_IMAGE=...:1.0.0  (le numero d'avant)
cd /opt/echo6
sudo podman-compose -f compose.prod.yaml up -d simulator
```

> **Sauf si la version retirée a posé une migration Flyway.** Une base migrée en V12 ne redescend pas en V11.
> L'ancienne image démarrera quand même — Flyway tolère un schéma plus avancé — mais les colonnes ajoutées
> resteront inutilisées. Un retour arrière après une migration **destructrice** (colonne supprimée, type changé)
> exige une restauration de sauvegarde. D'où la règle : une migration n'enlève et ne renomme rien tant que la
> version qui l'utilise n'est pas confirmée en service.

---

## C. Les cas qui sortent de la boucle

### C.1 Un scénario YAML a changé

Les scénarios sont **montés depuis le disque du serveur**, pas embarqués dans l'image. Les modifier n'a donc rien
à voir avec une livraison : ni tag, ni image, ni redémarrage.

```powershell
scp -r scenarios/prestations adm-mnguessan@10.10.4.165:/opt/echo6/scenarios/
```

```bash
# OBLIGATOIRE apres chaque scp : le conteneur tourne sous l'uid 10001, pas sous votre compte
sudo chmod -R a+rX /opt/echo6/scenarios
```

Sans ce `chmod`, les fichiers sont là mais invisibles de l'application : l'écran de lancement n'affiche aucun
scénario (panne du 07/11/2026, voir `05-deploiement.md` section 4).

### C.2 Une migration Flyway a été ajoutée

Rien de particulier à faire : elle part dans l'image et s'applique au démarrage. Mais **relire la règle** : un
script déjà appliqué ne se modifie jamais, on en ajoute un nouveau (CLAUDE.md §7). Et vérifier dans les journaux
qu'elle est bien passée (B.2).

### C.3 Le vhost nginx a changé

Rare, et c'est la seule manipulation qui peut couper **aussi Socle et AnyFiX** sur ce serveur partagé.

```bash
sudo cp /etc/nginx/conf.d/echo.ipscnam.ci.conf ~/vhost.sauvegarde    # TOUJOURS
sudo cp /tmp/echo.ipscnam.ci.conf /etc/nginx/conf.d/echo.ipscnam.ci.conf
sudo nginx -t                        # JAMAIS de reload sans -t vert
sudo systemctl reload nginx          # reload, jamais restart
```

---

## D. Aide-mémoire

| Je veux… | Commande |
|---|---|
| Démarrer en local | `podman compose up -d --build` |
| Voir l'application | <http://localhost:8080/simulateur/> |
| Voir les journaux | `podman compose logs -f simulator` |
| Arrêter sans rien perdre | `podman compose stop` |
| Repartir | `podman compose start` |
| Repartir **de zéro** (efface la base) | `podman compose down -v` puis `up -d --build` |
| Lancer les tests | `.\mvnw.cmd clean verify` |
| Publier une version | `git tag vX.Y.Z` puis `git push origin main --tags` |
| Mettre à jour le serveur | `.env` → `pull simulator` → `up -d simulator` |
| Revenir en arrière | remettre l'ancien `ECHO_IMAGE`, `up -d simulator` |

| | Local | Serveur |
|---|---|---|
| Dossier | `C:\Users\charles.nguessan\simulateur_v6` | `/opt/echo6` |
| Projet compose | `simulateur` | `echo6` |
| Fichier compose | `compose.yaml` (+ `compose.override.yaml`) | `compose.prod.yaml` |
| Conteneur applicatif | `simulateur-simulator-1` | `echo6_simulator_1` |
| Port | 8080 | 8088 en boucle locale, 443 via nginx |
| Adresse | `localhost:8080/simulateur/` | `https://echo.ipscnam.ci/simulateur/` |
| Outil | `podman compose` | `sudo podman-compose -f compose.prod.yaml` |
