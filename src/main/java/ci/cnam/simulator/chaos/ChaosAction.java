package ci.cnam.simulator.chaos;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Une action de chaos (ligne de chaos_action). Dates en temps REEL.
 *
 * @param finPrevue debut + duree demandee
 * @param fin       fin effective ; nulle tant que l'action est active
 * @param motifFin  EXPIREE, RETABLIE, RUN_TERMINE ou INTERROMPUE ; nul tant que l'action est active
 */
public record ChaosAction(
        UUID id,
        UUID runId,
        ChaosType type,
        Map<String, Object> parametres,
        Instant debut,
        Instant finPrevue,
        Instant fin,
        String motifFin,
        String auteur,
        long evenementsTouches) {

    public boolean active() {
        return fin == null;
    }

    public ChaosAction terminee(Instant quand, String motif, long touches) {
        return new ChaosAction(id, runId, type, parametres, debut, finPrevue, quand, motif, auteur, touches);
    }
}
