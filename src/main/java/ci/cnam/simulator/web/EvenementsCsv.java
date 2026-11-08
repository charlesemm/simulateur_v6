package ci.cnam.simulator.web;

import ci.cnam.simulator.core.event.EvenementLu;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.function.Function;

/**
 * Ecriture CSV (RFC 4180) des evenements de l'ecran Flux de donnees. L'echappement lui-meme vient de
 * {@link ci.cnam.simulator.export.CsvCellule}, seule regle CSV du projet.
 */
final class EvenementsCsv {

    static final List<String> ENTETES = List.of("event_id", "run_id", "facture_id", "domaine", "type",
            "timestamp_simule", "timestamp_reel", "statut", "payload");

    private EvenementsCsv() {
    }

    static void entete(Writer sortie) throws IOException {
        ligne(sortie, ENTETES);
    }

    static void ecrire(Writer sortie, EvenementLu e, Function<Object, String> enJson) throws IOException {
        ligne(sortie, List.of(
                texte(e.eventId()), texte(e.runId()), texte(e.factureId()), texte(e.domaine()), texte(e.type()),
                texte(e.timestampSimule()), texte(e.timestampReel()), texte(e.statut()),
                e.payload() == null ? "" : enJson.apply(e.payload())));
    }

    /** La regle d'echappement est unique dans le projet : {@link ci.cnam.simulator.export.CsvCellule}. */
    static String cellule(String valeur) {
        return ci.cnam.simulator.export.CsvCellule.de(valeur);
    }

    private static void ligne(Writer sortie, List<String> cellules) throws IOException {
        StringBuilder l = new StringBuilder();
        for (int i = 0; i < cellules.size(); i++) {
            if (i > 0) {
                l.append(',');
            }
            l.append(cellule(cellules.get(i)));
        }
        sortie.write(l.append("\r\n").toString());
    }

    private static String texte(Object o) {
        return o == null ? "" : o.toString();
    }
}
