package ci.cnam.simulator.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Classeur xlsx ecrit par Apache POI en mode SXSSF : seules {@value #FENETRE} lignes sont tenues en
 * memoire, les precedentes sont deja parties sur le disque temporaire. Un onglet par table.
 *
 * <p>Tout est ecrit en TEXTE, deliberement : un tableur qui devine les types transformerait un
 * numero d'assure en nombre et une date en serie. Le fichier dit donc exactement la meme chose que
 * le csv et le json.
 *
 * <p>Les proprietes du document sont figees : sans cela, deux telechargements du meme run
 * differeraient par leur seule date de creation et n'auraient pas la meme empreinte.
 */
@Component
public class EcrivainXlsx implements EcrivainExport {

    /** Lignes gardees en memoire par onglet. */
    public static final int FENETRE = 100;

    /** Date figee des proprietes du document (1980-01-01 UTC). */
    private static final String DATE_FIGEE = "1980-01-01T00:00:00Z";

    @Override
    public FormatExport format() {
        return FormatExport.XLSX;
    }

    @Override
    public void ecrire(List<TableExport> tables, OutputStream sortie) throws IOException {
        SXSSFWorkbook classeur = new SXSSFWorkbook(FENETRE);
        try {
            // Les lignes sorties de la fenetre attendent sur le disque temporaire : on les y compresse,
            // sinon un export de 100 000 evenements y laisse plusieurs dizaines de Mio de XML brut.
            classeur.setCompressTempFiles(true);
            figerProprietes(classeur);
            for (TableExport table : tables) {
                onglet(classeur, table);
            }
            classeur.write(sortie);   // ne ferme pas le flux : il appartient a la reponse
        } finally {
            classeur.dispose();       // efface les fichiers temporaires de la fenetre
            classeur.close();
        }
    }

    private static void onglet(SXSSFWorkbook classeur, TableExport table) {
        SXSSFSheet feuille = classeur.createSheet(table.nom());
        int[] numero = {0};
        ecrireLigne(feuille, numero[0]++, table.colonnes());
        table.lignes().pour(valeurs -> {
            if (numero[0] > SpreadsheetVersion.EXCEL2007.getLastRowIndex()) {
                throw new ExportTropVolumineuxException(table.nom());
            }
            ecrireLigne(feuille, numero[0]++, valeurs);
        });
    }

    private static void ecrireLigne(SXSSFSheet feuille, int numero, List<String> valeurs) {
        Row ligne = feuille.createRow(numero);
        for (int i = 0; i < valeurs.size(); i++) {
            String valeur = valeurs.get(i);
            if (valeur != null) {
                Cell cellule = ligne.createCell(i);
                cellule.setCellValue(valeur);
            }
        }
    }

    /** Sans date figee, le classeur porterait l'heure du telechargement et l'empreinte changerait. */
    private static void figerProprietes(SXSSFWorkbook classeur) {
        try {
            var proprietes = classeur.getXSSFWorkbook().getProperties().getCoreProperties();
            proprietes.setCreated(DATE_FIGEE);
            proprietes.setModified(DATE_FIGEE);
            proprietes.setCreator("Echo - donnees fictives");
        } catch (RuntimeException | org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new UncheckedIOException(new IOException("Proprietes du classeur non modifiables", e));
        }
    }
}
