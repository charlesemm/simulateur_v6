package ci.cnam.simulator.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Enregistrement d'un scenario : son nom est lu dans le YAML (champ {@code nom}). */
@Schema(description = "Scenario a enregistrer")
public record CreationScenarioRequest(
        @NotBlank(message = "yaml : champ obligatoire")
        @Schema(description = "Contenu YAML du scenario", example = "nom: demo\nseed: 42\ndomaines: {prestations: {debit: 5/s}}")
        String yaml) {
}
