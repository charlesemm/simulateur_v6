package ci.cnam.simulator.core.engine;

import ci.cnam.simulator.core.scenario.Scenario;
import java.time.Instant;
import java.util.UUID;

/** Memoire durable des runs (tables scenario et simulation_run). Implementee dans persistence/. */
public interface RunStore {

    /** Enregistre le scenario (cree ou met a jour par son nom) et le run au statut CREE. */
    void creer(UUID runId, Scenario scenario);

    /** Met a jour statut et dates reelles ; {@code debut} et {@code fin} peuvent etre nuls. */
    void mettreAJour(UUID runId, EtatRun etat, Instant debut, Instant fin, String erreur);
}
