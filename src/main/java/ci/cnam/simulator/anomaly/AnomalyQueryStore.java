package ci.cnam.simulator.anomaly;

import java.util.List;
import java.util.UUID;

/** Lecture de la verite terrain (table anomaly_injected). Implementee dans persistence/. */
public interface AnomalyQueryStore {

    /** Tranche triee par ordre d'ecriture ; les filtres nuls ne filtrent pas. */
    List<AnomalyRecord> rechercher(UUID runId, AnomalyType type, String champ, int page, int taille);

    long compter(UUID runId, AnomalyType type, String champ);
}
