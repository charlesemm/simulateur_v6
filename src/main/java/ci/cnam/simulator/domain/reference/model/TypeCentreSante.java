package ci.cnam.simulator.domain.reference.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Type d'etablissement sanitaire. Le nom reste lisible en francais, mais chaque valeur porte le
 * code de la pyramide sanitaire tel qu'il est stocke en base et reference dans
 * {@code TB_TV_TYPES_ETABLISSEMENTS_SANITAIRES} : c'est ce code qui circule, jamais le nom Java.
 */
public enum TypeCentreSante {
    HOPITAL("HG"),
    CLINIQUE("CLI"),
    CENTRE_SANTE("CSU"),
    CMR("CHR");

    private final String code;

    TypeCentreSante(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<TypeCentreSante> depuisCode(String code) {
        return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst();
    }
}
