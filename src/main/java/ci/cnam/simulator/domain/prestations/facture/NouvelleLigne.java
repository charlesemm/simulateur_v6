package ci.cnam.simulator.domain.prestations.facture;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Ligne a ajouter : la somme des trois parts doit etre egale au montant. */
public record NouvelleLigne(
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
