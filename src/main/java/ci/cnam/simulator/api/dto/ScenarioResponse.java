package ci.cnam.simulator.api.dto;

import ci.cnam.simulator.core.scenario.ScenarioEnregistre;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Scenario du catalogue. {@code source} vaut BASE (enregistre) ou FICHIER (livre dans scenarios/). */
@Schema(description = "Scenario du catalogue")
public record ScenarioResponse(
        String nom,
        String source,
        String yaml,
        Instant dateCreation,
        Instant dateMaj) {

    public static ScenarioResponse de(ScenarioEnregistre s) {
        return new ScenarioResponse(s.nom(), s.source().name(), s.contenuYaml(), s.dateCreation(), s.dateMaj());
    }
}
