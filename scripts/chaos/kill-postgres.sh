#!/usr/bin/env sh
# Arret BRUTAL de PostgreSQL (SIGKILL) puis retour apres N secondes (defaut 20).
# Verifie : le simulateur patiente (file bornee, contre-pression), puis reprend l'ecriture sans perte ni doublon.
#
# A lancer DEPUIS L'EXTERIEUR du simulateur. Le volume pgdata est conserve : seules les transactions en cours sont perdues.
# Usage : scripts/chaos/kill-postgres.sh [secondes]
set -eu
cd "$(dirname "$0")/../.."
PAUSE="${1:-20}"

echo "[chaos] kill -9 de postgres pour ${PAUSE} s..."
podman compose kill --signal SIGKILL postgres
date -u +"[chaos] base tuee a %Y-%m-%dT%H:%M:%SZ"
# On empeche un eventuel redemarrage automatique de masquer la panne pendant la duree voulue.
sleep "$PAUSE"
podman compose up -d postgres
date -u +"[chaos] base relancee a %Y-%m-%dT%H:%M:%SZ"
i=0
until podman compose exec -T postgres pg_isready -q 2>/dev/null; do
    i=$((i + 1))
    [ "$i" -gt 60 ] && { echo "[chaos] ECHEC : PostgreSQL ne revient pas apres 60 s" >&2; exit 1; }
    sleep 1
done
echo "[chaos] PostgreSQL pret apres ~${i} s de recuperation (rejeu du journal WAL compris)"
