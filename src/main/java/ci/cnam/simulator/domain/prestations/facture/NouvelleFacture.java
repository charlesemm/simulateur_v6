package ci.cnam.simulator.domain.prestations.facture;

import java.time.LocalDateTime;
import java.util.UUID;

/** Demande d'ouverture (ou de refus direct) d'une facture. */
public record NouvelleFacture(
        UUID runId,
        UUID assureId,
        UUID beneficiaireId,
        UUID centreSanteId,
        TypeBon typeBon,
        LocalDateTime dateOuverture) {
}
