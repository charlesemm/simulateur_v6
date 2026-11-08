package ci.cnam.simulator.quality;

import java.time.Instant;
import java.util.UUID;

/**
 * Une analyse qu'Echo attend qu'on fasse maintenant. C'est le point qui reconcilie « le script est
 * autonome » et « la planification est dans Echo » : le script lit cette liste, prend ce qui l'y
 * interesse, et reste maitre de son execution.
 *
 * @param runId      la simulation a analyser (terminee)
 * @param creeLe     quand l'echeance a ete ouverte
 * @param echeance   avant quand Echo attend le rapport ; au-dela c'est un SILENCE
 * @param statut     etat de l'echeance, tel que la planification le tient
 * @param export     prefixe des trois formats : {@code <export>.csv}, {@code .json}, {@code .xlsx}
 */
public record AnalyseAttendue(UUID id, UUID runId, Instant creeLe, Instant echeance, String statut, String export) {

    /** Les chemins de retrait, pour qu'un script n'ait aucune URL a construire lui-meme. */
    public static String cheminExport(UUID runId) {
        return "/api/runs/" + runId + "/export";
    }
}
