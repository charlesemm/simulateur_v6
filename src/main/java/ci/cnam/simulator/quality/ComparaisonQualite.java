package ci.cnam.simulator.quality;

import java.util.List;
import java.util.UUID;

/**
 * Injectees contre detectees pour un run.
 *
 * @param reference    vrai si rien n'a ete injecte (run a 0 %) : toute detection y est un faux positif
 * @param versionOutil filtre applique, nul si tous les rapports du run sont pris ensemble
 */
public record ComparaisonQualite(UUID runId, String versionOutil, boolean reference, LigneQualite total,
                                 List<LigneQualite> categories, List<LigneQualite> types) {
}
