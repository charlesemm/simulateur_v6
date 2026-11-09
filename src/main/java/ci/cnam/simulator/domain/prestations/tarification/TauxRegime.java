package ci.cnam.simulator.domain.prestations.tarification;

import java.math.BigDecimal;

/**
 * Taux de reference d'un regime, tel qu'il est lu dans {@code TB_TV_REGIMES}.
 *
 * <p>{@code contributif} est ce qui decide de l'effet du taux, et c'est une donnee, pas une regle
 * ecrite ici : un regime contributif laisse les taux par type de prestation faire leur office, un
 * regime non contributif couvre l'assure jusqu'a son taux de reference. C'est tout le sens du
 * regime d'assistance medicale, ou le beneficiaire ne paie pas de ticket moderateur.
 */
public record TauxRegime(BigDecimal taux, boolean contributif) {
}
