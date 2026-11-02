package ci.cnam.simulator.core.scenario;

import java.util.List;

/** Scenario YAML refuse a la lecture. Le message liste toutes les erreurs, chacune avec le chemin du champ. */
public class ScenarioInvalideException extends RuntimeException {

    private final List<String> erreurs;

    public ScenarioInvalideException(List<String> erreurs) {
        super("Scenario invalide : " + String.join(" ; ", erreurs));
        this.erreurs = List.copyOf(erreurs);
    }

    public List<String> erreurs() {
        return erreurs;
    }
}
