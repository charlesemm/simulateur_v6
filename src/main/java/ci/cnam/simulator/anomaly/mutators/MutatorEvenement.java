package ci.cnam.simulator.anomaly.mutators;

import ci.cnam.simulator.anomaly.AnomalyType;
import ci.cnam.simulator.core.event.SimEvent;
import ci.cnam.simulator.core.random.SeededRandom;
import java.util.Optional;

/** Anomalie qui porte sur l'evenement entier et non sur un champ : les doublons, exact et approximatif. */
public interface MutatorEvenement {

    AnomalyType type();

    /**
     * Rend l'evenement supplementaire a emettre apres l'original, ou vide si cet evenement ne s'y prete pas
     * (un doublon approximatif a besoin d'un nom a faire varier).
     */
    Optional<SimEvent> muter(SimEvent original, SeededRandom random);
}
