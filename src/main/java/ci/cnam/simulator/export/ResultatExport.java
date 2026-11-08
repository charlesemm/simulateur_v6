package ci.cnam.simulator.export;

import java.util.UUID;

/**
 * Ce qu'un telechargement a produit, tel qu'il est inscrit dans {@code export_fichier}.
 *
 * @param empreinte SHA-256 des octets reellement envoyes, en minuscules
 * @param lignes    lignes de donnees ecrites, en-tetes non comptes
 */
public record ResultatExport(UUID id, UUID runId, FormatExport format, long lignes, String empreinte) {
}
