package ci.cnam.simulator.quality;

import ci.cnam.simulator.anomaly.AnomalyType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Acces aux donnees du rapport qualite. Implemente dans persistence/. */
public interface QualityStore {

    /** Parmi ces identifiants, ceux qui sont des evenements de CE run. */
    Set<UUID> evenementsDuRun(UUID runId, Collection<UUID> eventIds);

    /** Cree le rapport et ses detections ; une detection deja connue du rapport est ignoree. Rend le nombre inserees. */
    int enregistrer(UUID rapportId, UUID runId, String versionOutil, List<Detection> detections);

    /** Anomalies injectees par type. */
    Map<AnomalyType, Long> injectees(UUID runId);

    /** Anomalies injectees retrouvees par une detection de meme evenement, type et champ. */
    Map<AnomalyType, Long> detectees(UUID runId, String versionOutil);

    /** Detections distinctes sans anomalie injectee correspondante, par type detecte. */
    Map<AnomalyType, Long> fauxPositifs(UUID runId, String versionOutil);
}
