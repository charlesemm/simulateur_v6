package ci.cnam.simulator.export;

/** Une table depasse le nombre de lignes qu'un onglet xlsx peut porter (1 048 576). */
public class ExportTropVolumineuxException extends RuntimeException {

    public ExportTropVolumineuxException(String table) {
        super("La table " + table + " depasse ce qu'un onglet xlsx peut porter : demander le format csv ou json");
    }
}
