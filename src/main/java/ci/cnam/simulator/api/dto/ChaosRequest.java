package ci.cnam.simulator.api.dto;

import ci.cnam.simulator.chaos.ChaosType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;

/**
 * Demande d'action de chaos. La duree est obligatoire et plafonnee cote serveur.
 *
 * @param type         COUPURE_BASE, LATENCE, SURCHARGE ou MESSAGE_TRONQUE
 * @param dureeSecondes duree de l'action ; elle prend fin toute seule a l'echeance
 * @param parametres   propres au type : COUPURE_BASE {mode: DESACTIVER|RESET_PEER}, LATENCE {latenceMs, jitterMs},
 *                     SURCHARGE {multiplicateur: 5|20|100}, MESSAGE_TRONQUE {proportion: ]0;1]}
 */
@Schema(description = "Action de chaos a declencher sur un run actif")
public record ChaosRequest(
        @NotNull @Schema(example = "COUPURE_BASE") ChaosType type,
        @NotNull @Positive @Schema(example = "30") Integer dureeSecondes,
        @Schema(example = "{\"mode\": \"DESACTIVER\"}") Map<String, Object> parametres) {
}
