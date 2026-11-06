package ci.cnam.simulator.web;

import ci.cnam.simulator.core.event.EvenementLu;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.function.Function;

/**
 * Ecriture CSV (RFC 4180) des evenements. Une cellule qui commence par {@code = + - @} (ou une tabulation) est
 * precedee d'une apostrophe : ouverte dans un tableur, elle ne sera jamais interpretee comme une formule.
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

    static String cellule(String valeur) {
        String v = valeur == null ? "" : valeur;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
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
