#!/usr/bin/env bash
# ECHO v6 — sauvegarde quotidienne de la base
#
# Meme principe que la sauvegarde de la v5, sur la base de la v6
# (projet « echo6 », base « simulateur », port hote 5440).
#
# Les donnees sont synthetiques, mais une simulation de plusieurs heures, ses
# anomalies injectees (la verite terrain) et les rapports de l'outil qualite
# ne se regenerent pas a l'identique : l'image se retire de GHCR, la
# configuration se reecrit, un run perdu l'est definitivement.
#
# Installation (docs/05-deploiement.md section 8) :
#   sudo chmod +x /opt/echo6/sauvegarde.sh
#   sudo crontab -e   ->   15 3 * * * /opt/echo6/sauvegarde.sh >> /var/log/echo6-sauvegarde.log 2>&1
#
# 3 h 15 et non 2 h 15 : la sauvegarde de la v5 tourne a 2 h 15, et deux
# pg_dump simultanes sur la meme machine partagee n'ont rien a s'apporter.
#
# La crontab de ROOT, et non celle de l'utilisateur : sur srv-gouv les
# conteneurs tournent en mode privilegie, et un podman-compose lance sans
# sudo ne les verrait pas.

# -e : arret a la premiere erreur. -u : une variable non definie est une
# erreur. -o pipefail : un echec au milieu d'un tuyau n'est pas avale par le
# succes de la commande suivante — sans lui, un pg_dump en echec suivi d'un
# gzip reussi produirait une archive vide reputee valide.
set -euo pipefail

DOSSIER_PROJET="/opt/echo6"
DOSSIER_SAUVEGARDES="/var/sauvegardes/echo6"
JOURS_CONSERVES=30

cd "$DOSSIER_PROJET"

# Le nom de la base et celui du compte viennent du .env : ils ne sont ecrits
# qu'a un seul endroit, et une faute de frappe ici ne produit pas une
# sauvegarde silencieusement vide.
set -a
. ./.env
set +a
BASE="${DB_NAME:-simulateur}"
COMPTE="${DB_USER:-simulateur}"

horodatage=$(date +%Y%m%d_%H%M)
destination="${DOSSIER_SAUVEGARDES}/echo6_${horodatage}.sql.gz"

mkdir -p "$DOSSIER_SAUVEGARDES"

# On ecrit d'abord dans un fichier temporaire, renomme seulement en cas de
# succes : une sauvegarde interrompue ne peut pas se faire passer pour une
# sauvegarde valide dans le dossier.
temporaire="${destination}.en_cours"

podman-compose -f compose.prod.yaml exec -T postgres \
    pg_dump -U "$COMPTE" -d "$BASE" --clean --if-exists \
    | gzip -9 > "$temporaire"

mv "$temporaire" "$destination"
chmod 600 "$destination"

echo "$(date '+%F %T') — sauvegarde ecrite : ${destination} ($(du -h "$destination" | cut -f1))"

# Purge des anciennes. Le motif ne couvre que les archives terminees : une
# sauvegarde en cours d'ecriture (« .en_cours ») n'est jamais effacee ici.
find "$DOSSIER_SAUVEGARDES" -name 'echo6_*.sql.gz' -mtime "+${JOURS_CONSERVES}" -delete

echo "$(date '+%F %T') — archives de plus de ${JOURS_CONSERVES} jours purgees"
