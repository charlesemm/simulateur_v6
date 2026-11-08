package ci.cnam.simulator.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;

/**
 * Archive zip : un {@code .csv} par table, dans l'ordre des tables. BOM UTF-8 et echappement de
 * {@link CsvCellule}, la seule regle CSV du projet.
 *
 * <p>Les entrees portent un horodatage FIXE : l'archive ne depend que de son contenu, donc deux
 * telechargements du meme run donnent la meme empreinte.
 */
@Component
public class EcrivainCsvZip implements EcrivainExport {

    /** 1980-01-01 00:00:00 UTC : la plus ancienne date qu'un en-tete zip sache representer. */
    private static final long HORODATAGE_FIXE = 315_532_800_000L;

    @Override
    public FormatExport format() {
        return FormatExport.CSV;
    }

    @Override
    public void ecrire(List<TableExport> tables, OutputStream sortie) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(sortie, StandardCharsets.UTF_8);
        for (TableExport table : tables) {
            ZipEntry entree = new ZipEntry(table.nom() + ".csv");
            entree.setTime(HORODATAGE_FIXE);
            zip.putNextEntry(entree);
            Writer ecrivain = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
            ecrivain.write('﻿');   // BOM : les tableurs lisent correctement les accents
            ligne(ecrivain, table.colonnes());
            table.lignes().pour(valeurs -> {
                try {
                    ligne(ecrivain, valeurs);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            ecrivain.flush();
            zip.closeEntry();
        }
        zip.finish();   // jamais close() : le flux appartient a la reponse
    }

    /** Une ligne CSV, en terminaison CRLF (RFC 4180), ecrite directement dans l'entree. */
    static void ligne(Writer sortie, List<String> cellules) throws IOException {
        StringBuilder l = new StringBuilder();
        for (int i = 0; i < cellules.size(); i++) {
            if (i > 0) {
                l.append(',');
            }
            l.append(CsvCellule.de(cellules.get(i)));
        }
        sortie.write(l.append("\r\n").toString());
    }
}
