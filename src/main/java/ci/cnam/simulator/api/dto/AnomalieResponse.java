package ci.cnam.simulator.api.dto;

import ci.cnam.simulator.anomaly.AnomalyRecord;
import ci.cnam.simulator.anomaly.AnomalyType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Une anomalie injectee : la verite terrain, avec la valeur propre d'origine. */
@Schema(description = "Anomalie injectee (verite terrain)")
public record AnomalieResponse(
        UUID runId,
        UUID eventId,
        UUID factureId,
        @Schema(example = "FAUTE_ORTHOGRAPHE") AnomalyType type,
        @Schema(example = "nom", description = "Champ corrompu ; nul pour un doublon") String champ,
        @Schema(example = "Kouassi") String valeurOrigine,
        @Schema(example = "Kouasi", description = "Nul si le champ a ete mis a null") String valeurCorrompue,
        @Schema(description = "Evenement original, pour un DOUBLON_EXACT seulement") UUID doublonDe) {

    public static AnomalieResponse de(AnomalyRecord a) {
        return new AnomalieResponse(a.runId(), a.eventId(), a.factureId(), a.type(), a.champ(), a.valeurOrigine(),
                a.valeurCorrompue(), a.doublonDe());
    }
}
