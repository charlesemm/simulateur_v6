package ci.cnam.simulator.quality;

import java.util.List;

/** Envoi refuse en bloc : rien n'est enregistre. */
public class DetectionInvalideException extends RuntimeException {

    private final List<String> erreurs;

    public DetectionInvalideException(String message, List<String> erreurs) {
        super(message);
        this.erreurs = List.copyOf(erreurs);
    }

    public List<String> erreurs() {
        return erreurs;
    }
}
