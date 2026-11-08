package ci.cnam.simulator.quality;

import java.util.List;

/**
 * Une tranche de lignes a montrer (anomalies ratees, faux positifs). Plafonnee et paginee comme
 * ailleurs : un run peut semer des centaines de milliers d'anomalies, l'ecran n'en montre jamais tout.
 *
 * @param total nombre total de lignes, toutes pages confondues
 */
public record PageQualite<T>(List<T> contenu, int page, int taille, long total) {

    public PageQualite {
        contenu = List.copyOf(contenu);
    }

    public static <T> PageQualite<T> vide(int taille) {
        return new PageQualite<>(List.of(), 0, taille, 0);
    }

    public int totalPages() {
        return taille == 0 ? 0 : (int) ((total + taille - 1) / taille);
    }
}
