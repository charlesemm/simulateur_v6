package ci.cnam.simulator.core.scenario;

import java.time.Instant;

/**
 * Scenario tel qu'il est conserve (en base ou sur disque), sans interpretation.
 *
 * @param nom           nom unique, lu dans le YAML
 * @param contenuYaml   texte d'origine
 * @param source        BASE ou FICHIER
 * @param dateCreation  nulle pour un scenario venant d'un fichier
 * @param dateMaj       nulle pour un scenario venant d'un fichier
 */
public record ScenarioEnregistre(
        String nom,
        String contenuYaml,
        Source source,
        Instant dateCreation,
        Instant dateMaj) {

    public enum Source {
        BASE, FICHIER
    }
}
