package ci.cnam.simulator.export;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Ce qu'une simulation a produit, dans l'ordre ou l'export le presente : assures concernes, factures,
 * lignes de facture, evenements. Le corrige n'en fait pas partie et part par un autre chemin.
 */
@Service
public class JeuSimulation {

    private final JeuStore store;

    public JeuSimulation(JeuStore store) {
        this.store = store;
    }

    /** Les quatre tables du jeu a analyser, toujours dans cet ordre. */
    public List<TableExport> tables(UUID runId) {
        return List.of(
                store.assures(runId),
                store.factures(runId),
                store.factureLignes(runId),
                store.evenements(runId));
    }

    /** La verite terrain, servie seule. */
    public TableExport corrige(UUID runId) {
        return store.corrige(runId);
    }
}
