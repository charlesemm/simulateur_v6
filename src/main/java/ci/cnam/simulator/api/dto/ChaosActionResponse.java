package ci.cnam.simulator.api.dto;

import ci.cnam.simulator.chaos.ChaosAction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Action de chaos, active (fin nulle) ou terminee. Dates en temps REEL (UTC). */
@Schema(description = "Action de chaos")
public record ChaosActionResponse(
        UUID id,
        UUID runId,
        String type,
        Map<String, Object> parametres,
        Instant debut,
        Instant finPrevue,
        Instant fin,
        @Schema(description = "EXPIREE, RETABLIE, RUN_TERMINE ou INTERROMPUE ; nul tant que l'action est active")
        String motifFin,
        String auteur,
        long evenementsTouches) {

    public static ChaosActionResponse de(ChaosAction a) {
        return new ChaosActionResponse(a.id(), a.runId(), a.type().name(), a.parametres(), a.debut(), a.finPrevue(),
                a.fin(), a.motifFin(), a.auteur(), a.evenementsTouches());
    }
}
