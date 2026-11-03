package ci.cnam.simulator.core.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Evenement tel qu'il a ete ecrit en base (temps simule ET temps reel).
 *
 * @param factureId nul pour les evenements emis avant l'ouverture de la facture
 */
public record EvenementLu(
        UUID eventId,
        UUID runId,
        UUID factureId,
        String domaine,
        String type,
        LocalDateTime timestampSimule,
        Instant timestampReel,
        String statut,
        Map<String, Object> payload) {
}
