package ci.cnam.simulator.export;

/**
 * La simulation n'est pas terminee : son export est refuse. Un run en cours continue d'ecrire, donc
 * deux telechargements donneraient deux fichiers differents et l'empreinte ne prouverait plus rien.
 */
public class ExportIndisponibleException extends RuntimeException {

    public ExportIndisponibleException(String message) {
        super(message);
    }
}
