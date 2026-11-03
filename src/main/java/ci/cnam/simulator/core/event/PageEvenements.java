package ci.cnam.simulator.core.event;

import java.util.List;

/**
 * Tranche de resultats.
 *
 * @param total nombre total d'evenements correspondant aux filtres, toutes pages confondues
 */
public record PageEvenements(List<EvenementLu> contenu, int page, int taille, long total) {

    public PageEvenements {
        contenu = List.copyOf(contenu);
    }

    public int totalPages() {
        return taille == 0 ? 0 : (int) ((total + taille - 1) / taille);
    }
}
