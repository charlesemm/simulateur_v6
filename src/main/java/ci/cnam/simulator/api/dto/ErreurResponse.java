package ci.cnam.simulator.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Format unique des erreurs de l'API.
 *
 * @param code    code stable, exploitable par un programme (ex. SCENARIO_INVALIDE)
 * @param message message lisible
 * @param details detail par champ fautif, quand il y en a
 */
@Schema(description = "Erreur de l'API")
public record ErreurResponse(
        @Schema(example = "SCENARIO_INVALIDE") String code,
        String message,
        List<String> details) {

    public static ErreurResponse de(String code, String message) {
        return new ErreurResponse(code, message, null);
    }
}
