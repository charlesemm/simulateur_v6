package ci.cnam.simulator.domain.reference.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Les deux regimes de la couverture maladie universelle, codes comme dans {@code TB_TV_REGIMES}.
 * Le regime general de base est contributif ; le regime d'assistance medicale couvre les personnes
 * economiquement faibles. Le taux par defaut du regime vit en base, pas ici : il est fixe par les
 * directions metier et la tarification ne l'applique pas encore.
 */
public enum Regime {
    GENERAL_DE_BASE("RGB"),
    ASSISTANCE_MEDICALE("RAM");

    private final String code;

    Regime(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<Regime> depuisCode(String code) {
        return Arrays.stream(values()).filter(r -> r.code.equals(code)).findFirst();
    }
}
