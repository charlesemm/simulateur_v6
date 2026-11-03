package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.Optional;

/**
 * Corrompt UNE valeur d'un champ, d'une seule facon. Une classe par type d'anomalie, testable isolement.
 *
 * <p>Regles : la valeur rendue est toujours differente de la valeur d'entree ; tout tirage passe par
 * {@link SeededRandom#entre(int, int)} (meme graine, meme resultat) ; si la valeur ne s'y prete pas
 * (mauvais type, trop courte...), on rend {@link Optional#empty()} et l'injecteur laisse l'evenement propre.
 */
public interface Mutator {

    AnomalyType type();

    Optional<Mutation> muter(Object valeur, SeededRandom random);
}
