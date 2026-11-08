package ci.cnam.simulator.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * La verite terrain, en un seul csv (pas d'archive : il n'y a qu'une table). Meme BOM et meme
 * echappement que le reste, et le meme marquage de donnee fictive.
 *
 * <p>Ce fichier NE DOIT JAMAIS etre servi au script d'analyse : il contient les reponses.
 */
@Component
public class EcrivainCorrigeCsv implements EcrivainExport {

    @Override
    public FormatExport format() {
        return FormatExport.CORRIGE;
    }

    @Override
    public void ecrire(List<TableExport> tables, OutputStream sortie) throws IOException {
        Writer ecrivain = new OutputStreamWriter(sortie, StandardCharsets.UTF_8);
        ecrivain.write('﻿');
        for (TableExport table : tables) {
            EcrivainCsvZip.ligne(ecrivain, table.colonnes());
            table.lignes().pour(valeurs -> {
                try {
                    EcrivainCsvZip.ligne(ecrivain, valeurs);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        ecrivain.flush();   // jamais close() : le flux appartient a la reponse
    }
}
