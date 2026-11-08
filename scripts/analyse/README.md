# Outil temoin d'analyse (scripts/analyse/)

Ce script est une **reference d'integration** et un **outil temoin** : il montre comment un
outil exterieur fait seul le cycle complet (trouver la simulation, retirer le jeu, pousser son
rapport) sans qu'Echo l'appelle ni connaisse sa machine.

**Ce n'est pas le produit.** Le vrai outil de qualite de la CNAM le remplace sans qu'une ligne
d'Echo change : seuls les quatre appels d'API font contrat.

Il ne depend que de la bibliotheque standard de Python 3, n'est pas construit par Maven, n'entre
pas dans l'image du conteneur et n'est appele par aucun test Java.

## Lancer (PowerShell)

```powershell
$env:ECHO_URL = "http://localhost:8080/simulateur"
$env:ECHO_API_KEY = "<la cle d'API>"
py -3 scripts\analyse\analyse_echo.py
```

Options : `--run <uuid>` pour viser une simulation precise, `--dry-run` pour analyser sans
pousser le rapport.

## Ce qu'il fait, dans l'ordre

| Etape | Appel | Cote Echo |
|---|---|---|
| 1 | `GET /api/analyses/attendues` | ce qu'Echo attend qu'on analyse (vide tant que la planification n'existe pas) |
| 2 | `GET /api/runs?statut=ARRETE&limit=1` | repli : la derniere simulation terminee |
| 3 | `GET /api/runs/{id}/export.csv` | **ENVOI** inscrit dans `echange` |
| 4 | `POST /api/runs/{id}/quality/report` | **RECEPTION** inscrite dans `echange` |

Les trois regles appliquees (champ vide, montant negatif, format d'identifiant) sont
volontairement naives : ce qui est demontre ici, c'est le canal, pas la detection.

## Ce qu'il ne lit jamais

Le **corrige** (`anomaly_injected`, la liste des anomalies semees) n'est pas servi sous cle
d'API : un script qui le lirait rendrait son propre score sans valeur. Il se telecharge depuis
l'interface, sous un compte Administrateur ou Operateur.

## Si le rapport n'arrive pas

Echo n'y voit pas un taux de detection de 0 %, mais un **echec d'echange**, avec son libelle :

- `SILENCE` : le jeu a ete retire, aucun rapport n'est arrive dans le delai regle
  (`simulateur.qualite.delai-rapport-minutes`, 60 minutes par defaut) ;
- `RAPPORT_MALFORME` : l'envoi a ete refuse en bloc (evenement inconnu du run, champ manquant,
  type inconnu) ;
- `RAPPORT_SANS_DETAIL` : rapport accepte, mais aucune detection ne porte a la fois un type et
  un champ exploitables — aucune mesure fiable n'est possible (cahier 2.3 et 4.3).

Les trois sont visibles sur l'ecran *Telecharger une simulation*, et aucun ne produit de chiffre.
