package ci.cnam.simulator.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

/**
 * Demande de lancement : SOIT le nom d'un scenario deja connu, SOIT un scenario YAML complet.
 *
 * @param scenario nom d'un scenario enregistre ou d'un fichier de {@code scenarios/}
 * @param yaml     contenu YAML d'un scenario, valide a la lecture puis enregistre
 */
@Schema(description = "Lancement d'une simulation : renseigner 'scenario' OU 'yaml', pas les deux")
public record LancementRunRequest(
        @Schema(example = "parcours-normal") String scenario,
        @Schema(description = "Contenu YAML complet d'un scenario") String yaml) {

    public boolean parNom() {
        return scenario != null && !scenario.isBlank();
    }

    public boolean parYaml() {
        return yaml != null && !yaml.isBlank();
    }

    @AssertTrue(message = "renseigner exactement un champ : 'scenario' (nom) ou 'yaml' (contenu)")
    @Schema(hidden = true)
    public boolean isChoixUnique() {
        return parNom() ^ parYaml();
    }
}
