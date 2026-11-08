package ci.cnam.simulator.anomaly.mutators;

/**
 * Resultat d'une anomalie de fichier : ce qui part dans le fichier, et ce qui s'inscrit au corrige.
 *
 * @param cellule         valeur reellement ecrite dans le fichier (cellule entiere)
 * @param valeurOrigine   valeur SAINE, celle qui reste en base
 * @param valeurCorrompue valeur piegee, telle qu'elle se lit dans le fichier
 */
public record MutationFichier(String cellule, String valeurOrigine, String valeurCorrompue) {

    public MutationFichier {
        if (cellule == null || valeurOrigine == null || valeurCorrompue == null) {
            throw new IllegalArgumentException("une anomalie de fichier garde toujours ses deux valeurs");
        }
    }
}
