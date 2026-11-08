package ci.cnam.simulator.export;

/**
 * La seule regle d'echappement CSV du projet (RFC 4180). Une cellule qui commence par {@code = + - @}
 * (ou une tabulation) est precedee d'une apostrophe : ouverte dans un tableur, elle ne sera jamais
 * interpretee comme une formule.
 */
public final class CsvCellule {

    private CsvCellule() {
    }

    public static String de(String valeur) {
        String v = valeur == null ? "" : valeur;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
