#!/usr/bin/env python3
"""Outil temoin : fait seul le cycle complet d'une analyse de qualite contre Echo.

Ce script est une REFERENCE D'INTEGRATION, pas le produit. Il montre comment un outil
exterieur se debrouille sans qu'Echo l'appelle :

    1. il demande ce qu'Echo attend qu'on analyse (GET /api/analyses/attendues) ;
       la liste est vide tant que la planification n'est pas branchee, il se rabat alors
       sur la derniere simulation terminee (GET /api/runs?statut=ARRETE&limit=1) ;
    2. il RETIRE le jeu (GET /api/runs/{id}/export.csv) : Echo ne pousse rien ;
    3. il applique trois regles simples (champ vide, montant negatif, format d'identifiant) ;
    4. il POUSSE son rapport (POST /api/runs/{id}/quality/report).

Il n'utilise que la bibliotheque standard. Le vrai outil de la CNAM le remplace sans
toucher a Echo : seuls les quatre appels ci-dessus font contrat.

Usage (PowerShell) :
    $env:ECHO_URL = "http://localhost:8080/simulateur"
    $env:ECHO_API_KEY = "<la cle d'API>"
    py -3 scripts\\analyse\\analyse_echo.py

Options :
    --run <uuid>    analyser cette simulation plutot que celle qu'Echo propose
    --dry-run       analyser sans pousser le rapport
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import os
import re
import sys
import urllib.error
import urllib.request
import zipfile

# --- Les trois regles. Volontairement naives : c'est un temoin, pas un moteur de regles. ---

# Champs numeriques du payload d'un evenement qui ne peuvent pas etre negatifs.
CHAMPS_MONTANT = (
    "montant", "montant_total", "part_cnam", "part_assure",
    "part_assurance_complementaire", "prix_unitaire", "quantite", "taux",
)

# Identifiants et leur forme attendue.
FORMATS = {
    "numero_assure": re.compile(r"^[0-9]{10}$"),
    "numero_facture": re.compile(r"^[A-Z0-9][A-Z0-9-]{3,}$"),
}

# Colonnes de marquage posees par Echo : elles ne sont pas des donnees a analyser.
MARQUAGE = ("donnee_fictive", "simulation")

VERSION_OUTIL = "temoin-python-1.0"


def analyser(evenement):
    """Rend les detections trouvees dans un evenement du jeu."""
    detections = []
    event_id = evenement.get("event_id")
    try:
        payload = json.loads(evenement.get("payload") or "{}")
    except json.JSONDecodeError:
        return detections
    if not isinstance(payload, dict):
        return detections

    for champ, valeur in payload.items():
        if champ in MARQUAGE or valeur is None:
            continue
        texte = str(valeur)

        if texte.strip() == "":
            detections.append(detection(event_id, "CHAMP_VIDE", champ, "valeur vide ou blanche"))
            continue

        if champ in CHAMPS_MONTANT:
            try:
                if float(texte.replace(" ", "").replace(",", ".")) < 0:
                    detections.append(
                        detection(event_id, "VALEUR_NEGATIVE", champ, "montant strictement negatif"))
            except ValueError:
                detections.append(
                    detection(event_id, "FORMAT_NUMERIQUE_INVALIDE", champ, "valeur non numerique"))
            continue

        forme = FORMATS.get(champ)
        if forme is not None and not forme.match(texte.strip()):
            detections.append(
                detection(event_id, "FORMAT_IDENTIFIANT_INVALIDE", champ, "forme attendue " + forme.pattern))

    return detections


def detection(event_id, type_anomalie, champ, regle):
    # eventId + type + champ : les trois que le rapprochement d'Echo exige (cahier 4.3).
    # Sans le champ, aucune mesure n'est possible et Echo le dit (RAPPORT_SANS_DETAIL).
    return {"eventId": event_id, "type": type_anomalie, "champ": champ, "regle": regle, "score": 1.0}


# --- Le canal. Echo expose, le script prend. ---


class Echo:
    """Les quatre seuls appels qui font contrat."""

    def __init__(self, base, cle):
        self.base = base.rstrip("/")
        self.cle = cle

    def _appel(self, chemin, corps=None, brut=False):
        requete = urllib.request.Request(self.base + chemin, data=corps, method="POST" if corps else "GET")
        requete.add_header("X-API-Key", self.cle)
        requete.add_header("Accept", "application/octet-stream" if brut else "application/json")
        if corps is not None:
            requete.add_header("Content-Type", "application/json")
        with urllib.request.urlopen(requete, timeout=600) as reponse:
            octets = reponse.read()
        return octets if brut else json.loads(octets.decode("utf-8"))

    def attendues(self):
        reponse = self._appel("/api/analyses/attendues")
        return reponse.get("analyses", [])

    def derniere_terminee(self):
        runs = self._appel("/api/runs?statut=ARRETE&limit=1")
        return runs[0]["id"] if runs else None

    def retirer(self, run_id):
        """Retire le jeu en csv (archive zip). C'est l'ENVOI, cote Echo."""
        return self._appel("/api/runs/" + run_id + "/export.csv", brut=True)

    def pousser(self, run_id, detections):
        corps = json.dumps({"versionOutil": VERSION_OUTIL, "detections": detections}).encode("utf-8")
        return self._appel("/api/runs/" + run_id + "/quality/report", corps=corps)


def evenements(archive):
    """Lit evenements.csv dans l'archive retiree, ligne par ligne."""
    with zipfile.ZipFile(io.BytesIO(archive)) as zip_jeu:
        nom = next((n for n in zip_jeu.namelist() if n.endswith("evenements.csv")), None)
        if nom is None:
            raise SystemExit("evenements.csv absent de l'archive : " + ", ".join(zip_jeu.namelist()))
        with zip_jeu.open(nom) as flux:
            texte = io.TextIOWrapper(flux, encoding="utf-8-sig", newline="")
            for ligne in csv.DictReader(texte):
                yield ligne


def main():
    arguments = argparse.ArgumentParser(description="Outil temoin d'analyse de qualite pour Echo")
    arguments.add_argument("--run", help="simulation a analyser (defaut : celle qu'Echo propose)")
    arguments.add_argument("--dry-run", action="store_true", help="ne pas pousser le rapport")
    options = arguments.parse_args()

    base = os.environ.get("ECHO_URL")
    cle = os.environ.get("ECHO_API_KEY")
    if not base or not cle:
        print("ECHO_URL et ECHO_API_KEY doivent etre dans l'environnement.", file=sys.stderr)
        return 2

    echo = Echo(base, cle)

    run_id = options.run
    if run_id is None:
        attendues = echo.attendues()
        if attendues:
            run_id = attendues[0]["runId"]
            print("Echo attend l'analyse de " + run_id)
        else:
            run_id = echo.derniere_terminee()
            print("Rien de planifie : derniere simulation terminee = " + str(run_id))
    if run_id is None:
        print("Aucune simulation terminee a analyser.", file=sys.stderr)
        return 1

    archive = echo.retirer(run_id)
    print("Jeu retire : %d octets" % len(archive))

    detections, lues = [], 0
    for evenement in evenements(archive):
        lues += 1
        detections.extend(analyser(evenement))
    print("%d evenement(s) lu(s), %d detection(s)" % (lues, len(detections)))

    if options.dry_run:
        print("--dry-run : rapport non pousse")
        return 0
    if not detections:
        # Pousser une liste vide est refuse par Echo (au moins une detection) : c'est voulu,
        # un rapport sans aucune ligne n'est pas mesurable.
        print("Aucune detection : rien a pousser.")
        return 0

    try:
        recu = echo.pousser(run_id, detections)
    except urllib.error.HTTPError as erreur:
        print("Rapport refuse (%d) : %s" % (erreur.code, erreur.read().decode("utf-8", "replace")),
              file=sys.stderr)
        return 1
    print("Rapport pousse : " + json.dumps(recu))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
