package ci.cnam.simulator.web;

import ci.cnam.simulator.chaos.ChaosType;

/**
 * Champs du panneau chaos. Seuls ceux qui concernent le {@code type} choisi sont lus ; les autres sont ignores.
 *
 * @param mode            COUPURE_BASE : DESACTIVER ou RESET_PEER
 * @param latenceMs       LATENCE
 * @param jitterMs        LATENCE
 * @param multiplicateur  SURCHARGE (5, 20 ou 100)
 * @param proportion      MESSAGE_TRONQUE, dans ]0 ; 1]
 */
public record DemandeChaos(
        ChaosType type,
        Integer dureeSecondes,
        String mode,
        Integer latenceMs,
        Integer jitterMs,
        Integer multiplicateur,
        Double proportion) {
}
