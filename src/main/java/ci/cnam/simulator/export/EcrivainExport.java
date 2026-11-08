package ci.cnam.simulator.export;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Ecrit les tables d'un export dans un format, EN FLUX : rien n'est accumule en memoire, ni le
 * fichier, ni une table. Les tables recues portent deja leur marquage de donnee fictive.
 *
 * <p>L'ecrivain ne ferme JAMAIS le flux qu'on lui confie : c'est la reponse HTTP, et
 * {@link ExportService} doit encore pouvoir calculer l'empreinte des octets envoyes.
 */
public interface EcrivainExport {

    FormatExport format();

    void ecrire(List<TableExport> tables, OutputStream sortie) throws IOException;
}
