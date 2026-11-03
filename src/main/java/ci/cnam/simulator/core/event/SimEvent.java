package ci.cnam.simulator.core.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evenement emis par un generateur (un domaine). Record minimal et generique : le moteur ne connait pas le metier.
 *
 * @param domaine          domaine emetteur (ex. "prestations")
 * @param type             nom du type d'evenement propre au domaine (ex. "FACTURE_OUVERTE")
 * @param timestampSimule  date fictive du soin (temps simule, pas le temps reel d'ecriture)
 * @param payload          donnees de l'evenement (cles stables, valeurs simples)
 * @param anomalies        anomalies injectees dans cet evenement (verite terrain) ; vide pour un evenement propre
 */
public record SimEvent(
        UUID eventId,
        UUID runId,
        String domaine,
        String type,
        LocalDateTime timestampSimule,
        Map<String, Object> payload,
        List<AnomalieTracee> anomalies) {

    public SimEvent {
        anomalies = anomalies == null ? List.of() : List.copyOf(anomalies);
    }
}
