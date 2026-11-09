package ci.cnam.simulator.domain.prestations.facture;

import java.util.Arrays;
import java.util.Optional;

/**
 * Les quatre types de bons de prise en charge. Chaque valeur porte le code officiel du bon, celui
 * que reference {@code TB_TV_TYPES_FACTURES} et qui est stocke en base : le nom Java reste lisible,
 * mais c'est le code qui circule et qui est ecrit.
 */
public enum TypeBon {
    /** Soins ambulatoires, consultations et medicaments. */
    AMBULATOIRE("ER-01-GPR"),
    /** Hospitalisation et soins. */
    HOSPITALISATION("ER-02-GPR"),
    /** Biologie, imagerie et autres. */
    BIOLOGIE_IMAGERIE("ER-03-GPR"),
    /** Soins et protheses dentaires. */
    DENTAIRE("DENTAIRE");

    private final String code;

    TypeBon(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<TypeBon> depuisCode(String code) {
        return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst();
    }
}
