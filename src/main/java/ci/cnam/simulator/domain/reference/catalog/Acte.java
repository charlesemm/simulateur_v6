package ci.cnam.simulator.domain.reference.catalog;

import java.math.BigDecimal;
import java.util.UUID;

public record Acte(UUID id, String code, String libelle, CategorieActe categorie, BigDecimal tarif) {

    public enum CategorieActe {
        CONSULTATION, HOSPITALISATION, DENTAIRE
    }
}
