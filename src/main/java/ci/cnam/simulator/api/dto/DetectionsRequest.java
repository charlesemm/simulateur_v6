package ci.cnam.simulator.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Corps de POST /api/runs/{id}/quality/report. */
@Schema(description = "Detections de l'outil qualite pour un run")
public record DetectionsRequest(
        @Schema(description = "Version de l'outil qualite, texte libre", example = "qualite-2.3.1")
        @NotBlank(message = "ne doit pas etre vide") @Size(max = 80, message = "80 caracteres au maximum")
        String versionOutil,
        @NotEmpty(message = "au moins une detection")
        @Size(max = MAX_DETECTIONS, message = "10000 detections au maximum par envoi")
        List<@Valid @NotNull(message = "detection absente") Detection> detections) {

    public static final int MAX_DETECTIONS = 10_000;

    @Schema(description = "Une detection : evenement, type d'anomalie, champ (omis pour DOUBLON_EXACT)")
    public record Detection(
            @NotNull(message = "obligatoire") UUID eventId,
            @Schema(example = "nom") @Size(max = 80, message = "80 caracteres au maximum") String champ,
            @Schema(example = "FAUTE_ORTHOGRAPHE")
            @NotBlank(message = "obligatoire") String type,
            @Schema(description = "Regle declenchee cote outil, facultatif")
            @Size(max = 200, message = "200 caracteres au maximum") String regle,
            @Schema(description = "Score de confiance, facultatif") Double score) {
    }
}
