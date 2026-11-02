package ci.cnam.simulator.domain.prestations.tarification;

import java.math.BigDecimal;

/** Repartition d'un montant. Invariant : partCnam + partAssuranceComplementaire + partAssure = montant. */
public record Repartition(BigDecimal montant, BigDecimal partCnam, BigDecimal partAssuranceComplementaire,
                          BigDecimal partAssure) {
}
