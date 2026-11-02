package ci.cnam.simulator.domain.reference.catalog;

import java.math.BigDecimal;
import java.util.UUID;

public record Examen(UUID id, String code, String libelle, TypeExamen type, BigDecimal tarif) {

    public enum TypeExamen {
        BIOLOGIE, IMAGERIE
    }
}
