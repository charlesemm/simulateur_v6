package ci.cnam.simulator.domain.prestations.tarification;

import ci.cnam.simulator.domain.reference.model.Regime;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Taux en vigueur. Deux axes, tous deux lus en base et jamais ecrits dans le code :
 * <ul>
 *   <li>un taux de prise en charge CNAM par type de prestation, dans {@code TB_ECHO_TAUX} ;</li>
 *   <li>un taux de reference par regime, dans {@code TB_TV_REGIMES}.</li>
 * </ul>
 *
 * <p>Leur articulation : le taux du regime est un <b>plancher</b>. Un assure du regime general de
 * base est pris en charge a 70 % au moins, un assure du regime d'assistance medicale a 100 %, ce
 * qui le dispense du ticket moderateur. Un type de prestation que les directions metier ont fixe
 * au-dessus du plancher garde son taux : l'hospitalisation reste a 90 % pour un assure du regime
 * general. Le plancher ne peut donc que relever la prise en charge, jamais l'abaisser.
 */
public final class RegleTaux {

    public static final String CODE_COMPLEMENTAIRE = "ASSURANCE_COMPLEMENTAIRE";

    private final Map<TypePrestation, BigDecimal> cnam = new EnumMap<>(TypePrestation.class);
    private final Map<Regime, TauxRegime> regimes = new EnumMap<>(Regime.class);
    private final BigDecimal complementaire;

    public RegleTaux(Map<TypePrestation, BigDecimal> tauxCnam, BigDecimal tauxComplementaire,
                     Map<Regime, TauxRegime> tauxRegimes) {
        for (TypePrestation type : TypePrestation.values()) {
            BigDecimal t = tauxCnam.get(type);
            if (t == null) {
                throw new IllegalArgumentException("Taux CNAM manquant pour " + type);
            }
            cnam.put(type, verifier(type.codeTaux(), t));
        }
        for (Regime regime : Regime.values()) {
            TauxRegime t = tauxRegimes.get(regime);
            if (t == null) {
                throw new IllegalArgumentException("Taux de regime manquant pour " + regime.code());
            }
            regimes.put(regime, new TauxRegime(verifier(regime.code(), t.taux()), t.contributif()));
        }
        this.complementaire = verifier(CODE_COMPLEMENTAIRE, tauxComplementaire);
    }

    /** Construit la regle depuis les taux par type de prestation et les taux de regime. */
    public static RegleTaux depuis(Map<String, BigDecimal> lignes, Map<Regime, TauxRegime> tauxRegimes) {
        Map<TypePrestation, BigDecimal> cnam = new EnumMap<>(TypePrestation.class);
        for (TypePrestation type : TypePrestation.values()) {
            BigDecimal t = lignes.get(type.codeTaux());
            if (t != null) {
                cnam.put(type, t);
            }
        }
        return new RegleTaux(cnam, lignes.get(CODE_COMPLEMENTAIRE), tauxRegimes);
    }

    /** Taux du type de prestation seul, sans tenir compte du regime. */
    public BigDecimal tauxCnam(TypePrestation type) {
        return cnam.get(type);
    }

    /** Taux reellement applique : celui du type de prestation, releve au plancher du regime. */
    public BigDecimal tauxCnam(TypePrestation type, Regime regime) {
        BigDecimal parType = cnam.get(type);
        BigDecimal plancher = regimes.get(regime).taux();
        return plancher.compareTo(parType) > 0 ? plancher : parType;
    }

    public TauxRegime regime(Regime regime) {
        return regimes.get(regime);
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
