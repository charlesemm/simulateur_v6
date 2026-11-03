package ci.cnam.simulator.anomaly;

import java.util.UUID;

/**
 * Une ligne de la verite terrain (table anomaly_injected).
 *
 * @param eventId         evenement porteur de l'anomalie (pour un doublon : le doublon, avec son propre identifiant)
 * @param factureId       facture concernee, si l'evenement en porte une
 * @param champ           champ corrompu ; null pour un doublon
 * @param valeurOrigine   valeur propre avant corruption ; null pour un doublon (voir doublonDe)
 * @param valeurCorrompue valeur ecrite ; null si le champ a ete mis a null
 * @param doublonDe       evenement original, pour DOUBLON_EXACT seulement
 */
public record AnomalyRecord(
        UUID runId,
        UUID eventId,
        UUID factureId,
        AnomalyType type,
        String champ,
        String valeurOrigine,
        String valeurCorrompue,
        UUID doublonDe) {
}
