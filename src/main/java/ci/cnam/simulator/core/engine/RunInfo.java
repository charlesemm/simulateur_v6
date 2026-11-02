package ci.cnam.simulator.core.engine;

import java.time.Instant;
import java.util.UUID;

/** Photo d'un run a un instant donne. Dates en temps REEL. */
public record RunInfo(
        UUID id,
        String scenario,
        long seed,
        EtatRun etat,
        long parcoursGeneres,
        long evenementsEmis,
        Instant debut,
        Instant fin,
        String erreur) {
}
