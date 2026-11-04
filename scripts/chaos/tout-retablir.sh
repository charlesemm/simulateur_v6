#!/usr/bin/env sh
# Remet l'environnement d'aplomb apres un test de chaos : relance les conteneurs arretes puis affiche l'etat
# du proxy Toxiproxy (a l'interieur du reseau compose : son API n'est jamais exposee a l'exterieur).
#
# Pour retirer les toxics poses par un run, utiliser l'API : DELETE /simulateur/api/runs/{id}/chaos.
# Usage : scripts/chaos/tout-retablir.sh
set -eu
cd "$(dirname "$0")/../.."

podman compose up -d postgres toxiproxy simulator
podman compose exec -T toxiproxy /toxiproxy-cli inspect postgres
echo "[chaos] verifier ci-dessus : le proxy 'postgres' est actif et la liste des toxics est vide"
