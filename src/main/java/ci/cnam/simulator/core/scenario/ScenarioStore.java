package ci.cnam.simulator.core.scenario;

import java.util.List;
import java.util.Optional;

/** Memoire durable des scenarios (table scenario). Implementee dans persistence/. */
public interface ScenarioStore {

    /** Enregistre le scenario, ou remplace son contenu s'il existe deja sous ce nom. */
    void enregistrer(Scenario scenario);

    List<ScenarioEnregistre> lister();

    Optional<ScenarioEnregistre> trouverParNom(String nom);
}
