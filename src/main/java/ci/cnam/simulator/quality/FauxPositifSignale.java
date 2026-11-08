package ci.cnam.simulator.quality;

import java.util.UUID;

/**
 * Une detection de l'outil a laquelle aucune anomalie semee ne correspond. Comme une anomalie ratee,
 * elle porte a l'ecran un lien vers la ligne exacte du flux de donnees.
 *
 * @param champ nul quand l'outil a signale l'evenement entier (doublon)
 */
public record FauxPositifSignale(UUID eventId, String type, String champ) {
}
