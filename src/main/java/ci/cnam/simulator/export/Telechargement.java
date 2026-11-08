package ci.cnam.simulator.export;

import java.time.Instant;
import java.util.UUID;

/**
 * Un telechargement deja servi, lu dans {@code export_fichier}. Deux lignes du meme run et du meme
 * format doivent porter la MEME empreinte : c'est la preuve de rejouabilite, affichee a l'ecran.
 */
public record Telechargement(UUID id, FormatExport format, Instant date, long lignes, String empreinte) {
}
