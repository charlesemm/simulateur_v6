package ci.cnam.simulator.core.event;

import java.util.UUID;

/**
 * Criteres de lecture des evenements d'un run. Les filtres nuls ne filtrent pas.
 *
 * @param runId   run concerne (obligatoire)
 * @param eventId un evenement precis ; c'est ce qui permet d'ouvrir depuis l'ecran Qualite la ligne
 *                exacte qu'un script a manquee, sans table intermediaire
 * @param domaine filtre sur le domaine emetteur
 * @param type    filtre sur le type d'evenement
 * @param statut  filtre sur le statut d'ecriture
 * @param page    numero de page, a partir de 0
 * @param taille  taille de page, deja bornee par {@link EventQueryService}
 */
public record RechercheEvenements(
        UUID runId,
        UUID eventId,
        String domaine,
        String type,
        String statut,
        int page,
        int taille) {
}
