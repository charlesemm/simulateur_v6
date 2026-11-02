package ci.cnam.simulator.domain.prestations.facture;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Ligne de prestation d'une facture. Les montants sont fournis par l'appelant (aucun calcul de tarif ici). */
public record FactureLigne(
        UUID id,
        UUID runId,
        UUID factureId,
        int numeroLigne,
        String code,
        String designation,
        LocalDateTime dateSoin,
        int quantite,
        BigDecimal prixUnitaire,
        BigDecimal montant,
        BigDecimal partCnam,
        BigDecimal partAssuranceComplementaire,
        BigDecimal partAssure) {
}
