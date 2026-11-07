# Mettre la v6 en service à la place de la v5

`echo.ipscnam.ci` sert aujourd'hui **Écho v5** (Python/FastAPI). Ce document décrit comment y mettre **Écho v6**
(Java/Spring Boot) **sans rien réinventer** : même serveur, même nginx, même certificat, même GHCR, même
`podman-compose` sous `sudo`, même `podman-restart.service`. Le dispositif de la v5 est repris tel quel.

La bascule se résume, au moment décisif, à **changer un numéro de port dans le vhost nginx**.

---

## 1. État des lieux : ce qui tourne déjà

| | Écho v5 (en service) | Écho v6 (à installer) |
|---|---|---|
| Technologie | Python / FastAPI / Alembic | Java 21 / Spring Boot / Flyway |
| Image | `ghcr.io/charlesemm/simulateur_v5` | `ghcr.io/charlesemm/simulateur_v6` |
| Dossier serveur | `/opt/echo` | `/opt/echo6` |
| Nom de projet compose | `echo` | `echo6` |
| Volume de la base | `echo_pgdata` | `echo6_pgdata` |
| Base / compte | `echo_db` / `echo` | `simulateur` / `simulateur` |
| Port application (boucle locale) | **8087** | **8088** |
| Port PostgreSQL (boucle locale) | 5439 | 5440 |
| Chemin servi | `/` (racine) | `/simulateur/` |
| Services | postgres, migrations, api | postgres, **toxiproxy**, simulator |
| Sauvegardes | `/var/sauvegardes/echo`, 2 h 15 | `/var/sauvegardes/echo6`, 3 h 15 |

Inchangé, et c'est tout l'intérêt : `srv-gouv`, le compte `adm-mnguessan`, nginx en portier avec le wildcard
`*.ipscnam.ci`, GHCR comme registre, `sudo podman-compose`, `podman-restart.service` au démarrage.

> **`srv-gouv` est une machine partagée** avec Socle et AnyFiX. Les ports 80, 443, 8000, 8080 et 5432 y sont déjà
> pris. Toute erreur sur nginx coupe **aussi Socle** : d'où `nginx -t` avant chaque `reload`, et `reload` plutôt que
> `restart`.

### Trois choses que la v6 fait différemment

1. **Flyway s'exécute dans l'application**, au démarrage. Il n'y a pas de service `migrations` séparé à lancer puis à
   surveiller comme en v5. La contrepartie : si une migration échoue, c'est l'application qui ne démarre pas.
2. **Toxiproxy est dans la pile de production.** L'application ne joint jamais PostgreSQL en direct. Ce n'est pas un
   outil de développement : les crash tests sont une fonction du produit.
3. **L'application vit sous `/simulateur`** (`context-path` dans `application.yml`, dont dépendent toutes les URL
   Thymeleaf). Le vhost redirige donc `https://echo.ipscnam.ci/` vers `/simulateur/` : l'adresse que les gens tapent
   ne change pas. On ne touche pas au `context-path` — c'est une règle du projet (CLAUDE.md §9).

### La base de la v5 n'est pas reprise

`echo_db` porte un schéma Alembic ; la v6 attend un schéma Flyway. Ce ne sont pas deux versions d'une même base,
ce sont **deux bases différentes**. La v6 part donc sur un volume neuf, et les campagnes de la v5 restent dans
`echo_pgdata`, intactes.

Ce n'est pas une perte sèche : les données sont synthétiques et se régénèrent en relançant un scénario. Mais si
l'historique des campagnes v5 compte pour quelqu'un, **archiver un dump avant de retirer la v5** (section 10).

---

## 2. Le principe : cohabiter, puis basculer

On n'arrête pas la v5 pour installer la v6. Les deux tournent côte à côte sur des ports différents, et la v6 reste
**invisible du réseau** jusqu'à ce que nginx la désigne.

```
   AVANT                            PENDANT                          APRES
   -----                            -------                          -----
   nginx :443                       nginx :443                       nginx :443
     |                                |                                |
     v                                v                                v
   8087  v5  (en ligne)             8087  v5  (en ligne)             8088  v6  (en ligne)
                                    8088  v6  (demarree, joignable   8087  v5  (encore la,
                                          seulement par tunnel SSH)        arretable, retour
                                                                            arriere immediat)
```

La mise en service, c'est **une ligne** du vhost : `proxy_pass http://127.0.0.1:8087` devient `:8088`.
Le retour arrière, c'est la même ligne dans l'autre sens. Moins de dix secondes, sans toucher aux conteneurs.

---

## 3. Partie 1 — Publier l'image (sur ton poste / dans GitHub)

La v6 n'avait pas de publication d'image : elle existe désormais
(`.github/workflows/image.yml`). Elle se déclenche sur un tag `v*`.

```powershell
# sur le poste de developpement
.\mvnw.cmd clean verify             # doit etre vert : 403 tests

# aligner la version du pom sur le numero qu'on va poser
#   pom.xml :  <version>0.1.0-SNAPSHOT</version>  ->  <version>1.0.0</version>
git add pom.xml
git commit -m "chore: version 1.0.0"

git tag v1.0.0
git push origin main --tags
```

Onglet **Actions** du dépôt → chantier *Image* → il publie :

- `ghcr.io/charlesemm/simulateur_v6:1.0.0`
- `ghcr.io/charlesemm/simulateur_v6:sha-a1b2c3d`

Pas de `latest`, volontairement : une production qui suit `latest` ne sait pas ce qu'elle exécute.

> **Première publication :** le paquet GHCR naît **privé**. Le serveur s'identifie déjà sur `ghcr.io` pour la v5,
> mais avec un jeton qui ne couvre pas forcément le nouveau dépôt. Si le `pull` de la section 5 répond
> `unauthorized`, soit rendre le paquet visible (`github.com/users/charlesemm/packages` → `simulateur_v6` →
> *Package settings* → *Change visibility*), soit refaire `sudo podman login ghcr.io` avec un jeton classique
> portant la portée `read:packages`.

---

## 4. Partie 2 — Préparer `/opt/echo6` (sur le serveur)

```bash
ssh adm-mnguessan@10.10.4.165

sudo mkdir -p /opt/echo6/docker /opt/echo6/scenarios
# Sans deux-points : sur srv-gouv (rattachée à l'Active Directory) le groupe
# principal est « utilisateurs du domaine », qui contient TOUS les comptes de
# l'institution. Lui donner l'écriture ouvrirait /opt/echo6 à tout le monde.
# -R : « sudo mkdir -p » a cree docker/ et scenarios/ en root. Sans le -R, la
# copie des fichiers DANS ces sous-dossiers est refusee (« Permission denied »)
# alors que /opt/echo6 lui-meme est bien a vous.
sudo chown -R "$USER" /opt/echo6
ls -ld /opt/echo6 /opt/echo6/docker /opt/echo6/scenarios    # adm-mnguessan sur les TROIS lignes
```

Les quatre fichiers à déposer, depuis le poste (PowerShell) :

```powershell
cd C:\Users\charles.nguessan\simulateur_v6
scp deploiement/compose.prod.yaml  adm-mnguessan@10.10.4.165:/opt/echo6/
scp deploiement/.env.exemple       adm-mnguessan@10.10.4.165:/opt/echo6/
scp deploiement/sauvegarde.sh      adm-mnguessan@10.10.4.165:/opt/echo6/
scp docker/toxiproxy.json          adm-mnguessan@10.10.4.165:/opt/echo6/docker/
# Pas de joker ici : PowerShell ne developpe pas « scenarios/* » pour une
# commande externe, scp recevrait les deux caracteres tels quels.
scp -r scenarios/prestations       adm-mnguessan@10.10.4.165:/opt/echo6/scenarios/
```

### Rendre `scenarios/` lisible par le conteneur

**À faire après le `scp -r`, sinon l'écran de lancement répond 500.** Le conteneur ne tourne pas sous votre
compte : le `Containerfile` crée l'utilisateur `simulateur` (**uid 10001**) et l'application s'exécute sous lui.
Or `scp` recrée `prestations/` avec les droits du poste d'origine, souvent `rwx------` : l'uid 10001 ne peut
alors pas ouvrir le dossier.

```bash
# Les dossiers doivent être traversables, les fichiers lisibles, par « autrui ».
sudo chmod -R a+rX /opt/echo6/scenarios
ls -lR /opt/echo6/scenarios        # « r-x » et « r--  » en dernière position
```

> Panne du 07/11/2026 : sans ce `chmod`, `/simulateur/lancement` répondait **500**
> (`AccessDeniedException: /app/scenarios/prestations`). L'application ne tombe plus pour autant — elle signale
> le dossier dans ses journaux et l'ignore — mais **aucun scénario de fichier n'apparaît** tant que les droits
> ne sont pas posés.

Le vhost nginx se copie séparément, et seulement au moment de la bascule (section 7).

### Le `.env`

```bash
cd /opt/echo6
sudo cp .env.exemple .env
sudo chmod 600 .env

# generer les trois secrets, un par un
openssl rand -base64 32 | tr -d '/+=' | cut -c1-32

sudo vi .env
```

Six lignes à remplir : `ECHO_IMAGE` (le tag publié à la section 3), `DB_NAME`, `DB_USER`, puis `DB_PASSWORD`,
`API_KEY`, `ADMIN_INITIAL_PASSWORD`.

> **`DB_PASSWORD` est fixé au tout premier démarrage**, dans le volume `echo6_pgdata`. Le changer ensuite dans
> `.env` ne change pas celui de la base : l'application ne se connecterait plus. Le choisir une bonne fois.
>
> Toutes ces variables, sauf `ADMIN_INITIAL_PASSWORD`, sont en forme `:?` dans `compose.prod.yaml` : s'il en manque
> une — **ou si elle est vide** —, le lancement échoue. Un démarrage silencieux sur un mot de passe de
> développement serait pire qu'un échec.
>
> **Mais `podman-compose` 1.0.6 ne dit pas laquelle.** Il affiche une pile Python se terminant par
> `RuntimeError: absent du .env - voir docs/05-deploiement.md section 4`, sans nommer la variable (contrairement à
> `docker compose`). Pour la trouver :
>
> ```bash
> sudo grep -nE '^[A-Z_]+=$' /opt/echo6/.env     # toute ligne qui ressort est coupable
> ```

### SELinux

Les deux volumes montés (`toxiproxy.json`, `scenarios/`) portent `:Z` dans `compose.prod.yaml` : Podman pose
lui-même le contexte SELinux. Rien à faire — mais si Toxiproxy démarre en se plaignant de ne pas lire sa
configuration, c'est là qu'il faut regarder :

```bash
sudo ausearch -m avc -ts recent | tail -20
```

---

## 5. Partie 3 — Démarrer la v6 à côté de la v5

```bash
cd /opt/echo6

sudo podman-compose -f compose.prod.yaml pull      # si « unauthorized » : voir l'encadre de la section 3
sudo podman-compose -f compose.prod.yaml up -d
```

> **`podman-compose` 1.0.6 ignore les conditions `depends_on`** (écart connu, documenté par la v5). Un `up -d`
> global peut donc lancer l'application avant que PostgreSQL ne soit prêt. La v6 y résiste : le moteur attend la
> base (`indisponibilite-max-ms`), et Spring réessaie la connexion. Si l'application échoue quand même au premier
> coup, un second `up -d` suffit — la base, elle, est prête.

```bash
sudo podman-compose -f compose.prod.yaml ps        # trois conteneurs : postgres, toxiproxy, simulator
sudo podman logs --tail 40 echo6_simulator_1       # Flyway doit annoncer V1 a V11
```

Ce qu'on cherche dans les journaux : `Successfully applied 11 migrations`, puis `Started SimulateurApplication`.

---

## 6. Partie 4 — Vérifier avant que quiconque ne la voie

La v6 n'est publiée que sur `127.0.0.1:8088` : rien sur le réseau ne l'atteint. On passe par un tunnel SSH.

```bash
# sur le serveur : l'application repond-elle ?
curl -s http://127.0.0.1:8088/simulateur/actuator/health     # attendu : {"status":"UP"}

# l'API refuse sans cle, accepte avec
curl -s -o /dev/null -w '%{http_code}\n'                             http://127.0.0.1:8088/simulateur/api/runs   # 401
curl -s -o /dev/null -w '%{http_code}\n' -H 'X-API-Key: LA_CLE'      http://127.0.0.1:8088/simulateur/api/runs   # 200

# la v5 est-elle toujours debout ? (on ne l'a pas touchee, on verifie quand meme)
curl -s http://127.0.0.1:8087/health
```

Puis, **depuis le poste Windows**, un tunnel pour voir l'interface dans un navigateur :

```powershell
ssh -L 8088:127.0.0.1:8088 adm-mnguessan@10.10.4.165
# puis ouvrir  http://localhost:8088/simulateur/
```

C'est le moment de tout essayer, pendant que la v6 n'est encore visible de personne : connexion avec
`ADMIN_INITIAL_PASSWORD`, changement imposé du mot de passe, lancement d'un scénario court, et surtout **le tableau
de bord doit bouger** — c'est le test du SSE.

> Le SSE fonctionnera forcément ici, puisque le tunnel ne met rien en tampon. C'est **après** la bascule nginx qu'il
> faut le revérifier : c'est là que `proxy_buffering off` entre en jeu.

---

## 7. Partie 5 — La bascule

Tout ce qui précède est réversible sans que personne ne s'en aperçoive. Cette section-ci est visible.

```bash
# 1. SAUVEGARDER le vhost de la v5 — c'est le retour arriere
sudo cp /etc/nginx/conf.d/echo.ipscnam.ci.conf ~/echo-v5-vhost.conf.sauvegarde
ls -l ~/echo-v5-vhost.conf.sauvegarde

# 2. deposer celui de la v6
#    (depuis le poste : scp deploiement/nginx/echo.ipscnam.ci.conf adm-mnguessan@10.10.4.165:/tmp/)
sudo cp /tmp/echo.ipscnam.ci.conf /etc/nginx/conf.d/echo.ipscnam.ci.conf

# 3. VALIDER LA SYNTAXE AVANT DE RECHARGER. Une erreur ici couperait aussi Socle.
sudo nginx -t

# 4. recharger — reload, jamais restart
sudo systemctl reload nginx
```

Vérification immédiate, depuis un navigateur du réseau IPS-CNAM :

```bash
curl -s -o /dev/null -w '%{http_code} -> %{redirect_url}\n' https://echo.ipscnam.ci/      # 301 -> /simulateur/
curl -sf https://echo.ipscnam.ci/simulateur/actuator/health                               # {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}\n' https://echo.ipscnam.ci/simulateur/login         # 200
```

Puis dans un navigateur : se connecter, lancer une simulation, **et regarder le tableau de bord se mettre à jour
tout seul**. Si les chiffres restent figés alors que la simulation tourne, c'est le tampon nginx sur le flux SSE :
vérifier que le bloc `location /simulateur/flux` est bien présent et placé **avant** `location /simulateur/`.

### Le retour arrière

```bash
sudo cp ~/echo-v5-vhost.conf.sauvegarde /etc/nginx/conf.d/echo.ipscnam.ci.conf
sudo nginx -t && sudo systemctl reload nginx
```

La v5 n'a pas été arrêtée, sa base n'a pas été touchée : elle répond de nouveau immédiatement. C'est précisément
pour ça qu'on ne l'arrête pas tout de suite.

---

## 8. Partie 6 — Les gestes d'après

### Vider le mot de passe initial

Une fois le compte administrateur créé et son mot de passe changé depuis l'interface :

```bash
sudo vi /opt/echo6/.env            # ADMIN_INITIAL_PASSWORD=
cd /opt/echo6 && sudo podman-compose -f compose.prod.yaml up -d simulator
```

### Survivre à un redémarrage

Le service existe déjà pour la v5 — et comme `compose.prod.yaml` déclare `restart: always`, les conteneurs de la v6
entrent bien dans son filtre. À vérifier quand même :

```bash
sudo systemctl is-enabled podman-restart.service        # attendu : enabled
```

> **C'est le piège le plus coûteux de ce serveur**, et la raison du `restart: always`.
> `podman-restart.service` lance `podman start --all --filter restart-policy=always`. Un conteneur en
> `unless-stopped` **n'entre pas dans ce filtre** : la machine redémarre, revient en ligne, et Écho ne démarre pas —
> alors que le service, lui, est bien actif. Personne ne s'en aperçoit avant qu'un utilisateur ne se plaigne.

La seule vérification qui prouve quelque chose, à faire pendant que personne ne dépend du service :

```bash
sudo reboot
# attendre deux minutes, se reconnecter, puis :
curl -sf https://echo.ipscnam.ci/simulateur/actuator/health
```

Si la réponse est `UP` **sans que tu aies rien relancé**, le déploiement tient debout tout seul.

### Les sauvegardes

```bash
sudo chmod +x /opt/echo6/sauvegarde.sh
sudo /opt/echo6/sauvegarde.sh                  # essai immediat : doit ecrire un fichier non vide
ls -lh /var/sauvegardes/echo6/

sudo crontab -e
# ajouter, SANS toucher a la ligne de la v5 :
15 3 * * * /opt/echo6/sauvegarde.sh >> /var/log/echo6-sauvegarde.log 2>&1
```

3 h 15 et non 2 h 15 : la v5 sauvegarde à 2 h 15, et deux `pg_dump` simultanés sur une machine partagée n'ont rien
à s'apporter.

Restauration :

```bash
cd /opt/echo6
set -a; . ./.env; set +a
gunzip -c /var/sauvegardes/echo6/echo6_20261107_0315.sql.gz \
  | sudo podman-compose -f compose.prod.yaml exec -T postgres psql -U "$DB_USER" -d "$DB_NAME"
```

**Une sauvegarde non testée n'est pas une sauvegarde.** Faire une restauration à blanc une fois, et le noter dans
`docs/journal.md`.

### La clé d'API

`API_KEY` est la valeur attendue dans l'en-tête `X-API-Key`. Elle est stockée hachée côté serveur
(`ApiKeyService`, SHA-256) ; la ligne du `.env` est la seule copie en clair. À transmettre à l'équipe de l'outil de
qualité par un canal sûr — pas par courriel, pas par messagerie.

---

## 9. Les mises à jour suivantes

Une fois la v6 en place, mettre à jour est exactement la routine de la v5 :

```bash
cd /opt/echo6
sudo /opt/echo6/sauvegarde.sh                                   # d'abord. Flyway ne defait pas une migration.
sudo vi .env                                                     # ECHO_IMAGE=...:1.0.1
sudo podman-compose -f compose.prod.yaml pull
sudo podman-compose -f compose.prod.yaml up -d
curl -sf http://127.0.0.1:8088/simulateur/actuator/health
```

`pull` télécharge la nouvelle image, `up -d` remplace les conteneurs par des neufs bâtis dessus.
**Les volumes ne sont pas touchés : la base est conservée.**

**Revenir en arrière** : remettre l'ancienne valeur dans `ECHO_IMAGE`, relancer `up -d`. C'est tout l'intérêt
d'épingler un numéro plutôt que de suivre `latest`.

Mais attention : le retour arrière ramène **le code**, pas le **schéma**. Flyway ne défait pas une migration. Si la
version fautive a joué un `V12__....sql`, l'ancienne application refusera de démarrer sur un schéma plus récent — il
faut alors restaurer le dump pris juste avant. D'où la première ligne.

**Le ménage**, une fois par trimestre : `sudo podman image prune -a`.

> **À faire un jour :** la v5 a un chantier `deploy.yml` qui déroule cette routine en SSH depuis GitHub Actions
> (déclenchement manuel, compte `echo-deploy`, `sudo` limité par `sudoers.d`). Le même pour la v6 est à écrire en
> reprenant celui de la v5 — en remplaçant le service `migrations` par une simple attente de `/actuator/health`,
> puisque Flyway tourne dans l'application. Tant qu'il n'existe pas, la routine ci-dessus se tape à la main.

---

## 10. Retirer la v5 (pas tout de suite)

**Laisser tourner la v5 au moins une semaine** après la bascule. Elle ne coûte qu'un peu de mémoire, et elle est le
seul retour arrière qui ne demande aucune réflexion.

Quand la v6 a fait ses preuves :

```bash
# 1. archiver la base de la v5 — c'est irreversible ensuite
sudo /opt/echo/sauvegarde.sh
sudo cp /var/sauvegardes/echo/echo_<le_plus_recent>.sql.gz /var/sauvegardes/archive-v5-definitive.sql.gz

# 2. arreter la v5 — SANS -v : le -v effacerait le volume
cd /opt/echo && sudo podman-compose -f compose.prod.yaml down

# 3. verifier que la v6 est seule en ligne
curl -sf https://echo.ipscnam.ci/simulateur/actuator/health
```

Le volume `echo_pgdata` reste sur le disque, et c'est voulu : ne le supprimer que lorsque l'archive de l'étape 1 a
été restaurée pour de vrai ailleurs.

```bash
sudo podman volume ls           # echo_pgdata doit encore apparaitre
```

---

## 11. Les pièges de ce serveur

**Le certificat expire le 20/11/2026.** C'est dans deux semaines. Il couvre `*.ipscnam.ci`, donc Socle, AnyFiX **et**
Écho : son renouvellement n'est pas lié à cette bascule, mais il la rendrait invisible si on l'oubliait. Les éléments
sont dans `OneDrive - IPSCNAM/Documents/ipscnam SSL 2026` (dont la clé privée : ne jamais la verser dans le dépôt).
**À relancer maintenant, indépendamment de ce déploiement.**

**Les ports sont rares.** 80, 443, 8000, 8080, 5432 sont pris ; 8087 et 5439 sont à la v5 ; la v6 prend 8088 et 5440.
Avant d'en ajouter un : `ss -ltnp | grep <port>`. Le symptôme d'un port occupé est
`rootlessport ... address already in use`.

**`sudo` partout.** Les conteneurs tournent en mode privilégié. Un `podman-compose` lancé sans `sudo` ne les voit
pas — et, pire, en créerait d'autres, à vide.

**nginx est partagé.** `nginx -t` avant chaque `reload`, et `reload` jamais `restart`. Une erreur de syntaxe dans le
vhost d'Écho coupe Socle.

**`restart: always`, pas `unless-stopped`.** Voir l'encadré de la section 8.

**Les mesures de performance sont à refaire ici.** Le point de rupture connu (~150 parcours/s, reprise 1,0 s après
coupure) a été mesuré sous Testcontainers, sur Podman/Windows, avec 2 Gio. Ces chiffres ne valent rien pour
`srv-gouv`. `mem_limit: 2g` et `cpus: 2.0` sont des valeurs de départ, à revoir après mesure sur une machine
partagée avec deux autres applications.

**Toxiproxy en production.** Une toxine oubliée ressemble à une panne d'infrastructure.
`scripts/chaos/tout-retablir.sh` et le bouton « Tout rétablir » existent pour ça : les passer après toute séance de
crash test, et dans le doute avant une démonstration. Les actions de chaos sont tracées dans `chaos_action` et dans
le journal d'audit : regarder cette table **avant** de soupçonner le réseau.

**Aucune donnée réelle.** La règle absolue du projet vaut aussi en production : `DB_URL` pointe sur le PostgreSQL de
sa propre pile, via Toxiproxy, et rien d'autre. Jamais de connexion à une base CNAM.

---

## 12. Mémo

| Besoin | Commande (sur `srv-gouv`) |
|---|---|
| Voir ce qui tourne | `cd /opt/echo6 && sudo podman-compose -f compose.prod.yaml ps` |
| Journaux | `sudo podman logs -f echo6_simulator_1` |
| Redémarrer l'application | `cd /opt/echo6 && sudo podman-compose -f compose.prod.yaml up -d simulator` |
| Mettre à jour | `.env` → `pull` → `up -d` (section 9) |
| Revenir à la version d'avant | remettre l'ancien `ECHO_IMAGE`, `up -d` |
| **Revenir à la v5** | `sudo cp ~/echo-v5-vhost.conf.sauvegarde /etc/nginx/conf.d/echo.ipscnam.ci.conf && sudo nginx -t && sudo systemctl reload nginx` |
| Santé (local) | `curl -s http://127.0.0.1:8088/simulateur/actuator/health` |
| Santé (réelle) | `curl -sf https://echo.ipscnam.ci/simulateur/actuator/health` |
| Version déployée | `grep ECHO_IMAGE /opt/echo6/.env` |
| Sauvegarder maintenant | `sudo /opt/echo6/sauvegarde.sh` |
| Console SQL | `cd /opt/echo6 && sudo podman-compose -f compose.prod.yaml exec postgres psql -U simulateur -d simulateur` |
| Annuler tous les chaos | `scripts/chaos/tout-retablir.sh` |

**`podman-compose down -v` efface le volume.** C'est la commande de remise à zéro du poste de développement. Sur
`srv-gouv`, elle détruit toutes les simulations enregistrées. Ne jamais la taper ici.
