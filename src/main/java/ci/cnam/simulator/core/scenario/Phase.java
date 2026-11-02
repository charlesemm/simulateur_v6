package ci.cnam.simulator.core.scenario;

import java.time.Duration;

/**
 * Phase de charge, mesuree en temps REEL ACTIF du run (la pause ne compte pas).
 *
 * @param de              debut de la phase depuis le demarrage du run
 * @param a               fin de la phase
 * @param multiplicateur  facteur applique au debit (1 = charge normale, 20 = "x20")
 * @param charge          libelle d'origine ("normale", "x20")
 */
public record Phase(Duration de, Duration a, double multiplicateur, String charge) {
}
