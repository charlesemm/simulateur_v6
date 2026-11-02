package ci.cnam.simulator.domain.prestations.facture;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Une transition de statut. {@code ancienStatut} est nul a la creation de la facture. */
public record FactureHistorique(
        UUID id,
        UUID runId,
        UUID factureId,
        FactureStatut ancienStatut,
        FactureStatut nouveauStatut,
        LocalDateTime horodatageSimule,
        OffsetDateTime horodatageReel) {
}
