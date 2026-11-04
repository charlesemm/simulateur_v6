#!/usr/bin/env sh
# Arret BRUTAL du simulateur (SIGKILL, aucun arret propre) : simule un crash du processus.
# Verifie ensuite : redemarrage automatique (restart: unless-stopped), reprise au dernier checkpoint, aucun doublon.
#
# A lancer DEPUIS L'EXTERIEUR du simulateur : celui-ci ne monte jamais le socket Podman/Docker.
# Usage : scripts/chaos/kill-simulator.sh
set -eu
cd "$(dirname "$0")/../.."

echo "[chaos] kill -9 du conteneur simulator..."
podman compose kill --signal SIGKILL simulator
date -u +"[chaos] simulateur tue a %Y-%m-%dT%H:%M:%SZ"

# Sans politique de redemarrage (ou en rootless sans Quadlet), on le relance a la main :
sleep 3
if [ "$(podman compose ps --status running --format '{{.Service}}' 2>/dev/null | grep -c '^simulator$' || true)" = "0" ]; then
    echo "[chaos] pas de redemarrage automatique, relance manuelle..."
    podman compose up -d simulator
fi
echo "[chaos] attente de /actuator/health..."
i=0
until curl -fs "http://localhost:8080/simulateur/actuator/health" | grep -q '"UP"'; do
    i=$((i + 1))
    [ "$i" -gt 60 ] && { echo "[chaos] ECHEC : le simulateur ne revient pas apres 60 s" >&2; exit 1; }
    sleep 1
done
echo "[chaos] simulateur de retour apres ~${i} s"
