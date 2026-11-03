package ci.cnam.simulator.api.dto;

import ci.cnam.simulator.core.event.EvenementLu;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Evenement ecrit en base. {@code timestampSimule} est la date fictive du soin, {@code timestampReel} l'ecriture. */
@Schema(description = "Evenement de simulation")
public record EvenementResponse(
        UUID eventId,
        UUID runId,
        UUID factureId,
        String domaine,
        @Schema(example = "FACTURE_OUVERTE") String type,
        LocalDateTime timestampSimule,
        Instant timestampReel,
        String statut,
        Map<String, Object> payload) {

    public static EvenementResponse de(EvenementLu e) {
        return new EvenementResponse(e.eventId(), e.runId(), e.factureId(), e.domaine(), e.type(),
                e.timestampSimule(), e.timestampReel(), e.statut(), e.payload());
    }
}
