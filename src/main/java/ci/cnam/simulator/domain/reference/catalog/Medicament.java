package ci.cnam.simulator.domain.reference.catalog;

import java.math.BigDecimal;
import java.util.UUID;

public record Medicament(UUID id, String code, String libelle, BigDecimal tarif) {
}
