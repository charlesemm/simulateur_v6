# Mettre la v6 en service à la place de la v5

`echo.ipscnam.ci` sert aujourd'hui **Écho v5** (Python/FastAPI). Ce document décrit comment y mettre **Écho v6**
(Java/Spring Boot) **sans rien réinventer** : même serveur, même nginx, même certificat, même GHCR, même
`podman-compose` sous `sudo`, même `podman-restart.service`. Le dispositif de la v5 est repris tel quel.

La bascule se résume, au moment décisif, à **changer un numéro de port dans le vhost nginx**.

> **Une fois la v6 en service, ce document ne resservira plus.** Le cycle de tous les jours — relancer en local,
> publier une version, mettre à jour le serveur — est dans [`06-cycle-de-travail.md`](06-cycle-de-travail.md).

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

## 9 bis. Monter la base de V12 à V20 : d'abord sur une copie

La base du serveur est à **V12** (v1.1.0) et contient des simulations. La version suivante lui fera jouer V13 à
V20 d'un coup, au démarrage de l'application. Trois de ces scripts touchent des données : V16 (contraintes des
anomalies), V17 (renommage de 23 tables en `TB_ECHO_*`) et V19 (villes et spécialités remplacées par des codes).

**Ce qu'on sait déjà**, par `MonteeV12IntegrationTest` (une base V12 remplie, migrée jusqu'à V20) :

- aucune ligne perdue, aucune clé étrangère orpheline, les anomalies des 11 types d'origine passent les
  contraintes de V16 ;
- V19 avait un défaut, **corrigé le 13/11/2026 avant tout passage sur le serveur** : sur une base remplie, il
  rattachait **toutes** les villes à Abidjan ;
- **une valeur inconnue des tables de valeurs ne bloque pas V19** : une ville est rattachée à Abidjan (`D109`),
  une spécialité à Médecine générale (`MG`). Pour ne pas le subir en silence, **la procédure refuse de migrer**
  tant que `scripts/db/controle-avant-montee.sql` renvoie une ligne. Les 15 villes et 10 spécialités que la
  v1.1.0 a pu écrire sont toutes reconnues : sur la base du serveur, le contrôle doit revenir vide.

**Flyway ne défait pas une migration.** D'où l'ordre, qui ne se discute pas : sauvegarder, restaurer sur une
copie jetable, migrer la copie, contrôler, et **seulement ensuite** la vraie base.

### Étape 0 — sur le poste : déposer les fichiers

Depuis la version qu'on va déployer (`git switch --detach vX.Y.Z`) :

```powershell
# poste de developpement, PowerShell, a la racine du depot
ssh adm-mnguessan@10.10.4.165 "mkdir -p /tmp/echo6-montee"
scp scripts/db/controle-avant-montee.sql scripts/db/comptes-par-table.sql scripts/db/cles-sans-index.sql adm-mnguessan@10.10.4.165:/tmp/echo6-montee/
scp -r src/main/resources/db/migration adm-mnguessan@10.10.4.165:/tmp/echo6-montee/
```

### Étape 1 — sur le serveur : sauvegarder

```bash
cd /opt/echo6
sudo /opt/echo6/sauvegarde.sh                       # ecrit /var/sauvegardes/echo6/echo6_AAAAMMJJ_HHMM.sql.gz
DUMP=$(ls -t /var/sauvegardes/echo6/echo6_*.sql.gz | head -1); echo "$DUMP"; ls -lh "$DUMP"   # non vide
```

### Étape 2 — restaurer sur une copie jetable

Un PostgreSQL à part, sur un réseau à part, **sans aucun port publié**. Il ne voit ni la vraie base ni Toxiproxy.

```bash
sudo podman network create echo6-repetition
sudo podman run -d --name echo6-copie --network echo6-repetition \
  -e POSTGRES_DB=simulateur -e POSTGRES_USER=simulateur -e POSTGRES_PASSWORD=copie-jetable \
  docker.io/library/postgres:16-alpine
sleep 5   # le temps que PostgreSQL accepte les connexions (pg_isready pour s'en assurer)
sudo podman cp "$DUMP" echo6-copie:/tmp/dump.sql.gz
sudo podman exec echo6-copie sh -c "gunzip -c /tmp/dump.sql.gz | psql -q -v ON_ERROR_STOP=1 -U simulateur -d simulateur"
```

### Étape 3 — contrôler la copie AVANT de la migrer

```bash
COPIE="sudo podman exec -i echo6-copie psql -U simulateur -d simulateur -At"
$COPIE -c "select version from flyway_schema_history where success order by installed_rank desc limit 1"   # 12
$COPIE < /tmp/echo6-montee/controle-avant-montee.sql        # DOIT etre vide. Sinon : on s'arrete (voir plus bas)
$COPIE < /tmp/echo6-montee/comptes-par-table.sql > /tmp/echo6-montee/comptes-avant.txt
cat /tmp/echo6-montee/comptes-avant.txt                     # 24 tables
```

Si le contrôle renvoie une ligne : ne pas aller plus loin. Corriger la valeur **sur la vraie base, encore à V12**
(par exemple `UPDATE centre_sante SET ville = 'Yamoussoukro' WHERE code = '...'`), refaire une sauvegarde, et
reprendre à l'étape 1. Une ligne marquée `REFUS DE V22` se traite comme en section 9 ter.

### Étape 4 — migrer la copie

Flyway seul, en conteneur, dans la **même version que celle embarquée dans l'application** (11.7.2 aujourd'hui :
`unzip -l target/simulateur.jar | grep flyway-core` sur le poste pour vérifier). L'application n'est pas lancée
sur la copie : elle remplirait le référentiel et fausserait les comptes.

```bash
sudo podman run --rm --network echo6-repetition \
  -v /tmp/echo6-montee/migration:/flyway/sql:ro,Z \
  docker.io/flyway/flyway:11.7.2 \
  -url=jdbc:postgresql://echo6-copie:5432/simulateur -user=simulateur -password=copie-jetable migrate
# attendu : « Successfully applied 10 migrations ... now at version v22 »
```

### Étape 5 — contrôler la copie APRÈS

```bash
$COPIE -c "select version from flyway_schema_history where success order by installed_rank desc limit 1"   # 22
$COPIE -c "select count(*) from flyway_schema_history where not success"                                    # 0
$COPIE < /tmp/echo6-montee/comptes-par-table.sql > /tmp/echo6-montee/comptes-apres.txt
# chaque ligne d'avant doit se retrouver telle quelle apres ; la sortie doit etre VIDE
grep -vxFf /tmp/echo6-montee/comptes-apres.txt /tmp/echo6-montee/comptes-avant.txt
# les villes ne sont pas toutes a Abidjan : plusieurs departements doivent apparaitre
$COPIE -c 'select departement_code, count(*) from "TB_ECHO_CENTRE_SANTE" group by 1 order by 2 desc'
$COPIE -c 'select specialite_medicale_code, count(*) from "TB_ECHO_MEDECIN" group by 1 order by 2 desc'
# aucune contrainte laissee non validee
$COPIE -c "select count(*) from pg_constraint where not convalidated"                                        # 0
```

Puis effacer la copie :

```bash
sudo podman rm -f echo6-copie && sudo podman network rm echo6-repetition
```

Noter le résultat dans `docs/journal.md` (date, nombre de tables, nombre de lignes de `evenement` et
`anomaly_injected`).

### Étape 6 — la vraie base

Seulement si les étapes 3 et 5 sont propres. Juste avant, **une sauvegarde de plus** (la base a pu bouger depuis
l'étape 1) :

```bash
cd /opt/echo6
sudo /opt/echo6/sauvegarde.sh
sudo podman-compose -f compose.prod.yaml exec -T postgres psql -U simulateur -d simulateur -At \
  < /tmp/echo6-montee/comptes-par-table.sql > /tmp/echo6-montee/comptes-prod-avant.txt
sudo vi .env                                          # ECHO_IMAGE=...:X.Y.Z (la version qui porte V22)
sudo podman-compose -f compose.prod.yaml pull
sudo podman-compose -f compose.prod.yaml up -d
sudo podman logs echo6_simulator_1 2>&1 | grep -i "flyway\|migrat"   # « now at version v22 »
curl -sf http://127.0.0.1:8088/simulateur/actuator/health
sudo podman-compose -f compose.prod.yaml exec -T postgres psql -U simulateur -d simulateur -At \
  < /tmp/echo6-montee/comptes-par-table.sql > /tmp/echo6-montee/comptes-prod-apres.txt
grep -vxFf /tmp/echo6-montee/comptes-prod-apres.txt /tmp/echo6-montee/comptes-prod-avant.txt   # vide
```

`assure`, `centre_sante`, etc. peuvent avoir **plus** de lignes après qu'avant si l'application a complété le
référentiel au démarrage : la comparaison ne vérifie que l'absence de **perte**. Une ligne qui sort du `grep`
dont le compte a baissé, c'est un retour arrière : remettre l'ancien `ECHO_IMAGE`, **restaurer le dump** pris
juste avant (section 8, « Restauration »), `up -d`.

### La même répétition sur le poste (PowerShell)

Pour s'entraîner sans toucher au serveur, sur une sauvegarde rapatriée (données synthétiques, comme toutes
celles d'Écho) :

```powershell
# a la racine du depot
scp adm-mnguessan@10.10.4.165:/var/sauvegardes/echo6/echo6_AAAAMMJJ_HHMM.sql.gz .\dump.sql.gz
podman network create echo6-repetition
podman run -d --name echo6-copie --network echo6-repetition `
  -e POSTGRES_DB=simulateur -e POSTGRES_USER=simulateur -e POSTGRES_PASSWORD=copie-jetable `
  docker.io/library/postgres:16-alpine
Start-Sleep -Seconds 5
podman cp .\dump.sql.gz echo6-copie:/tmp/dump.sql.gz
podman exec echo6-copie sh -c "gunzip -c /tmp/dump.sql.gz | psql -q -v ON_ERROR_STOP=1 -U simulateur -d simulateur"

# controles avant (le controle DOIT etre vide)
podman cp scripts/db/. echo6-copie:/tmp/
podman exec echo6-copie psql -U simulateur -d simulateur -At -f /tmp/controle-avant-montee.sql
podman exec echo6-copie psql -U simulateur -d simulateur -At -f /tmp/comptes-par-table.sql > comptes-avant.txt

# migrer la copie (sans :Z si Podman sous Windows le refuse)
podman run --rm --network echo6-repetition `
  -v "${PWD}/src/main/resources/db/migration:/flyway/sql:ro" `
  docker.io/flyway/flyway:11.7.2 `
  -url=jdbc:postgresql://echo6-copie:5432/simulateur -user=simulateur -password=copie-jetable migrate

# controles apres : la comparaison doit etre vide
podman exec echo6-copie psql -U simulateur -d simulateur -At -f /tmp/comptes-par-table.sql > comptes-apres.txt
Compare-Object (Get-Content comptes-avant.txt) (Get-Content comptes-apres.txt) | Where-Object SideIndicator -eq '<='
podman exec echo6-copie psql -U simulateur -d simulateur -c 'select departement_code, count(*) from "TB_ECHO_CENTRE_SANTE" group by 1 order by 2 desc'

# menage
podman rm -f echo6-copie; podman network rm echo6-repetition
Remove-Item .\dump.sql.gz, comptes-avant.txt, comptes-apres.txt
```

## 9 ter. V21 et V22 : index et contraintes sur une base remplie

La montée de 9 bis joue aussi V21 (index) et V22 (contraintes sur les événements, clé étrangère `run_id` des lignes
et de l'historique de facture). Une base à V20 qui monte seule passe par la même procédure, avec
`scripts/db/controle-avant-v22.sql` à la place de `controle-avant-montee.sql` à l'étape 3.

- **V21** ne touche aucune donnée. Elle crée 36 index, application arrêtée (pas de `CONCURRENTLY` : Flyway joue le
  script dans une transaction). Durée de création non mesurée sur la base du serveur : la noter dans le journal.
- **V22 ne rattache rien en silence** : si une ligne existante viole une contrainte, elle **s'arrête** avec un
  message du type `V22 refusee : lignes existantes hors contrainte (evenements : 0 statut, 3 type, 0 domaine ; ...)`
  et **ne modifie rien** : la base reste à V21, l'application ne démarre pas (Flyway échoue au démarrage).
  `MonteeV22IntegrationTest` rejoue ce cas.

Une base remplie par l'application ne contient que des valeurs permises (elle écrit le nom exact de l'enum et le
domaine `prestations`) : le contrôle doit revenir vide. S'il renvoie une ligne, c'est une donnée écrite à la main ;
sur la vraie base, **à son ancienne version**, après une sauvegarde :

| Ligne du contrôle | Quoi faire |
|---|---|
| `statut` inconnu | Un événement non écrit normalement : `UPDATE ... SET statut = 'ECHOUE'` le rend rejouable depuis l'explorateur. |
| `type` ou `domaine` inconnu | Si c'est une variante d'écriture (casse, espace) : la corriger en la valeur exacte. Sinon, la ligne n'a pas été produite par Écho : supprimer l'événement (`DELETE`), ses anomalies partent en cascade. |
| `run_id` sans run (lignes, historique) | Orphelins sans simulation : les supprimer. |

Puis refaire le contrôle (vide), refaire une sauvegarde, et reprendre la procédure. Ne jamais ajouter une valeur au
`CHECK` à la main pour faire passer V22 : un type nouveau s'ajoute dans le code **et** par une migration, et le test
`IndexEtContraintesIntegrationTest` vérifie qu'ils restent alignés.

Après la montée, deux vérifications de plus à l'étape 5 :

```bash
$COPIE < /tmp/echo6-montee/cles-sans-index.sql                                                  # vide
$COPIE -c "select count(*) from pg_constraint where conname like 'ck_evenement_%'"              # 3
```

### Une base de développement déjà à V20

V19 ayant été corrigé, sa somme de contrôle a changé : une base locale qui l'a déjà joué refuse de démarrer
(« Validate failed: Migration checksum mismatch for migration version 19 »). Elle ne contient que des données
jetables : `podman compose down -v` puis `podman compose up -d`. Le serveur, resté à V12, n'est pas concerné.

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
