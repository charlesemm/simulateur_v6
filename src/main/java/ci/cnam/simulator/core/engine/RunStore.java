package ci.cnam.simulator.core.engine;

import ci.cnam.simulator.core.scenario.Scenario;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Memoire durable des runs (tables scenario et simulation_run). Implementee dans persistence/. */
public interface RunStore {

    /** Enregistre le scenario (cree ou met a jour par son nom) et le run au statut CREE. */
    void creer(UUID runId, Scenario scenario);

    /** Met a jour statut et dates reelles ; {@code debut} et {@code fin} peuvent etre nuls. */
    void mettreAJour(UUID runId, EtatRun etat, Instant debut, Instant fin, String erreur);

    /**
     * Tous les runs connus en base, du plus recent au plus ancien. Pour un run qui n'est plus en memoire,
     * les compteurs sont ceux conserves en base : evenements reellement ecrits, et parcours inconnus (0).
     * Une implementation en memoire (tests) peut ne rien conserver.
     */
    default List<RunInfo> lister() {
        return List.of();
    }

    /** Le run, s'il est en base. Memes reserves sur les compteurs que {@link #lister()}. */
    default Optional<RunInfo> trouver(UUID runId) {
        return Optional.empty();
    }
}
