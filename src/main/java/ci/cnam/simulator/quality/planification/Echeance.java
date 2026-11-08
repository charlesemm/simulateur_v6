package ci.cnam.simulator.quality.planification;

import java.time.Instant;
import java.util.UUID;

/**
 * Une echeance reellement ouverte, et son suivi : le script est-il venu, son rapport est-il arrive.
 *
 * @param planificationId nul pour une analyse demandee a la main, ou dont la planification a ete supprimee
 * @param runId           la simulation visee ; nul quand il n'y en avait aucune (statut SANS_CIBLE)
 * @param dateEcheance    l'heure dite, celle du calendrier
 * @param dateRetrait     quand le script est venu chercher le jeu, nul s'il n'est pas venu
 * @param dateRapport     quand son rapport est arrive, nul s'il n'est pas arrive
 */
public record Echeance(UUID id, UUID planificationId, UUID runId, Instant dateEcheance,
                       Instant dateRetrait, Instant dateRapport, StatutAnalyse statut, String detail) {

    /** Longueur de la colonne. */
    public static final int DETAIL_MAX = 500;
}
