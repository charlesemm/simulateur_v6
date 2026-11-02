package ci.cnam.simulator.core.scenario;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Scenario de simulation, tel que lu et valide par {@link ScenarioLoader}.
 *
 * @param nom               nom unique du scenario
 * @param seed              graine : meme scenario + meme graine = memes evenements
 * @param accelerationTemps secondes simulees par seconde reelle (1, 10, 100...)
 * @param debutSimule       origine du temps simule
 * @param domaines          reglages par domaine (cle = nom du domaine, ex. "prestations")
 * @param phases            phases de charge triees, sans chevauchement (peut etre vide)
 * @param contenuYaml       texte d'origine, conserve en base
 */
public record Scenario(
        String nom,
        long seed,
        double accelerationTemps,
        LocalDateTime debutSimule,
        Map<String, ConfigDomaine> domaines,
        List<Phase> phases,
        String contenuYaml) {

    public Scenario {
        domaines = Map.copyOf(domaines);
        phases = List.copyOf(phases);
    }
}
