package ci.cnam.simulator.chaos;

/** Toxiproxy est injoignable ou refuse la commande : l'action de chaos n'a pas pu etre appliquee ou retiree. */
public class ChaosIndisponibleException extends RuntimeException {

    public ChaosIndisponibleException(String message) {
        super(message);
    }

    public ChaosIndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
