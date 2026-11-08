package ci.cnam.simulator.quality;

import java.time.Instant;
import java.util.UUID;

/**
 * Un echange inscrit dans {@code echange}, horodate et conserve (cahier 4.5).
 *
 * @param versionOutil nul pour un retrait d'export : le script ne s'annonce qu'en poussant
 * @param detail       ce qui s'est passe, en clair, pour l'ecran et pour un litige
 */
public record Echange(UUID id, UUID runId, SensEchange sens, Instant date, String versionOutil,
                      StatutEchange statut, String detail) {
}
