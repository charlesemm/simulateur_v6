package ci.cnam.simulator.core.scenario;

/** Aucun scenario de ce nom, ni en base ni dans le dossier des scenarios. */
public class ScenarioInconnuException extends RuntimeException {

    public ScenarioInconnuException(String nom) {
        super("Scenario inconnu : " + nom);
    }
}
