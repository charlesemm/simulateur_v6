package ci.cnam.simulator.api.dto;

import ci.cnam.simulator.core.engine.RunInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Etat d'un run. Dates en temps REEL (UTC). */
@Schema(description = "Etat d'un run de simulation")
public record RunResponse(
        UUID id,
        String scenario,
        long seed,
        @Schema(example = "EN_COURS") String etat,
        long parcoursGeneres,
        long evenementsEmis,
        Instant debut,
        Instant fin,
        String erreur) {

    public static RunResponse de(RunInfo info) {
        return new RunResponse(info.id(), info.scenario(), info.seed(), info.etat().name(),
                info.parcoursGeneres(), info.evenementsEmis(), info.debut(), info.fin(), info.erreur());
    }
}
