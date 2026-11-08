package ci.cnam.simulator.quality;

import java.util.UUID;

/**
 * Une anomalie semee que l'outil n'a jamais signalee, telle qu'elle est inscrite dans
 * {@code anomaly_injected}. C'est la ligne exacte a aller voir : l'ecran Qualite pose dessus un lien
 * vers le flux de donnees filtre sur {@code eventId}.
 *
 * <p>Aucune table n'est creee pour cela : la liste se lit dans les tables metier existantes.
 *
 * @param champ          nul pour un doublon : l'anomalie porte sur l'evenement entier
 * @param valeurOrigine  la valeur propre, celle qu'Echo seul connait
 * @param valeurCorrompue nulle quand le champ a ete vide
 */
public record AnomalieRatee(UUID eventId, UUID factureId, String type, String champ,
                            String valeurOrigine, String valeurCorrompue) {
}
