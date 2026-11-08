package ci.cnam.simulator.export;

import java.util.List;
import java.util.UUID;

/**
 * Lecture EN FLUX des tables metier d'une simulation. Implemente par la couche persistence : aucune
 * table de lignes exportees, on interroge {@code assure}, {@code facture}, {@code facture_ligne} et
 * {@code evenement}. Chaque table est lue par paquets, triee sur sa cle primaire : l'ordre est donc
 * totalement deterministe, et deux telechargements donnent la meme empreinte.
 */
public interface JeuStore {

    /** Les assures CONCERNES par la simulation (jointure depuis facture.assure_id), jamais tout le referentiel. */
    TableExport assures(UUID runId);

    TableExport factures(UUID runId);

    TableExport factureLignes(UUID runId);

    TableExport evenements(UUID runId);

    /** La verite terrain ({@code anomaly_injected}). Ne rejoint JAMAIS les quatre tables du jeu. */
    TableExport corrige(UUID runId);

    /** Trace un telechargement servi : une ligne par telechargement. */
    void tracer(UUID id, UUID runId, FormatExport format, long nombreLignes, String empreinte);

    /** Les telechargements deja servis pour ce run, du plus recent au plus ancien. */
    List<Telechargement> telechargements(UUID runId);
}
