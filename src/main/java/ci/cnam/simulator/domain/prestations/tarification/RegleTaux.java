package ci.cnam.simulator.domain.prestations.tarification;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Taux en vigueur : un taux de prise en charge CNAM par type de prestation et un taux d'assurance complementaire
 * (applique au ticket moderateur). Valeurs lues dans la table taux, jamais dans le code.
 */
public final class RegleTaux {

    public static final String CODE_COMPLEMENTAIRE = "ASSURANCE_COMPLEMENTAIRE";

    private final Map<TypePrestation, BigDecimal> cnam = new EnumMap<>(TypePrestation.class);
    private final BigDecimal complementaire;

    public RegleTaux(Map<TypePrestation, BigDecimal> tauxCnam, BigDecimal tauxComplementaire) {
        for (TypePrestation type : TypePrestation.values()) {
            BigDecimal t = tauxCnam.get(type);
            if (t == null) {
                throw new IllegalArgumentException("Taux CNAM manquant pour " + type);
            }
            cnam.put(type, verifier(type.codeTaux(), t));
        }
        this.complementaire = verifier(CODE_COMPLEMENTAIRE, tauxComplementaire);
    }

    /** Construit la regle depuis les lignes de la table taux (code vers valeur). */
    public static RegleTaux depuis(Map<String, BigDecimal> lignes) {
        Map<TypePrestation, BigDecimal> cnam = new EnumMap<>(TypePrestation.class);
        for (TypePrestation type : TypePrestation.values()) {
            BigDecimal t = lignes.get(type.codeTaux());
            if (t != null) {
                cnam.put(type, t);
            }
        }
        return new RegleTaux(cnam, lignes.get(CODE_COMPLEMENTAIRE));
    }

    public BigDecimal tauxCnam(TypePrestation type) {
        return cnam.get(type);
    }

    public BigDecimal tauxComplementaire() {
        return complementaire;
    }

    private static BigDecimal verifier(String code, BigDecimal taux) {
        if (taux == null || taux.signum() < 0 || taux.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Taux absent ou hors de [0 ; 1] pour " + code + " : " + taux);
        }
        return taux;
    }
}
